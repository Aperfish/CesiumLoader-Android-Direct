package dev.cesium.androidmin;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Shared installation transaction. The privilege backend supplies the same interface. */
public final class InstallerEngine {
    public interface Environment {
        File storage();
        List<String> packages() throws Exception;
        boolean running(String packageName) throws Exception;
        boolean nativeLoader(String packageName) throws Exception;
    }
    private static final long LIMIT=64L*1024*1024;
    private final Environment env;
    private final ZipFile zip;
    private final JSONObject manifest;
    public InstallerEngine(Environment environment,File payload)throws Exception {
        env=environment;zip=new ZipFile(payload);
        manifest=new JSONObject(new String(entry("manifest.json",1024*1024),"UTF-8"));
        if(manifest.getInt("format")!=2||!manifest.getString("bundleKey").matches("[a-f0-9]{32}"))throw new IOException("不支持的安装负载");
    }
    public void close()throws IOException{zip.close();}
    public static boolean allowedPackage(String p){return p!=null&&p.matches("com\\.feimo\\.(?:astralparty|jxpd)(?:\\.[A-Za-z0-9_]+)*")&&!p.toLowerCase(Locale.ROOT).contains("jpn");}
    private File files(String pkg)throws IOException {
        if(!allowedPackage(pkg))throw new IOException("不支持的游戏包名");
        return new File(env.storage(),"Android/data/"+pkg+"/files");
    }
    private File root(String pkg)throws IOException{return new File(files(pkg),"CesiumLoader");}
    private static JSONObject ok(String text)throws Exception{return new JSONObject().put("ok",true).put("message",text);}

    public JSONObject execute(JSONObject request)throws Exception {
        String action=request.getString("action");
        if(action.equals("discover"))return discover();
        String pkg=request.getString("package");
        if(!env.packages().contains(pkg)||!allowedPackage(pkg))throw new IOException("游戏未安装在当前用户空间");
        if(action.equals("status"))return status(pkg);
        if(env.running(pkg))throw new IOException("请先完全退出游戏，再安装、恢复或修改启用项。");
        recoverPending(pkg);
        Match match=select(pkg);
        if(action.equals("install"))return install(pkg,match,request.optJSONArray("enabled"));
        if(action.equals("restore"))return restore(pkg,match);
        if(action.equals("configure")) {
            if(!hash(match.data).equals(match.profile.getString("patchedSha256")))throw new IOException("请先安装此版本加载器。");
            configure(pkg,request.optJSONArray("enabled"));return ok("启用项已保存，下次启动游戏生效。");
        }
        throw new IOException("未知操作");
    }
    private JSONObject discover()throws Exception {
        JSONArray games=new JSONArray();
        for(String pkg:env.packages()) {
            if(!allowedPackage(pkg))continue;
            JSONObject g=new JSONObject().put("package",pkg).put("running",env.running(pkg));
            try {
                if(env.nativeLoader(pkg))throw new IOException("检测到原生加载器，请先恢复官方客户端，避免重复加载。");
                Match m=select(pkg);
                g.put("supported",true).put("profile",m.profile.getString("id")).put("gameVersion",m.profile.getString("gameVersion"))
                 .put("state",state(m)).put("cache",relative(files(pkg),m.data.getParentFile()));
            } catch(Exception e){g.put("supported",false).put("reason",e.getMessage());}
            games.put(g);
        }
        return ok(games.length()==0?"未找到当前用户空间的国服或渠道服。":"已检测游戏资源。 ").put("games",games).put("mods",manifest.getJSONArray("mods"));
    }
    private static final class Match {
        File data;JSONObject profile;long used;
        Match(File f,JSONObject p,long u){data=f;profile=p;used=u;}
    }
    private Match select(String pkg)throws Exception {
        File dir=new File(files(pkg),"com.unity.addressables/AssetBundles/"+manifest.getString("bundleKey"));
        File[] children=dir.listFiles();
        if(children==null)throw new IOException("尚未找到热更新缓存，请先启动游戏完成资源下载。");
        List<Match> matches=new ArrayList<>();int candidates=0;long newestSeen=-1;
        JSONArray profiles=manifest.getJSONArray("profiles");
        for(File child:children) {
            if(!child.isDirectory()||!child.getName().matches("[a-f0-9]{32}"))continue;
            File data=new File(child,"__data"),info=new File(child,"__info");
            if(!data.isFile()||!info.isFile())continue;candidates++;
            long used=validInfo(info);newestSeen=Math.max(newestSeen,used);
            if(data.length()>LIMIT)continue;
            String digest=hash(data);
            for(int i=0;i<profiles.length();i++) {
                JSONObject p=profiles.getJSONObject(i);
                if(digest.equals(p.getString("originalSha256"))||digest.equals(p.getString("patchedSha256"))||contains(p.optJSONArray("legacyMinSha256"),digest)) {
                    matches.add(new Match(data,p,used));break;
                }
            }
        }
        if(matches.isEmpty()) {
            Match interrupted=pendingMatch(pkg);
            if(interrupted!=null&&interrupted.used>=newestSeen)return interrupted;
            throw new IOException(candidates==0?"资源尚未下载完整。":"此游戏资源版本未收录，未修改缓存。请更新适配包。");
        }
        matches.sort((a,b)->Long.compare(b.used,a.used));
        if(matches.get(0).used<newestSeen)throw new IOException("当前使用的较新资源版本未收录，拒绝修改旧缓存冒充安装成功。");
        if(matches.size()>1&&matches.get(0).used==matches.get(1).used)throw new IOException("发现多个同时间的资源缓存，无法安全确定当前版本。请先正常启动一次游戏。");
        return matches.get(0);
    }
    private String state(Match m)throws Exception {
        String h=hash(m.data);
        return h.equals(m.profile.getString("patchedSha256"))?"installed":h.equals(m.profile.getString("originalSha256"))?"original":contains(m.profile.optJSONArray("legacyMinSha256"),h)?"legacy-min":"interrupted";
    }
    private Match pendingMatch(String pkg)throws Exception {
        File base=root(pkg),pending=new File(base,"pending.json");if(!pending.isFile())return null;
        JSONObject record=new JSONObject(new String(read(pending,16384),"UTF-8"));
        if(!pkg.equals(record.getString("package")))throw new IOException("未完成操作的包名不符");
        String relative=record.getString("cache");
        if(!relative.matches("com\\.unity\\.addressables/AssetBundles/"+manifest.getString("bundleKey")+"/[a-f0-9]{32}"))throw new IOException("未完成操作路径无效");
        JSONObject profile=null;JSONArray profiles=manifest.getJSONArray("profiles");
        for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).getString("id").equals(record.getString("profile")))profile=profiles.getJSONObject(i);
        if(profile==null||!profile.getString("originalSha256").equals(record.getString("originalSha256"))||!profile.getString("patchedSha256").equals(record.getString("patchedSha256")))throw new IOException("未完成操作的版本记录不匹配");
        File target=inside(files(pkg),relative+"/__data"),backup=new File(base,"backups/"+profile.getString("originalSha256"));
        verifyBackup(backup,profile.getString("originalSha256"));
        String digest=hash(target);File staged=new File(base,"staged-data");
        boolean known=digest.equals(profile.getString("originalSha256"))||digest.equals(profile.getString("patchedSha256"))||contains(profile.optJSONArray("legacyMinSha256"),digest);
        boolean partial=prefixOf(target,new File(backup,"__data"))||(staged.isFile()&&hash(staged).equals(profile.getString("patchedSha256"))&&prefixOf(target,staged));
        if(!known&&!partial)throw new IOException("缓存已由其他程序更新，拒绝从旧操作记录覆盖。");
        return new Match(target,profile,validInfo(new File(target.getParentFile(),"__info")));
    }
    private void recoverPending(String pkg)throws Exception {
        Match interrupted=pendingMatch(pkg);if(interrupted==null)return;
        if(env.running(pkg))throw new IOException("游戏正在运行，不能恢复未完成操作");
        File backup=new File(root(pkg),"backups/"+interrupted.profile.getString("originalSha256"));
        publish(new File(backup,"__data"),interrupted.data,interrupted.profile.getString("originalSha256"));
        if(!new File(root(pkg),"pending.json").delete())throw new IOException("无法清理恢复记录");
    }
    private static boolean prefixOf(File partial,File full)throws Exception {
        if(!partial.isFile()||!full.isFile()||partial.length()>full.length())return false;
        try(InputStream a=new BufferedInputStream(new FileInputStream(partial));InputStream b=new BufferedInputStream(new FileInputStream(full))) {
            int n;while((n=a.read())!=-1)if(n!=b.read())return false;return true;
        }
    }
    private JSONObject install(String pkg,Match match,JSONArray enabled)throws Exception {
        if(env.nativeLoader(pkg))throw new IOException("已检测到另一个原生加载器，拒绝叠加安装。");
        File base=root(pkg),backup=new File(base,"backups/"+match.profile.getString("originalSha256"));
        String current=hash(match.data),original=match.profile.getString("originalSha256"),patched=match.profile.getString("patchedSha256");
        File originalSource=match.data;
        if(!current.equals(original)) {
            if(new File(backup,"__data").isFile())originalSource=new File(backup,"__data");
            else if(contains(match.profile.optJSONArray("legacyMinSha256"),current))
                originalSource=new File(files(pkg),"CesiumLoader.Android/backups/"+original+"/__data");
            else throw new IOException("缺少与此补丁匹配的原版备份。");
        }
        require(originalSource,original);
        File info=new File(match.data.getParentFile(),"__info");validInfo(info);
        byte[] delta=entry("profiles/"+match.profile.getString("id")+"/cache.csdiff",LIMIT);
        require(delta,match.profile.getString("deltaSha256"));
        byte[] generated=DeltaPatch.apply(read(originalSource,LIMIT),delta);require(generated,patched);
        mkdir(backup);
        if(!new File(backup,"__data").exists())copy(originalSource,new File(backup,"__data"));
        require(new File(backup,"__data"),original);
        if(!new File(backup,"__info").exists()) {
            copy(info,new File(backup,"__info"));
            atomic(new File(backup,"info.sha256"),hash(new File(backup,"__info")).getBytes("UTF-8"));
        }
        verifyBackup(backup,original);
        installFiles(pkg,match.profile);
        File staged=new File(base,"staged-data");atomic(staged,generated);require(staged,patched);
        JSONObject record=new JSONObject().put("format",2).put("package",pkg).put("profile",match.profile.getString("id"))
            .put("cache",relative(files(pkg),match.data.getParentFile())).put("originalSha256",original).put("patchedSha256",patched);
        File pending=new File(base,"pending.json");atomic(pending,record.toString(2).getBytes("UTF-8"));
        if(env.running(pkg))throw new IOException("安装前发现游戏已启动，请退出后重试。");
        if(!hash(match.data).equals(current))throw new IOException("游戏缓存发生变化，停止写入。");
        try {publish(staged,match.data,patched);}
        catch(Exception failure) {
            publish(new File(backup,"__data"),match.data,original);pending.delete();
            throw new IOException("替换失败，已恢复原缓存。",failure);
        }
        configure(pkg,enabled);
        File disabled=new File(base,"DISABLED");if(disabled.exists()&&!disabled.delete())throw new IOException("无法清除停用标记");
        atomic(new File(base,"installed.json"),record.toString(2).getBytes("UTF-8"));pending.delete();
        return ok("安装完成，已备份并校验。现在可以启动游戏。").put("state","installed").put("package",pkg)
            .put("profile",match.profile.getString("id")).put("backup",backup.getAbsolutePath()).put("enabled",enabledList(pkg));
    }
    private void installFiles(String pkg,JSONObject profile)throws Exception {
        File base=root(pkg);JSONObject common=manifest.getJSONObject("commonFiles");
        Iterator<String> it=common.keys();
        while(it.hasNext()) {
            String name=it.next();File dest=inside(base,name);
            if(name.endsWith("/config.json")&&dest.isFile())continue;
            byte[] value=entry("common/"+name,LIMIT);require(value,common.getString(name));writePayloadFile(base,name,value,common.getString(name));
        }
        JSONObject files=profile.getJSONObject("files");it=files.keys();
        while(it.hasNext()) {
            String name=it.next();
            String dest=name.equals("CesiumLoader.Android.Bootstrap.dll")?"bootstrap/"+name:name;
            byte[] value=entry("profiles/"+profile.getString("id")+"/"+name,LIMIT);require(value,files.getString(name));
            writePayloadFile(base,dest,value,files.getString(name));
        }
    }
    private static void writePayloadFile(File base,String name,byte[] value,String sha)throws Exception {
        File dest=inside(base,name);atomic(dest,value);require(dest,sha);
        // Upgrade a previously disabled managed DLL as well, before configure moves it.
        if(name.matches("mods/[A-Za-z0-9_]+\\.dll")) {
            File off=inside(base,"disabled_mods/"+new File(name).getName());
            if(off.exists()){atomic(off,value);require(off,sha);}
        }
    }
    private void configure(String pkg,JSONArray enabled)throws Exception {
        if(enabled==null)enabled=new JSONArray();
        JSONArray mods=manifest.getJSONArray("mods");File base=root(pkg);
        for(int i=0;i<mods.length();i++) {
            String id=mods.getJSONObject(i).getString("id");
            File active=inside(base,"mods/"+id+".dll"),off=inside(base,"disabled_mods/"+id+".dll");
            File from=contains(enabled,id)?off:active,to=contains(enabled,id)?active:off;
            if(from.exists()) {
                mkdir(to.getParentFile());
                if(to.exists())require(to,hash(from));
                Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
        }
        atomic(new File(base,"enabled.json"),enabledList(pkg).toString(2).getBytes("UTF-8"));
    }
    private JSONArray enabledList(String pkg)throws Exception {
        JSONArray list=new JSONArray(),mods=manifest.getJSONArray("mods");
        for(int i=0;i<mods.length();i++){String id=mods.getJSONObject(i).getString("id");if(new File(root(pkg),"mods/"+id+".dll").isFile())list.put(id);}
        return list;
    }
    private JSONObject restore(String pkg,Match match)throws Exception {
        File base=root(pkg),backup=new File(base,"backups/"+match.profile.getString("originalSha256"));
        verifyBackup(backup,match.profile.getString("originalSha256"));
        String current=hash(match.data);
        if(!current.equals(match.profile.getString("originalSha256"))&&!current.equals(match.profile.getString("patchedSha256")))throw new IOException("当前缓存不是此安装器管理的版本。");
        publish(new File(backup,"__data"),match.data,match.profile.getString("originalSha256"));
        File info=new File(match.data.getParentFile(),"__info");publish(new File(backup,"__info"),info,hash(new File(backup,"__info")));
        atomic(new File(base,"DISABLED"),"restored\n".getBytes("UTF-8"));
        new File(base,"pending.json").delete();
        return ok("原版缓存已恢复并校验，模组和日志保留。").put("state","original");
    }
    private JSONObject status(String pkg)throws Exception {
        JSONObject out=ok("状态已读取。").put("package",pkg).put("enabled",enabledList(pkg));
        try{out.put("state",state(select(pkg)));}catch(Exception e){out.put("state","unsupported").put("detail",e.getMessage());}
        JSONObject logs=new JSONObject();File base=root(pkg);
        for(String n:new String[]{"logs/loader.log","bootstrap.log","logs/activity.log","logs/errors.log","logs/bootstrap_error.txt","logs/outer_error.txt"}) {
            File f=inside(base,n);if(f.isFile())logs.put(n,tail(f,24000));
        }
        return out.put("logs",logs);
    }
    private static void verifyBackup(File b,String sha)throws Exception {
        require(new File(b,"__data"),sha);File info=new File(b,"__info");validInfo(info);
        String expected=new String(read(new File(b,"info.sha256"),256),"UTF-8").trim();require(info,expected);
    }
    private byte[] entry(String name,long limit)throws Exception {
        ZipEntry e=zip.getEntry(name);if(e==null||e.isDirectory())throw new IOException("安装负载缺少文件："+name);
        return read(zip.getInputStream(e),limit);
    }
    private static boolean contains(JSONArray a,String s){if(a!=null)for(int i=0;i<a.length();i++)if(s.equals(a.optString(i)))return true;return false;}
    static long validInfo(File f)throws Exception {
        String s=new String(read(f,4096),"UTF-8");if(!s.matches("-1\\n[0-9]+\\n1\\n__data\\n"))throw new IOException("Unity 缓存元信息格式不匹配");
        return Long.parseLong(s.split("\\n")[1]);
    }
    static File inside(File base,String relative)throws Exception {
        if(relative.isEmpty()||relative.startsWith("/")||relative.contains("\\")||relative.contains(":"))throw new IOException("非法相对路径");
        File f=new File(base,relative).getCanonicalFile();
        if(!f.getPath().startsWith(base.getCanonicalPath()+File.separator))throw new IOException("路径越界");return f;
    }
    private static String relative(File base,File f)throws Exception {
        String prefix=base.getCanonicalPath()+File.separator,full=f.getCanonicalPath();
        if(!full.startsWith(prefix))throw new IOException("目标越界");return full.substring(prefix.length()).replace(File.separatorChar,'/');
    }
    static String hash(File f)throws Exception {try(InputStream in=new FileInputStream(f)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)md.update(b,0,n);return hex(md.digest());}}
    private static String hash(byte[] b)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    private static void require(File f,String expected)throws Exception{if(!hash(f).equals(expected))throw new IOException("文件校验失败："+f.getName());}
    private static void require(byte[] b,String expected)throws Exception{if(!hash(b).equals(expected))throw new IOException("负载校验失败");}
    static byte[] read(File f,long limit)throws Exception{return read(new FileInputStream(f),limit);}
    static byte[] read(InputStream input,long limit)throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("文件超过大小限制");out.write(b,0,n);}return out.toByteArray();}
    }
    private static void mkdir(File d)throws Exception{if(!d.isDirectory()&&!d.mkdirs())throw new IOException("无法创建目录："+d);}
    private static void write(File f,byte[] value)throws Exception{mkdir(f.getParentFile());try(FileOutputStream out=new FileOutputStream(f)){out.write(value);out.getFD().sync();}}
    private static void copy(File a,File b)throws Exception{write(b,read(a,LIMIT));}
    private static void atomic(File f,byte[] value)throws Exception {
        File temp=new File(f.getParentFile(),f.getName()+".cs-new");
        try{write(temp,value);Files.move(temp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(IOException e){
            if(!f.isFile()||!f.canWrite())throw e;
            write(f,value);if(temp.exists())temp.delete();
        }
    }
    private static void publish(File a,File b,String expected)throws Exception {
        require(a,expected);atomic(b,read(a,LIMIT));require(b,expected);
    }
    private static String tail(File f,int count)throws Exception{try(RandomAccessFile in=new RandomAccessFile(f,"r")){int n=(int)Math.min(in.length(),count);in.seek(in.length()-n);byte[] b=new byte[n];in.readFully(b);return new String(b,"UTF-8");}}
}
