package dev.cesium.androidmin;
import android.content.*;

/** Shell-only test control protected by the system DUMP permission. */
public final class ControlReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent) {
        String action=intent.getStringExtra("operation");
        if(MainActivity.current!=null&&("discover".equals(action)||"install".equals(action)||"restore".equals(action)||"configure".equals(action)||"status".equals(action))) {
            MainActivity.current.control(action,intent.getStringExtra("gamePackage"),intent.getStringExtra("backend"));
            setResultCode(0);setResultData("queued "+action);
        }else{setResultCode(1);setResultData("Open the installer first");}
    }
}
