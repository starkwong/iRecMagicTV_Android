package com.studiokuma.irecmagictv;

import android.app.KeyguardManager;
import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by starkwong on 15/01/24.
 */
public class EventReceiver extends BroadcastReceiver {
    private static PowerManager.WakeLock wakeLock;
    private static KeyguardManager.KeyguardLock keyguardLock;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("alarm")) {
            if (wakeLock==null) {
                PowerManager powerManager= (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock=powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"EventReceiver");
                wakeLock.acquire();

                KeyguardManager keyguardManager= (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                keyguardLock=keyguardManager.newKeyguardLock("EventReceiver");
                keyguardLock.disableKeyguard();
            }

            Intent newIntent=new Intent(context, AlarmEventActivity.class);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            newIntent.putExtras(intent);
            context.startActivity(newIntent);
        } else if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            String payload="[]";
            try {
                payload=ChannelListFragment.readAsString(context,AppBackupAgent.FILE_ALERTS);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                JSONArray jsonArray=new JSONArray(payload);
                JSONArray newArray=new JSONArray();
                long timestamp=System.currentTimeMillis();

                for (int c=0; c<jsonArray.length(); c++) {
                    JSONObject jsonObject=jsonArray.getJSONObject(c);
                    IRecLibrary.Programme programme=new IRecLibrary.Programme(jsonObject);
                    if (programme.multi==false && timestamp>programme.timestamp) {
                        newArray.put(jsonObject);

                        ProgrammeListFragment.scheduleAlert(context, jsonObject.getString("channel"), jsonObject.getString("channeltitle"), programme);
                    }
                }

                if (newArray.length()!=jsonArray.length()) {
                    try {
                        FileOutputStream fileOutputStream=context.openFileOutput(AppBackupAgent.FILE_ALERTS,Context.MODE_PRIVATE);
                        fileOutputStream.write(newArray.toString().getBytes("UTF-8"));
                        fileOutputStream.close();

                        MainActivity.triggerSync(context);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static void releaseWakeLock() {
        if (wakeLock!=null) {
            wakeLock.release();
            wakeLock=null;
        }
    }

    public static void releaseKeyguard() {
        if (keyguardLock!=null) {
            keyguardLock.reenableKeyguard();
            keyguardLock=null;
        }
    }
}
