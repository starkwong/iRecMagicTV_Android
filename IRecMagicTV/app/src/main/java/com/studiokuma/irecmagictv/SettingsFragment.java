package com.studiokuma.irecmagictv;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/**
 * Created by starkwong on 15/02/01.
 */
public class SettingsFragment extends BasePreferenceFragment implements Preference.OnPreferenceClickListener, IRecLibrary.OnIRecListResult {
    private class DeviceListAdapter extends ArrayAdapter<IRecLibrary.Device> {
        public DeviceListAdapter(Context context, int resource, IRecLibrary.Device[] objects) {
            super(context, resource, objects);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(IRecLibrary.class.getSimpleName());
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getMainActivity().setTitle(R.string.sf_title);

        updateStatus();
    }

    void updateStatus() {
        Preference preference=findPreference(getString(R.string.pref_logout));
        preference.setSummary(getPreferenceManager().getSharedPreferences().getString("username", ""));
        preference.setOnPreferenceClickListener(this);

        preference=findPreference(getString(R.string.pref_device));
        preference.setSummary(getPreferenceManager().getSharedPreferences().getString("device", "?"));
        preference.setOnPreferenceClickListener(this);

        preference=findPreference(getString(R.string.pref_unlink));
        preference.setOnPreferenceClickListener(this);
        preference.setSummary(getString(getPreferenceManager().getSharedPreferences().getBoolean(getString(R.string.pref_unlink), false) ? R.string.pref_unlink_yes : R.string.pref_unlink_no));

        preference=findPreference(getString(R.string.pref_language));
        preference.setSummary(getResources().getStringArray(R.array.langugaes)[Integer.parseInt(getPreferenceManager().getSharedPreferences().getString(getString(R.string.pref_language),"0"))]);
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, Object o) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(preference.getTitle())
                        .setMessage(R.string.sf_restart)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final Context context = getActivity().getApplicationContext();
                                getActivity().finish();
                                new Thread() {
                                    @Override
                                    public void run() {
                                        SharedPreferences sharedPreferences = context.getSharedPreferences(IRecLibrary.class.getSimpleName(), Context.MODE_PRIVATE);
                                        Locale.setDefault(new Locale[]{((Application) getActivity().getApplication()).defaultLocale, Locale.ENGLISH, Locale.TRADITIONAL_CHINESE}[Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_language), "0"))]);
                                        IRecLibrary.getsInstance(context).resetLogin();
                                        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                                    }
                                }.start();
                            }
                        }).show();
                return true;
            }
        });

        try {
            PackageInfo pi=getMainActivity().getPackageManager().getPackageInfo(getMainActivity().getPackageName(), 0);
            preference=findPreference(getString(R.string.pref_about));
            preference.setSummary(getString(R.string.app_name)+" v"+pi.versionName);
            preference.setOnPreferenceClickListener(this);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        String payload = "[]";
        try {
            payload = ChannelListFragment.readAsString(getMainActivity(), AppBackupAgent.FILE_ALERTS);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            JSONArray jsonArray = new JSONArray(payload);
            JSONArray newArray = new JSONArray();
            long timestamp = System.currentTimeMillis();
            AlarmManager alarmManager= (AlarmManager) getMainActivity().getSystemService(Context.ALARM_SERVICE);
            Intent intent=new Intent(getMainActivity(), EventReceiver.class);
            intent.setAction("alarm");

            for (int c = 0; c < jsonArray.length(); c++) {
                JSONObject jsonObject = jsonArray.getJSONObject(c);
                IRecLibrary.Programme programme = new IRecLibrary.Programme(jsonObject);
                if (timestamp > programme.timestamp) {
                    newArray.put(jsonObject);
                    PendingIntent pendingIntent=PendingIntent.getBroadcast(getMainActivity(), (int)programme.timestamp, intent, PendingIntent.FLAG_CANCEL_CURRENT);

                    alarmManager.cancel(pendingIntent);
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        getActivity().deleteFile(AppBackupAgent.FILE_ALERTS);
        getActivity().deleteFile(AppBackupAgent.FILE_RECORDINGS);
        getActivity().deleteFile(AppBackupAgent.FILE_HISTORY);
        getActivity().deleteFile(AppBackupAgent.FILE_FAVORITES);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(getString(R.string.pref_device))) {
            ProgressIndicator.showIndicator(getActivity());
            getMainActivity().iRecLibrary.getUnitList(this);
        } else if (preference.getKey().equals(getString(R.string.pref_logout))) {
            new AlertDialog.Builder(getMainActivity())
                    .setTitle(preference.getTitle())
                    .setMessage(R.string.sf_logout_message)
                    .setPositiveButton(R.string.sf_logout_clear_logout, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_unlink),false).remove("username").remove("password").remove("device").commit();

                            cleanup();

                            IRecLibrary.clearSession();

                            restartApplication(getMainActivity());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (preference.getKey().equals(getString(R.string.pref_unlink))) {
            DialogInterface.OnClickListener clearaOcl=new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    DialogInterface.OnClickListener clearOcl2=new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // "favorites.json","recordings.json","history.json","alerts.json"

                            cleanup();

                            getMainActivity().triggerSync();

                            restartApplication(getMainActivity());
                        }
                    };

                    if (getPreferenceManager().getSharedPreferences().getBoolean(getString(R.string.pref_unlink),false)) {
                        // Unlink: clear
                        new AlertDialog.Builder(getMainActivity())
                                .setTitle(((AlertDialog)dialogInterface).getButton(i).getText())
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setMessage(R.string.sf_unlink_local)
                                .setPositiveButton(android.R.string.ok, clearOcl2)
                                .setNegativeButton(android.R.string.cancel,null)
                                .show();

                    } else {
                        // Link: clear
                        new AlertDialog.Builder(getMainActivity())
                                .setTitle(((AlertDialog)dialogInterface).getButton(i).getText())
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setMessage(R.string.sf_unlink_all)
                                .setPositiveButton(android.R.string.ok, clearOcl2)
                                .setNegativeButton(android.R.string.cancel,null)
                                .show();
                    }
                }
            };

            if (getPreferenceManager().getSharedPreferences().getBoolean(getString(R.string.pref_unlink),false)) {
                new AlertDialog.Builder(getMainActivity())
                        .setTitle(preference.getTitle())
                        .setMessage(preference.getSummary()+"\n\n"+getString(R.string.sf_unlink_message1))
                        .setPositiveButton(R.string.sf_unlink_link, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new AlertDialog.Builder(getMainActivity())
                                        .setTitle(((AlertDialog)dialogInterface).getButton(i).getText())
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setMessage(R.string.sf_unlink_link_message)
                                        .setNegativeButton(android.R.string.cancel,null)
                                        .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_unlink),false).commit();
                                                getMainActivity().triggerSync();
                                            }
                                        }).show();
                            }
                        }).setNegativeButton(R.string.sf_unlink_clear, clearaOcl)
                        .setNeutralButton(R.string.sf_unlink_clear_relink, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new AlertDialog.Builder(getMainActivity())
                                        .setTitle(((AlertDialog)dialogInterface).getButton(i).getText())
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setMessage(R.string.sf_unlink_clear_relink_message)
                                        .setNegativeButton(android.R.string.cancel,null)
                                        .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_unlink),false).commit();

                                                // "favorites.json","recordings.json","history.json","alerts.json"
                                                cleanup();

                                                restartApplication(getMainActivity());
                                            }
                                        }).show();
                            }
                        })
                        .show();
            } else {
                new AlertDialog.Builder(getMainActivity())
                        .setTitle(preference.getTitle())
                        .setMessage(preference.getSummary()+"\n\n"+getString(R.string.sf_unlink_message2))
                        .setNeutralButton(R.string.sf_unlink_unlink, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new AlertDialog.Builder(getMainActivity())
                                        .setTitle(((AlertDialog)dialogInterface).getButton(i).getText())
                                        .setMessage(R.string.sf_unlink_unlink_message)
                                        .setIcon(android.R.drawable.ic_dialog_info)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            }
                        }).setNegativeButton(R.string.sf_unlink_clear, clearaOcl)
                        .show();
            }
        } else if (preference.getKey().equals(getString(R.string.pref_about))) {
            new AlertDialog.Builder(getMainActivity())
                    .setTitle(preference.getTitle())
                    .setMessage(preference.getSummary()+"\n\nCopyright(C) 2015 StudioKUMA/Stark Wong. All Rights Reserved.\n\nIcons in this app created by Cris Dobbins, Julynn B., Gustav Salomonsson and Mike Arndt, retrieved from The Noun Project and licensed under CC BY 3.0.\n\nThis app is not associated with Pixel Magic in any ways.")
                    .setPositiveButton(android.R.string.ok,null)
                    .show();
        }
        return true;
    }

    private static void restartApplication(MainActivity mainActivity) {
        Context appContext=mainActivity.getApplicationContext();
        Intent intent=new Intent(appContext, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);

        mainActivity.finish();

        appContext.startActivity(intent);
    }

    @Override
    public void onIRecListResult(Class objectType, final Object[] result) {
        ProgressIndicator.removeIndicator(getActivity());
        if (objectType==null) {
            // Change device successful
            restartApplication(getMainActivity());
        } else if (objectType.equals(IRecLibrary.Device.class)) {
            String currentDevice=getPreferenceManager().getSharedPreferences().getString("device","");
            int currentIndex=-1;
            int tempIndex=0;

            if (currentDevice.length()>0) {
                for (IRecLibrary.Device device : (IRecLibrary.Device[]) result) {
                    if (device.name.equals(currentDevice)) {
                        currentIndex=tempIndex;
                        break;
                    }
                    tempIndex++;
                }
            }

            new AlertDialog.Builder(getMainActivity())
                    .setSingleChoiceItems(new ArrayAdapter<IRecLibrary.Device>(getMainActivity(),android.R.layout.select_dialog_singlechoice,(IRecLibrary.Device[])result),currentIndex,new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            IRecLibrary.Device device=(IRecLibrary.Device)result[i];
                            getMainActivity().iRecLibrary.changeDevice(device.href,SettingsFragment.this);
                        }
                    })
                    .show();
        }
    }
}
