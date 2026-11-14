package dev.cesium.androidmin;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Base64;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    static MainActivity current;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,CheckBox> mods=new LinkedHashMap<>();
    private TextView output,state;
    private Spinner backendChoice;
    private CheckBox experimental;
    private volatile IBinder binder;
    private volatile boolean busy;
    private volatile String backend="";
    private String pending;
    private String gamePackage;
    private Shizuku.UserServiceArgs args;
    private Shizuku.OnBinderReceivedListener received;
    private Shizuku.OnBinderDeadListener dead;
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->{
        if(result==0)connectShizuku();else fail("Shizuku 未授权，尚未修改游戏。");
    };
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){binder=service;backend="shizuku";show("Shizuku 文件服务已连接");resumePending();}
        public void onServiceDisconnected(ComponentName name){binder=null;backend="";show("文件服务已断开，重新点击操作可连接。");}
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);current=this;
        gamePackage=getPreferences(0).getString("game",null);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,20,24,24);
        TextView title=new TextView(this);title.setText("Cesium 安卓模组包 0.5.0 实验版");title.setTextSize(23);layout.addView(title);
        TextView help=new TextView(this);help.setText("先完成游戏资源下载并退出游戏。授权后可一键安装，原版缓存会自动备份。\n支持国服和渠道后缀识别；开启实验兼容可尝试未收录缓存。不需要卸载官方游戏。");help.setTextSize(14);layout.addView(help);
        backendChoice=new Spinner(this);backendChoice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动选择（root 优先）","使用 Shizuku","使用 root"}));
        backendChoice.setSelection(getPreferences(0).getInt("backend",0));layout.addView(backendChoice);
        experimental=new CheckBox(this);experimental.setText("实验性强制兼容渠道资源（未知代码使用 3.3.0 基准）");
        experimental.setChecked(getPreferences(0).getBoolean("experimental",true));layout.addView(experimental);
        TextView channelHelp=new TextView(this);channelHelp.setText("已识别的渠道后缀共用安装入口。开启实验兼容后，不再要求整个缓存匹配：先按内部程序集匹配；未知程序集按最接近的内置 3.3.0 基准替换。其他程序集保留，原缓存仍会备份。可能影响渠道功能；不正常时退出游戏并恢复原版。");layout.addView(channelHelp);
        button(layout,"操作与适配说明",()->new AlertDialog.Builder(this).setTitle("安卓适配测试包 0.5.0").setMessage("收录所选目录内 27 个 Mod，保留 NoBot，并增加 AndroidApi 触屏入口与 Cesium 悬浮窗。SpeedPerfProbe 无 DLL，未收录。\n\n左侧头像按钮打开悬浮窗：左栏选择模组，右栏显示功能；列表和功能都可翻页。窗口与头像可拖动。\n\n变速：选择变速模组，使用 1–10x 预设与加减、1x/记忆切换。使用 Unity 时间倍率，系统时钟不变，启动保持 1x。\n\n自由相机：仅支持双指纵滑或捏合缩放，横向平移不旋转；模组控制面板可复位。单指保留游戏操作。\n\n模组控制面板提供 HUD、角色半透明、刷怪预报、商店看板、快捷购买与 PVE 面板入口。\n\n回合置前改为游戏内文字提醒，不切换后台应用。皮肤/音效 Mod 需要另备方案素材；本包包含原目录内的数据、图标及提示音。\n\n已修复旧版在主线程加载时未启动每帧回调的问题。仓库 AndroidApi 已接入；窗口保留头像、左侧模组与右侧功能结构，使用 FairyGUI 作为当前免改包方案的窗口宿主。\n\n已完成四份 3.3.0 资源 API 静态检查与构建检查，未在手机运行本版本。root 完整安装仍未实测。实验兼容默认开启：整份缓存未收录也会尝试安装；内部程序集匹配时采用对应补丁，否则使用大小最接近的 3.3.0 基准运行程序集。其他程序集保留。未知代码版本可能不兼容，安装后需要实测。关闭实验兼容可恢复严格匹配。更新旧版后需要退出游戏，再点一键安装以更新 Mod 文件。").setPositiveButton("知道了",null).show());
        button(layout,"全选模组",()->{for(CheckBox c:mods.values())c.setChecked(true);});
        button(layout,"清空选择（保留触屏入口）",()->{for(Map.Entry<String,CheckBox> e:mods.entrySet())e.getValue().setChecked((e.getKey().equals("AndroidControlsMod")||e.getKey().equals("CesiumOverlayMod")));});
        try(InputStream in=getAssets().open("mod-list.json")){
            JSONArray list=new JSONArray(new String(InstallerEngine.read(in,1024*1024),"UTF-8"));
            for(int i=0;i<list.length();i++){JSONObject mod=list.getJSONObject(i);addMod(layout,mod.getString("id"),mod.getString("name"));}
            mods.get("AndroidControlsMod").setChecked(true);mods.get("AndroidControlsMod").setEnabled(false);mods.get("CesiumOverlayMod").setChecked(true);mods.get("CesiumOverlayMod").setEnabled(false);
        }catch(Exception e){throw new IllegalStateException("模组清单损坏",e);}
        button(layout,"一键安装",()->start("install"));
        button(layout,"检测游戏与环境",()->start("discover"));
        button(layout,"保存模组启用项",()->start("configure"));
        button(layout,"读取状态与日志",()->start("status"));
        button(layout,"恢复原版缓存",()->start("restore"));
        button(layout,"启动所选游戏",()->{
            if(gamePackage==null){show("先检测或安装游戏。");return;}
            Intent launch=getPackageManager().getLaunchIntentForPackage(gamePackage);
            if(launch==null){show("没有找到游戏启动入口。");return;}startActivity(launch);
        });
        state=new TextView(this);state.setTextSize(15);layout.addView(state);
        output=new TextView(this);output.setTextIsSelectable(true);output.setTextSize(12);layout.addView(output);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
        received=()->main.post(()->{if(pending!=null)ensureShizuku();});
        dead=()->main.post(()->{binder=null;if(backend.equals("shizuku")){backend="";busy=false;show("Shizuku 已停止。启动后重新点击操作即可。");}});
        Shizuku.addRequestPermissionResultListener(permission);Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(dead);
        show("30 项启用项 · 渠道实验兼容 · 更新后请重新执行一键安装");
    }
    private void addMod(LinearLayout parent,String id,String label){CheckBox c=new CheckBox(this);c.setText(label);c.setChecked(getPreferences(0).getBoolean(id,true));mods.put(id,c);parent.addView(c);}
    private void button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());parent.addView(b);}
    private void show(String text){main.post(()->{if(state!=null)state.setText(text);});}
    private void append(String text){main.post(()->{if(output!=null)output.append(text+"\n");});}
    private void fail(String text){busy=false;pending=null;show(text);append(text);}

    void control(String action,String pkg,String selectedBackend) {
        if(pkg!=null&&InstallerEngine.allowedPackage(pkg))gamePackage=pkg;
        if("root".equals(selectedBackend))backendChoice.setSelection(2);
        if("shizuku".equals(selectedBackend))backendChoice.setSelection(1);
        start(action);
    }
    private void start(String action) {
        if(busy){show("操作进行中，请稍候。");return;}
        busy=true;pending=action;
        int choice=backendChoice.getSelectedItemPosition();getPreferences(0).edit().putInt("backend",choice).apply();
        if(choice==1){ensureShizuku();return;}
        if(backend.equals("root")){resumePending();return;}
        show("检查 root 授权…");
        worker.execute(()->{
            boolean granted=false;
            try {
                java.lang.Process p=new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start();
                if(!p.waitFor(15,TimeUnit.SECONDS))p.destroyForcibly();
                else granted=p.exitValue()==0&&new String(InstallerEngine.read(p.getInputStream(),8192),"UTF-8").trim().equals("0");
            }catch(Exception ignored){}
            final boolean root=granted;
            main.post(()->{if(root){backend="root";show("root 已授权");resumePending();}else if(choice==2)fail("未获得 root。可切换为 Shizuku。");else ensureShizuku();});
        });
    }
    private void ensureShizuku() {
        try {
            if(!Shizuku.pingBinder()){fail("请先启动 Shizuku 服务，再点击一键安装。");return;}
            if(Shizuku.checkSelfPermission()!=0){show("请在弹窗允许 Shizuku 授权");Shizuku.requestPermission(200);return;}
            if(binder!=null&&binder.pingBinder()){backend="shizuku";resumePending();return;}
            connectShizuku();
        }catch(Throwable e){fail("连接 Shizuku 失败："+e.getMessage());}
    }
    private void connectShizuku() {
        try {
            show("连接 Shizuku 文件服务…");
            args=new Shizuku.UserServiceArgs(new ComponentName(this,PrivilegedService.class)).daemon(false).debuggable(false).version(50).processNameSuffix("direct-files").tag("cesium-direct-files");
            Shizuku.bindUserService(args,connection);
            main.postDelayed(()->{if(pending!=null&&(binder==null||!binder.pingBinder()))fail("文件服务未连接，请重启 Shizuku 后重试。");},12000);
        }catch(Throwable e){fail("启动文件服务失败："+e.getMessage());}
    }
    private void resumePending() {
        if(pending==null)return;String action=pending;pending=null;
        runRequest("discover",null,null,result->{
            if(!result.optBoolean("ok")){finish(result);return;}
            JSONArray games=result.optJSONArray("games");
            if(action.equals("discover")){finish(result);return;}
            List<JSONObject> candidates=new ArrayList<>();
            if(games!=null)for(int i=0;i<games.length();i++){JSONObject g=games.optJSONObject(i);if(g!=null&&g.optBoolean("supported"))candidates.add(g);}
            if(candidates.isEmpty()){finish(result);show("未找到可安装的资源版本，详细原因见下方。");return;}
            JSONObject chosen=null;
            for(JSONObject g:candidates)if(g.optString("package").equals(gamePackage))chosen=g;
            if(chosen==null&&candidates.size()==1)chosen=candidates.get(0);
            if(chosen!=null){runGameAction(action,chosen.optString("package"));return;}
            String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).optString("package")+" · "+candidates.get(i).optString("gameVersion");
            new AlertDialog.Builder(this).setTitle("选择要操作的游戏").setItems(labels,(d,n)->runGameAction(action,candidates.get(n).optString("package"))).setOnCancelListener(d->fail("已取消，未修改缓存。")).show();
        });
    }
    private void runGameAction(String action,String pkg) {
        gamePackage=pkg;getPreferences(0).edit().putString("game",pkg).apply();
        JSONArray enabled=new JSONArray();for(Map.Entry<String,CheckBox> e:mods.entrySet()){if(e.getValue().isChecked())enabled.put(e.getKey());getPreferences(0).edit().putBoolean(e.getKey(),e.getValue().isChecked()).apply();}
        runRequest(action,pkg,enabled,this::finish);
    }
    private interface Result{void done(JSONObject result);}
    private void runRequest(String action,String pkg,JSONArray enabled,Result callback) {
        final boolean useExperimental=experimental.isChecked();getPreferences(0).edit().putBoolean("experimental",useExperimental).apply();
        show(action.equals("install")?"校验、备份并生成适配补丁…":"正在处理…");
        worker.execute(()->{
            JSONObject result;
            try {
                File stage=new File(getExternalFilesDir(null),"payload.zip");
                if(!stage.isFile()||!digest(stage).equals(BuildInfo.PAYLOAD_SHA256)) {
                    try(InputStream in=getAssets().open("payload.zip");FileOutputStream out=new FileOutputStream(stage)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
                }
                JSONObject request=new JSONObject().put("action",action).put("experimental",useExperimental);
                if(pkg!=null)request.put("package",pkg);if(enabled!=null)request.put("enabled",enabled);
                String json;
                if(backend.equals("root")) {
                    String encoded=Base64.encodeToString(request.toString().getBytes("UTF-8"),Base64.NO_WRAP);
                    String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" /system/bin/app_process / dev.cesium.androidmin.RootEntry "+quote(stage.getAbsolutePath())+" "+quote(encoded);
                    java.lang.Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                    String raw=new String(InstallerEngine.read(process.getInputStream(),1024*1024),"UTF-8");process.waitFor();json=null;
                    for(String line:raw.split("\\r?\\n"))if(line.startsWith("CSDIRECT:"))json=new String(Base64.decode(line.substring(9),Base64.NO_WRAP),"UTF-8");
                    if(json==null)throw new IOException("root 文件服务未返回结果："+raw);
                } else {
                    IBinder service=binder;if(service==null||!service.pingBinder())throw new IOException("Shizuku 文件服务已断开，请重试。");
                    Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                    try{data.writeInterfaceToken(PrivilegedService.DESCRIPTOR);data.writeString(stage.getAbsolutePath());data.writeString(request.toString());service.transact(IBinder.FIRST_CALL_TRANSACTION,data,reply,0);reply.readException();json=reply.readString();}
                    finally{data.recycle();reply.recycle();}
                }
                result=new JSONObject(json).put("backend",backend);
                try(FileOutputStream out=new FileOutputStream(new File(getExternalFilesDir(null),"last-result.json"))){out.write(result.toString(2).getBytes("UTF-8"));}
            }catch(Throwable e){try{result=new JSONObject(PrivilegedService.failure(e)).put("backend",backend);}catch(Exception ignored){result=new JSONObject();}}
            final JSONObject value=result;main.post(()->callback.done(value));
        });
    }
    private void finish(JSONObject result) {
        busy=false;show(result.optString("message","处理失败"));
        try{append(result.toString(2));}catch(Exception ignored){}
        JSONArray enabled=result.optJSONArray("enabled");
        if(enabled!=null)for(Map.Entry<String,CheckBox> e:mods.entrySet()){boolean found=false;for(int i=0;i<enabled.length();i++)if(e.getKey().equals(enabled.optString(i)))found=true;e.getValue().setChecked(found);}
    }
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private static String digest(File f)throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    @Override public void onDestroy(){if(current==this)current=null;Shizuku.removeRequestPermissionResultListener(permission);if(received!=null)Shizuku.removeBinderReceivedListener(received);if(dead!=null)Shizuku.removeBinderDeadListener(dead);super.onDestroy();}
}
