package com.studiokuma.irecmagictv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Created by Sheep on 2015/01/07.
 */
public class AlertListFragment extends BaseListFragment {
    private class AlertAdapter extends BaseAdapter {
        private Context context;
        private JSONArray alerts;

        public AlertAdapter(Context context, JSONArray alerts) {
            super();
            this.context=context;
            this.alerts=alerts;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView==null) {
                convertView=((LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.channel_list_item, null);
            }

            try {
                JSONObject jsonObject=alerts.getJSONObject(position);
                IRecLibrary.Programme programme=new IRecLibrary.Programme(jsonObject);

                ((TextView) convertView.findViewById(android.R.id.text1)).setText(programme.progname);
                ((TextView) convertView.findViewById(android.R.id.text2)).setText(jsonObject.getString("channeltitle") + " " + programme.progday + " " + programme.progtime);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return convertView;
        }

        @Override
        public int getCount() {
            return alerts.length();
        }

        @Override
        public Object getItem(int i) {
            try {
                return alerts.get(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }
    }

    private final String LOGTAG=AlertListFragment.class.getSimpleName();

    JSONArray alerts;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        readRecordings();

        getMainActivity().setTitle(R.string.plf_exist2_title);

        registerForContextMenu(getListView());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, final ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0,1,0,"Remove").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                JSONObject item=null;
                try {
                    item=alerts.getJSONObject(((AdapterView.AdapterContextMenuInfo) menuInfo).position);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (item==null) return false;

                alerts.remove(((AdapterView.AdapterContextMenuInfo) menuInfo).position);

                try {
                    FileOutputStream fileOutputStream = getMainActivity().openFileOutput(AppBackupAgent.FILE_ALERTS, Context.MODE_PRIVATE);
                    fileOutputStream.write(alerts.toString().getBytes("UTF-8"));
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                getMainActivity().triggerSync();

                try {
                    Intent intent = new Intent(getMainActivity(), EventReceiver.class);
                    intent.setAction("alarm");
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(getMainActivity(), (int) item.getLong("timestamp"), intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_NO_CREATE);
                    //PendingIntent pendingIntent=PendingIntent.getActivity(context, (int)programme.timestamp, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    AlarmManager alarmManager = (AlarmManager) getMainActivity().getSystemService(Context.ALARM_SERVICE);
                    alarmManager.cancel(pendingIntent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                readRecordings();

                return true;
            }
        });
    }

    public void readRecordings() {
        alerts=new JSONArray();

        long timestamp=System.currentTimeMillis();

        String payload = "[]";
        try {
            payload = ChannelListFragment.readAsString(getMainActivity(), AppBackupAgent.FILE_ALERTS);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            JSONArray jsonArray = new JSONArray(payload);
            for (int c = 0; c < jsonArray.length(); c++) {
                JSONObject jsonObject=jsonArray.getJSONObject(c);
                IRecLibrary.Programme programme=new IRecLibrary.Programme(jsonObject);
                if (programme.timestamp>timestamp) {
                    alerts.put(jsonObject);
                }
            }

            if (alerts.length()!=jsonArray.length()) {
                try {
                    FileOutputStream fileOutputStream=getMainActivity().openFileOutput(AppBackupAgent.FILE_ALERTS,Context.MODE_PRIVATE);
                    fileOutputStream.write(alerts.toString().getBytes("UTF-8"));
                    fileOutputStream.close();

                    getMainActivity().triggerSync();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        setListAdapter(new AlertAdapter(getMainActivity(), alerts));
    }
}
