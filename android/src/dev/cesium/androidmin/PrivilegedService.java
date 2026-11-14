package dev.cesium.androidmin;

import android.os.*;
import java.io.File;
import org.json.*;

public final class PrivilegedService extends Binder {
    public static final String DESCRIPTOR="dev.cesium.androidmin.DirectFileService";
    public PrivilegedService(){attachInterface(null,DESCRIPTOR);}
    public static String execute(String payload,String request)throws Exception {
        int uid=android.os.Process.myUid();if(uid!=0&&uid!=2000)throw new SecurityException("文件后端没有 root/shell 权限");
        File source=new File(payload);AndroidEnvironment environment=new AndroidEnvironment(source);
        InstallerEngine engine=new InstallerEngine(environment,source);
        try{return engine.execute(new JSONObject(request)).put("uid",uid).toString();}finally{engine.close();}
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) {
        if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        if(code==16777115){System.exit(0);return true;}
        if(code!=IBinder.FIRST_CALL_TRANSACTION)return false;
        data.enforceInterface(DESCRIPTOR);
        String path=data.readString(),request=data.readString(),result;
        try{result=execute(path,request);}catch(Throwable e){result=failure(e);}
        android.util.Log.i("CesiumDirect",result);
        reply.writeNoException();reply.writeString(result);return true;
    }
    public static String failure(Throwable e) {
        try{return new JSONObject().put("ok",false).put("message",e.getMessage()==null?e.toString():e.getMessage()).put("errorType",e.getClass().getName()).toString();}
        catch(Exception ignored){return "{\"ok\":false,\"message\":\"文件操作失败\"}";}
    }
}
