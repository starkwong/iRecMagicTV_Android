package com.studiokuma.irecmagictv;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.backup.BackupManager;
import android.app.backup.RestoreObserver;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    public IRecLibrary iRecLibrary;
    public boolean favoriteChanged;
    private Fragment fragments[] = new Fragment[4];
    private AdView adView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!getSharedPreferences(IRecLibrary.class.getSimpleName(), MODE_PRIVATE).getBoolean(getString(R.string.pref_unlink), false)) {
            BackupManager backupManager = new BackupManager(this);
            if (backupManager.requestRestore(new RestoreObserver() {
                @Override
                public void restoreFinished(int error) {
                    super.restoreFinished(error);

                    String payload="[]";
                    try {
                        payload=ChannelListFragment.readAsString(MainActivity.this,AppBackupAgent.FILE_ALERTS);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        JSONArray jsonArray = new JSONArray(payload);
                        JSONArray newArray = new JSONArray();
                        long timestamp = System.currentTimeMillis();

                        for (int c = 0; c < jsonArray.length(); c++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(c);
                            IRecLibrary.Programme programme = new IRecLibrary.Programme(jsonObject);
                            if (timestamp > programme.timestamp) {
                                newArray.put(jsonObject);

                                ProgrammeListFragment.scheduleAlert(MainActivity.this, jsonObject.getString("channel"), jsonObject.getString("channeltitle"), programme);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    startApp(savedInstanceState);
                }
            }) != 0) {
                // Backup transport init error
                startApp(savedInstanceState);
            }
        } else {
            startApp(savedInstanceState);
        }
    }

    private void startApp(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        // adView を作成する
        adView = new AdView(this);
        adView.setAdUnitId("");
        adView.setAdSize(AdSize.BANNER);

        // 属性 android:id="@+id/mainLayout" が与えられているものとして
        // LinearLayout をルックアップする
        LinearLayout layout = (LinearLayout)findViewById(R.id.root);

        // adView を追加する
        layout.addView(adView);

        // 一般的なリクエストを行う
        AdRequest adRequest = new AdRequest.Builder().build();

        // 広告リクエストを行って adView を読み込む
        if (!BuildConfig.DEBUG) {
            adView.loadAd(adRequest);
        }

        iRecLibrary = IRecLibrary.getsInstance(MainActivity.this);

        if (savedInstanceState == null) {
            tabButtonClick(((ViewGroup) findViewById(R.id.buttonsLayout)).getChildAt(0));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == android.R.id.home && ((getActionBar().getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0)) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
            try {
                Field field = fragment.getClass().getField("customBack");
                field.setAccessible(true);
                boolean customBack = field.getBoolean(fragment);
                if (!customBack) {
                    getFragmentManager().popBackStack();
                    return true;
                } else {
                    return super.onOptionsItemSelected(item);
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            getFragmentManager().popBackStack();
        }
        //noinspection SimplifiableIfStatement
        else if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }

    public void testButtonClick(View v) {
        /*
        new Thread() {
            @Override
            public void run() {
                IRecLibrary iRecLibrary=IRecLibrary.getsInstance();

                iRecLibrary.fetchRoot();
                iRecLibrary.login("96617499", "27989938");
            }
        }.start();*/
        IRecLibrary iRecLibrary = IRecLibrary.getsInstance(this);
        /*
        try {
            InputStream is=getAssets().open("channel-list.php#jump");
            byte[] bs=new byte[is.available()];
            is.read(bs);
            is.close();

            iRecLibrary.parseChannelList(new String(bs,"UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        iRecLibrary.test(this);
    }

    public void tabButtonClick(View v) {
        ViewGroup viewGroup = (ViewGroup) v.getParent();
        ImageButton button;
        int newIndex = -1;
        int curIndex = -1;

        for (int c = 0; c < viewGroup.getChildCount(); c++) {
            button = (ImageButton) viewGroup.getChildAt(c);
            if (button.isSelected()) curIndex = c;
            button.setSelected(button == v);
            if (button == v) newIndex = c;
        }

        String fragmentName = (String) v.getTag();
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.container);

        if (curIndex > -1) {
            fragments[curIndex] = currentFragment;
        }

        if (fragments[newIndex] == null) {
            try {
                Class<Fragment> cls = (Class<Fragment>) Class.forName(this.getPackageName() + "." + fragmentName);
                fragments[newIndex] = cls.newInstance();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                new AlertDialog.Builder(this)
                        .setMessage("Under Construction")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        getFragmentManager().beginTransaction().replace(R.id.container, fragments[newIndex]).commit();
    }

    public void back() {
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
        /*
        Fragment fragment=getFragmentManager().findFragmentById(R.id.container);
        try {
            Method method=fragment.getClass().getMethod("onBackPressed");
            method.invoke(fragment);
            return;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }*/
        if ((getActionBar().getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) == 0) {
            finish();
        } else {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
            try {
                Method method = fragment.getClass().getMethod("onBackPressed");
                method.invoke(fragment);
                return;
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            super.onBackPressed();
        }
    }

    public void triggerSync() {
        triggerSync(this);
    }

    public static void triggerSync(Context context) {
        if (!context.getSharedPreferences(IRecLibrary.class.getSimpleName(), MODE_PRIVATE).getBoolean(context.getString(R.string.pref_unlink), false)) {
            BackupManager backupManager = new BackupManager(context);
            backupManager.dataChanged();
        }
    }

    @Override
    public void onPause() {
        adView.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adView!=null) adView.resume();
    }

    @Override
    public void onDestroy() {
        adView.destroy();
        super.onDestroy();
    }
}