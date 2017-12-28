package com.studiokuma.irecmagictv;

import android.app.ListFragment;
import android.app.backup.BackupManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Sheep on 2015/01/07.
 */
public class RecordingListFragment extends BaseListFragment {
    private class RecordingAdapter extends ArrayAdapter<IRecLibrary.Programme> {
        public RecordingAdapter(Context context, List<IRecLibrary.Programme> recordings) {
            super(context, R.layout.channel_list_item, android.R.id.text1, recordings);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView=super.getView(position, convertView, parent);

            IRecLibrary.Programme programme=getItem(position);

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(programme.progname);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(programme.progday+" "+programme.progtime+" "+getString(programme.multi?R.string.plf_multiple:R.string.plf_single));

            ((TextView) convertView.findViewById(android.R.id.text1)).setTextColor(getResources().getColor(position<recordings.size()?android.R.color.black:android.R.color.darker_gray));

            return convertView;
        }
    }

    private final String LOGTAG=RecordingListFragment.class.getSimpleName();

    ArrayList<IRecLibrary.Programme> recordings;
    ArrayList<IRecLibrary.Programme> histories;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.recording_list_fragment,null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getMainActivity().setTitle(R.string.plf_info_title);

        readRecordings();
        registerForContextMenu(getListView());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, final ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0,1,0,"Remove").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                IRecLibrary.Programme programme= (IRecLibrary.Programme) getListAdapter().getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position);
                recordings.remove(programme);
                histories.remove(programme);

                int c=0;
                for (List<IRecLibrary.Programme> list: new List[]{recordings,histories}) {
                    JSONArray array=new JSONArray();

                    for (IRecLibrary.Programme recording: list) {
                        array.put(recording.toJSONObject());
                    }

                    try {
                        FileOutputStream fileOutputStream=getMainActivity().openFileOutput(c == 0 ? AppBackupAgent.FILE_RECORDINGS : AppBackupAgent.FILE_HISTORY, Context.MODE_PRIVATE);
                        fileOutputStream.write(array.toString().getBytes("UTF-8"));
                        fileOutputStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    c++;
                }

                getMainActivity().triggerSync();

                readRecordings();

                return true;
            }
        });
    }

    void readRecordings() {
        recordings=new ArrayList<IRecLibrary.Programme>();
        histories=new ArrayList<IRecLibrary.Programme>();

        boolean moved=false;
        boolean flushed=false;

        long timestamp=System.currentTimeMillis();

        for(int c2=0; c2<2; c2++) {
            String payload = "[]";
            try {
                payload = ChannelListFragment.readAsString(getMainActivity(), c2==0?AppBackupAgent.FILE_RECORDINGS:AppBackupAgent.FILE_HISTORY);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                JSONArray jsonArray = new JSONArray(payload);
                for (int c = 0; c < jsonArray.length(); c++) {
                    IRecLibrary.Programme programme=new IRecLibrary.Programme(jsonArray.getJSONObject(c));
                    if (c2==0) {
                        if (programme.multi==false && programme.timestamp<timestamp) {
                            histories.add(programme);
                            flushed=moved=true;
                        } else {
                            recordings.add(programme);
                        }
                    } else {
                        if (timestamp-programme.timestamp<1000*60*60*24*14) {
                            // Less than 14 days
                            histories.add(new IRecLibrary.Programme(jsonArray.getJSONObject(c)));
                        } else {
                            Log.i(LOGTAG,"readRecordings(): Item expired: "+programme.progname+" ("+programme.timestamp+")");
                            flushed=true;
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        Log.i(RecordingListFragment.class.getSimpleName(),"readRecordings(): moved="+moved+" flushed="+flushed);

        if (moved) {
            // recordings changed
            int c=0;
            for (List<IRecLibrary.Programme> list: new List[]{recordings,histories}) {
                JSONArray array=new JSONArray();

                for (IRecLibrary.Programme recording: list) {
                    array.put(recording.toJSONObject());
                }

                try {
                    FileOutputStream fileOutputStream=getMainActivity().openFileOutput(c == 0 ? AppBackupAgent.FILE_RECORDINGS : AppBackupAgent.FILE_HISTORY, Context.MODE_PRIVATE);
                    fileOutputStream.write(array.toString().getBytes("UTF-8"));
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                c++;
                if (!flushed) break;
            }

            getMainActivity().triggerSync();
        }

        ArrayList<IRecLibrary.Programme> fullList=new ArrayList<IRecLibrary.Programme>(recordings.size()+histories.size());
        fullList.addAll(recordings);
        fullList.addAll(histories);
        Collections.sort(fullList, Collections.reverseOrder());

        setListAdapter(new RecordingAdapter(getMainActivity(), fullList));

    }
}
