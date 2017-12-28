package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Sheep on 2015/01/25.
 */
public class AlarmEventActivity extends Activity implements DialogInterface.OnClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        /*
        intent.putExtra("programme",programme.toJSONObject().toString());
        intent.putExtra("channel",channel);
        intent.putExtra("title",title);
        */

        Intent intent=getIntent();
        try {
            JSONObject jsonObject=new JSONObject(intent.getStringExtra("programme"));
            IRecLibrary.Programme programme=new IRecLibrary.Programme(jsonObject);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.aea_start,programme.progname,intent.getStringExtra("title")))
                    .setPositiveButton(android.R.string.ok,this)
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        EventReceiver.releaseWakeLock();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        finish();
        EventReceiver.releaseKeyguard();
    }
}
