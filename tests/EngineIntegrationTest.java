package dev.cesium.androidmin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

public final class EngineIntegrationTest {
    private static int assertions;
    private static final class Env implements InstallerEngine.Environment {
        final File storage;final String pkg;boolean active,nativeLib;int runningChecks,startAt=Integer.MAX_VALUE;
        Env(File s,String p){storage=s;pkg=p;}
        public File storage(){return storage;}
        public List<String> packages(){return Arrays.asList(pkg,"com.feimo.astralpartyjpn");}
        public boolean running(String p){runningChecks++;return active||runningChecks>=startAt;}
        public boolean nativeLoader(String p){return nativeLib;}
    }
    private static void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}
    private static JSONObject req(String a,String pkg)throws Exception{return new JSONObject().put("action",a).put("package",pkg).put("enabled",new JSONArray().put("CombatOddsMod").put("LanguageUnlockMod").put("MapPeekMod").put("NoBotMod"));}
    private static void rejected(InstallerEngine engine,JSONObject request,String label)throws Exception{
        try{engine.execute(request);throw new AssertionError("accepted "+label);}catch(IOException expected){assertions++;}
    }
    private static void copy(File from,File to)throws Exception{to.getParentFile().mkdirs();Files.copy(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    private static File cache(Env e,String key,String id)throws Exception{return new File(e.storage,"Android/data/"+e.pkg+"/files/com.unity.addressables/AssetBundles/"+key+"/"+id);}
    public static void main(String[] args)throws Exception {
        File workspace=new File(args[0]).getCanonicalFile(),payload=new File(workspace,"android/assets/payload.zip");
        JSONObject manifest;
        try(ZipFile z=new ZipFile(payload)){manifest=new JSONObject(new String(InstallerEngine.read(z.getInputStream(z.getEntry("manifest.json")),1024*1024),"UTF-8"));}
        File sandbox=Files.createTempDirectory(new File(workspace,"build").toPath(),"engine-test-").toFile();
        JSONArray profiles=manifest.getJSONArray("profiles");
        JSONArray allMods=new JSONArray();
        for(int i=0;i<manifest.getJSONArray("mods").length();i++)allMods.put(manifest.getJSONArray("mods").getJSONObject(i).getString("id"));
        for(int n=0;n<profiles.length();n++) {
            JSONObject p=profiles.getJSONObject(n);String id=p.getString("id");
            String pkg=n%2==0?"com.feimo.astralparty":"com.feimo.jxpd.bilibili";
            Env env=new Env(new File(sandbox,"user-"+n),pkg);File target=cache(env,manifest.getString("bundleKey"),id);target.mkdirs();
            File data=new File(target,"__data"),info=new File(target,"__info"),original=new File(workspace,"fixtures/original-"+id+".bundle");
            copy(original,data);Files.write(info.toPath(),"-1\n1790000000\n1\n__data\n".getBytes("UTF-8"));
            InstallerEngine engine=new InstallerEngine(env,payload);
            JSONObject detected=engine.execute(new JSONObject().put("action","discover"));
            check(detected.getJSONArray("games").length()==1,"Japanese excluded");
            check(detected.getJSONArray("games").getJSONObject(0).getBoolean("supported"),"profile discovered");
            engine.execute(req("install",pkg).put("enabled",allMods));check(InstallerEngine.hash(data).equals(p.getString("patchedSha256")),"generated cache hash");
            File base=new File(env.storage,"Android/data/"+pkg+"/files/CesiumLoader");
            for(int i=0;i<allMods.length();i++)check(new File(base,"mods/"+allMods.getString(i)+".dll").isFile(),"every selected mod installed: "+allMods.getString(i));
            JSONObject expectedFiles=manifest.getJSONObject("commonFiles");Iterator<String> expectedNames=expectedFiles.keys();
            while(expectedNames.hasNext()){String name=expectedNames.next();check(InstallerEngine.hash(new File(base,name)).equals(expectedFiles.getString(name)),"common payload hash: "+name);}
            expectedFiles=p.getJSONObject("files");expectedNames=expectedFiles.keys();
            while(expectedNames.hasNext()){String name=expectedNames.next();String path=name.equals("CesiumLoader.Android.Bootstrap.dll")?"bootstrap/"+name:name;check(InstallerEngine.hash(new File(base,path)).equals(expectedFiles.getString(name)),"profile payload hash: "+name);}
            File backup=new File(base,"backups/"+p.getString("originalSha256")+"/__data");
            check(InstallerEngine.hash(backup).equals(p.getString("originalSha256")),"backup original hash");
            check(new File(base,"mods/NoBotMod.dll").isFile(),"ZIP NoBot installed");
            Files.write(info.toPath(),"-1\n1899999999\n1\n__data\n".getBytes("UTF-8"));
            engine.execute(req("install",pkg));check(InstallerEngine.hash(data).equals(p.getString("patchedSha256")),"reinstall after access timestamp");
            engine.execute(req("configure",pkg).put("enabled",new JSONArray().put("CombatOddsMod")));
            check(new File(base,"disabled_mods/NoBotMod.dll").isFile()&&!new File(base,"mods/NoBotMod.dll").exists(),"disable moves DLL");
            engine.execute(req("configure",pkg));check(new File(base,"mods/NoBotMod.dll").isFile(),"enable moves DLL");
            Files.write(new File(base,"disabled_mods/SpeedHackMod.dll").toPath(),new byte[]{9,8,7});
            engine.execute(req("install",pkg).put("enabled",allMods));
            check(!new File(base,"disabled_mods/SpeedHackMod.dll").exists(),"disabled old version upgraded and enabled");
            check(engine.execute(req("status",pkg)).getJSONArray("enabled").length()==allMods.length(),"all mods active after version upgrade");
            env.active=true;rejected(engine,req("restore",pkg),"running game");env.active=false;
            engine.execute(req("restore",pkg));check(InstallerEngine.hash(data).equals(p.getString("originalSha256")),"restore exact original");
            env.nativeLib=true;rejected(engine,req("install",pkg),"native loader conflict");env.nativeLib=false;
            // An interrupted direct overwrite is recoverable from the retained known backup.
            engine.execute(req("install",pkg));copy(new File(base,"installed.json"),new File(base,"pending.json"));
            byte[] first=Arrays.copyOf(Files.readAllBytes(data.toPath()),1024);Files.write(data.toPath(),first);
            engine.execute(req("install",pkg));check(InstallerEngine.hash(data).equals(p.getString("patchedSha256")),"partial write recovered");
            engine.execute(req("restore",pkg));
            // Never silently patch an old supported cache while a newer unknown cache is active.
            File newer=cache(env,manifest.getString("bundleKey"),"ffffffffffffffffffffffffffffffff");newer.mkdirs();
            Files.write(new File(newer,"__data").toPath(),new byte[]{1,2,3});Files.write(new File(newer,"__info").toPath(),"-1\n1999999999\n1\n__data\n".getBytes("UTF-8"));
            rejected(engine,req("install",pkg),"newer unknown cache");check(InstallerEngine.hash(data).equals(p.getString("originalSha256")),"old original retained");
            new File(newer,"__data").delete();new File(newer,"__info").delete();newer.delete();
            byte[] corrupted=Files.readAllBytes(data.toPath());corrupted[0]^=1;Files.write(data.toPath(),corrupted);
            String corruptHash=InstallerEngine.hash(data);rejected(engine,req("install",pkg),"unknown cache");check(InstallerEngine.hash(data).equals(corruptHash),"unknown cache untouched");
            copy(original,data);rejected(engine,req("install","com.feimo.astralparty/../../escape"),"package traversal");
            engine.close();System.out.println("PASS profile "+id+" package="+pkg);
        }
        // Migration from the previously verified prototype preserves the original backup.
        JSONObject p=profiles.getJSONObject(0);
        for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).getString("id").equals("0c6650c5e9eccffde61b8397d4694f72"))p=profiles.getJSONObject(i);
        Env migrated=new Env(new File(sandbox,"migration"),"com.feimo.astralparty");File target=cache(migrated,manifest.getString("bundleKey"),p.getString("id"));target.mkdirs();
        File legacy=new File(workspace,"fixtures/legacy-min.bundle");if(!legacy.isFile()){System.out.println("SKIP optional legacy migration fixture; PASS "+assertions+" assertions");return;}copy(legacy,new File(target,"__data"));
        Files.write(new File(target,"__info").toPath(),"-1\n1790000000\n1\n__data\n".getBytes("UTF-8"));
        File oldBackup=new File(migrated.storage,"Android/data/"+migrated.pkg+"/files/CesiumLoader.Android/backups/"+p.getString("originalSha256")+"/__data");
        copy(new File(workspace,"fixtures/original-"+p.getString("id")+".bundle"),oldBackup);
        InstallerEngine migration=new InstallerEngine(migrated,payload);
        migrated.runningChecks=0;migrated.startAt=2;
        rejected(migration,req("install",migrated.pkg),"game starts before legacy publish");
        check(InstallerEngine.hash(new File(target,"__data")).equals(p.getJSONArray("legacyMinSha256").getString(0)),"legacy target unchanged before publish");
        migrated.runningChecks=0;migrated.startAt=Integer.MAX_VALUE;
        migration.execute(req("install",migrated.pkg));
        check(InstallerEngine.hash(new File(target,"__data")).equals(p.getString("patchedSha256")),"legacy migration patched");
        check(InstallerEngine.hash(oldBackup).equals(p.getString("originalSha256")),"legacy backup preserved");migration.close();
        System.out.println("PASS "+assertions+" assertions; 4 profiles; original/install/reinstall/configure/restore/interruption/migration/negative guards");
    }
}
