package dev.cesium.androidmin;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.zip.ZipFile;

public final class AndroidEnvironment implements InstallerEngine.Environment {
    private final File storage;
    private final String user;
    public AndroidEnvironment(File payload)throws Exception {
        Matcher m=Pattern.compile("^(/storage/emulated/([0-9]+))/Android/data/dev\\.cesium\\.androidmin/files/payload\\.zip$").matcher(payload.getCanonicalPath());
        if(!m.matches())throw new IOException("安装负载不在本应用的暂存目录");
        storage=new File(m.group(1));user=m.group(2);
    }
    public File storage(){return storage;}
    public List<String> packages()throws Exception {
        String output=command("/system/bin/pm","list","packages","--user",user,"com.feimo");
        List<String> result=new ArrayList<>();for(String line:output.split("\\r?\\n"))if(line.startsWith("package:")) {
            String p=line.substring(8).trim();if(InstallerEngine.allowedPackage(p))result.add(p);
        }
        Collections.sort(result);return result;
    }
    public boolean running(String pkg)throws Exception{return !command("/system/bin/pidof",pkg).trim().isEmpty();}
    public boolean nativeLoader(String pkg)throws Exception {
        String paths=command("/system/bin/pm","path","--user",user,pkg);
        for(String line:paths.split("\\r?\\n"))if(line.startsWith("package:")) {
            File apk=new File(line.substring(8).trim());
            try(ZipFile z=new ZipFile(apk)) {
                for(String abi:new String[]{"arm64-v8a","armeabi-v7a","x86_64","x86"})if(z.getEntry("lib/"+abi+"/libcesium.so")!=null)return true;
            }
        }
        return false;
    }
    static String command(String... args)throws Exception {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("系统命令超时");}
        return new String(InstallerEngine.read(p.getInputStream(),1024*1024),"UTF-8");
    }
}
