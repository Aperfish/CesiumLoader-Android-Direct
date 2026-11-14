package dev.cesium.androidmin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Exercises unknown channel caches through the real installation transaction. */
public final class ChannelCompatibilityTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static final class Env implements InstallerEngine.Environment {
        final File storage;final String pkg="com.feimo.astralparty.haoyou";
        Env(File storage){this.storage=storage;}
        public File storage(){return storage;}
        public List<String> packages(){return Collections.singletonList(pkg);}
        public boolean running(String p){return false;}
        public boolean nativeLoader(String p){return false;}
    }
    private static JSONObject req(String action,String pkg,boolean experimental)throws Exception {
        return new JSONObject().put("action",action).put("package",pkg)
            .put("experimental",experimental).put("enabled",new JSONArray().put("CombatOddsMod").put("CesiumOverlayMod"));
    }
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);File payload=new File(root,"android/assets/payload.zip");
        JSONObject manifest;
        try(ZipFile z=new ZipFile(payload)){manifest=new JSONObject(new String(InstallerEngine.read(z.getInputStream(z.getEntry("manifest.json")),1024*1024),"UTF-8"));}
        for(int n=0;n<manifest.getJSONArray("profiles").length();n++) {
            JSONObject profile=manifest.getJSONArray("profiles").getJSONObject(n);
            byte[] original=Files.readAllBytes(new File(root,"fixtures/original-"+profile.getString("id")+".bundle").toPath());
            byte[] runtime=new UnityBundle(original).runtime();
            File extracted=new File(root,"build/channel-fixtures/parsed-runtime.dll");Files.write(extracted.toPath(),runtime);
            check(InstallerEngine.hash(extracted).equals(profile.getJSONObject("channelRuntime").getString("originalSha256")),"original LZ4HC/padded bundle parsed");
        }
        for(String kind:new String[]{"repacked","different-runtime"}) {
            Env env=new Env(Files.createTempDirectory(new File(root,"build").toPath(),"channel-"+kind+"-").toFile());
            File cache=new File(env.storage,"Android/data/"+env.pkg+"/files/com.unity.addressables/AssetBundles/"+manifest.getString("bundleKey")+"/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");cache.mkdirs();
            File original=new File(root,"build/channel-fixtures/"+kind+".bundle"),data=new File(cache,"__data");
            Files.copy(original.toPath(),data.toPath());
            Files.write(new File(cache,"__info").toPath(),"-1\n1999999999\n1\n__data\n".getBytes("UTF-8"));
            String before=InstallerEngine.hash(data);
            try(EngineClose engine=new EngineClose(new InstallerEngine(env,payload))) {
                JSONObject discovered=engine.value.execute(req("discover",env.pkg,true));
                JSONObject game=discovered.getJSONArray("games").getJSONObject(0);
                check(game.getBoolean("supported"),"experimental channel discovered: "+kind+" reason="+game.optString("reason"));
                check(game.getString("compatibility").equals(kind.equals("repacked")?"runtime-match":"forced-reference"),"correct compatibility branch");
                if(kind.equals("different-runtime"))check(game.getString("referenceProfile").equals("aadfc2bcce57f6266d07f90b29bea89c"),"closest runtime reference selected");
                JSONObject installed=engine.value.execute(req("install",env.pkg,true));
                check(installed.getBoolean("ok"),"experimental installation completes");
                check(!InstallerEngine.hash(data).equals(before),"channel cache actually patched");
                File published=new File(root,"build/channel-fixtures/"+kind+".patched.bundle");
                Files.copy(data.toPath(),published.toPath(),StandardCopyOption.REPLACE_EXISTING);
                check(engine.value.execute(req("status",env.pkg,false)).getString("state").equals("installed"),"installed channel detected with switch off");
                engine.value.execute(req("configure",env.pkg,false));
                engine.value.execute(req("install",env.pkg,false));
                check(engine.value.execute(req("status",env.pkg,false)).getString("state").equals("installed"),"channel reinstall");
                // Recover an interrupted overwrite using the channel's original, not the reference cache.
                File base=new File(env.storage,"Android/data/"+env.pkg+"/files/CesiumLoader");
                Files.copy(new File(base,"installed.json").toPath(),new File(base,"pending.json").toPath(),StandardCopyOption.REPLACE_EXISTING);
                Files.write(data.toPath(),Arrays.copyOf(Files.readAllBytes(data.toPath()),1024));
                engine.value.execute(req("install",env.pkg,false));
                check(engine.value.execute(req("status",env.pkg,false)).getString("state").equals("installed"),"channel interrupted install recovered");
                engine.value.execute(req("restore",env.pkg,false));
                check(InstallerEngine.hash(data).equals(before),"restore exact channel original");
            }
            System.out.println("PASS channel "+kind);
        }
        System.out.println("PASS "+assertions+" channel transaction assertions");
    }
    private static final class EngineClose implements AutoCloseable {
        final InstallerEngine value;EngineClose(InstallerEngine value){this.value=value;}
        public void close()throws Exception{value.close();}
    }
}
