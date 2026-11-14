package dev.cesium.androidmin;

import android.util.Base64;

/** app_process entry, invoked by the user's su manager under uid 0. */
public final class RootEntry {
    public static void main(String[] args) {
        String result;
        try {
            if(args.length!=2)throw new IllegalArgumentException("payload request");
            result=PrivilegedService.execute(args[0],new String(Base64.decode(args[1],Base64.NO_WRAP),"UTF-8"));
        }catch(Throwable e){result=PrivilegedService.failure(e);}
        try{System.out.println("CSDIRECT:"+Base64.encodeToString(result.getBytes("UTF-8"),Base64.NO_WRAP));}
        catch(Exception e){System.out.println("CSDIRECT_ERROR");}
        System.exit(0);
    }
}
