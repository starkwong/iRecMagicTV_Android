package com.studiokuma.irecmagictv;

import android.app.AlertDialog;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Sheep on 2015/01/03.
 */
public class FavoriteListFragment extends BaseListFragment implements CompoundButton.OnCheckedChangeListener, DialogInterface.OnClickListener {
    private class FavoriteListAdapter extends ArrayAdapter<Object> implements View.OnClickListener {
        private View favoriteView;
        private View allView;

        public FavoriteListAdapter(Context context, List<Object> objects) {
            super(context, R.layout.channel_list_item, android.R.id.text1, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object object=getItem(position);

            Log.i(this.getClass().getSimpleName(), "getView("+position+") object="+object);

            if (object instanceof Integer) {
                int n=((Integer)object).intValue();

                convertView=(n==0?favoriteView:allView);

                if (convertView==null) {
                    TextView textView=new TextView(getContext());
                    textView.setId(android.R.id.text1);
                    textView.setBackgroundColor(0xff999999);
                    textView.setTextColor(0xffffffff);
                    convertView=textView;
                    if (n==0) favoriteView=textView; else allView=textView;
                }

                TextView textView= (TextView) convertView;

                Log.i(this.getClass().getSimpleName(), "convertView="+convertView+" text="+textView.getText());

                textView.setText(getString(n==0?R.string.flf_favorite_channels:R.string.flf_all_channels));
                textView.setTag(textView.getText());
            } else {
                convertView=super.getView(position, convertView, parent);
                if (convertView instanceof TextView) {
                    ((TextView)convertView).setText((String) convertView.getTag());
                    LayoutInflater layoutInflater= (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView=layoutInflater.inflate(R.layout.channel_list_item, null);
                }

                IRecLibrary.Channel channel = (IRecLibrary.Channel) object;
                View favButton;
                (favButton=convertView.findViewById(R.id.favButton)).setVisibility(View.VISIBLE);
                favButton.setTag(object);
                favButton.setSelected(favorites.contains(channel.channelsel));
                favButton.setOnClickListener(this);


                ((TextView) convertView.findViewById(android.R.id.text1)).setText(channel.chnumber + " " + channel.chname);
                TextView textView;
                (textView = (TextView) convertView.findViewById(android.R.id.text2)).setText(channel.chlogo_alt);
            }
            return convertView;
        }

        @Override
        public void onClick(View view) {
            Button button= (Button) view;
            button.setSelected(!button.isSelected());

            favoriteChanged=true;

            if (button.isSelected()) {
                favorites.add(((IRecLibrary.Channel)button.getTag()).channelsel);
            } else {
                favorites.remove(((IRecLibrary.Channel) button.getTag()).channelsel);
            }
            updateItems();
        }
    }

    public IRecLibrary.Channel[] allChannels;
    public ArrayList<Object> items;
    public HashMap<String, IRecLibrary.Channel> channelMap;
    public ArrayList<String> favorites;
    public View rootView;
    private FavoriteListAdapter favoriteListAdapter;
    private boolean favoriteChanged=false;

    /*
    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }*/

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        customBack=true;
        return rootView=getMainActivity().getLayoutInflater().inflate(R.layout.favorite_list_fragment, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        for (int id: new int[]{R.id.dttCheckBox, R.id.nowTvCheckBox, R.id.iCableCheckBox}) {
            ((CheckBox)rootView.findViewById(id)).setOnCheckedChangeListener(this);
        }

        if (favoriteListAdapter==null) {
            channelMap = ChannelListFragment.generateChannelMap(allChannels);
            readFavorites();
            updateItems();
        } else {
            setListAdapter(favoriteListAdapter);
        }
        getMainActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void updateItems() {
        if (items==null)
            items=new ArrayList<Object>();
        else
            items.clear();

        items.add(new Integer(0));

        Collections.sort(favorites, new Comparator<String>() {
            @Override
            public int compare(String s, String s2) {
                return Integer.valueOf(s).compareTo(Integer.valueOf(s2));
            }
        });

        for (String favorite: favorites) {
            items.add(channelMap.get(favorite));
        }
        items.add(new Integer(1));

        boolean dttChecked=((CheckBox)rootView.findViewById(R.id.dttCheckBox)).isChecked();
        boolean nowTvChecked=((CheckBox)rootView.findViewById(R.id.nowTvCheckBox)).isChecked();
        boolean iCableChecked=((CheckBox)rootView.findViewById(R.id.iCableCheckBox)).isChecked();

        for (IRecLibrary.Channel channel: allChannels) {
            if (!favorites.contains(channel.channelsel)) {
                if (channel.channelsel.length() < 4) {
                    // DTT
                    if (dttChecked) items.add(channel);
                } else if (channel.channelsel.startsWith("4") && nowTvChecked) {
                    items.add(channel);
                } else if (channel.channelsel.startsWith("8") && iCableChecked) {
                    items.add(channel);
                }
            }
        }

        if (favoriteListAdapter==null) {
            setListAdapter(favoriteListAdapter=new FavoriteListAdapter(getMainActivity(),items));
        } else {
            favoriteListAdapter.notifyDataSetChanged();
        }
    }

    private void readFavorites() {
        favorites=new ArrayList<String>();
        try {
            String json=ChannelListFragment.readAsString(getMainActivity(),AppBackupAgent.FILE_FAVORITES);
            JSONObject jsonObject=new JSONObject(json);

            JSONArray jsonArray=jsonObject.getJSONArray("favorites");
            for (int c=0; c<jsonArray.length(); c++) {
                favorites.add(jsonArray.getString(c));
            }
        } catch (IOException e) {
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveFavorites() {
        JSONObject jsonObject=new JSONObject();
        JSONArray jsonArray=new JSONArray();

        for (String favorite: favorites) {
            jsonArray.put(favorite);
        }

        try {
            jsonObject.put("favorites",jsonArray);

            FileOutputStream fos=getMainActivity().openFileOutput(AppBackupAgent.FILE_FAVORITES,Context.MODE_PRIVATE);
            fos.write(jsonObject.toString().getBytes("UTF-8"));
            fos.close();

            getMainActivity().favoriteChanged=true;
            getMainActivity().triggerSync();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // OnCheckedChangeListener
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        updateItems();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==android.R.id.home) {
            onBackPressed();
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        if (i==DialogInterface.BUTTON_NEGATIVE) {
            /*
            getMainActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
            getMainActivity().back();*/
        } else if (i==DialogInterface.BUTTON_POSITIVE) {
            saveFavorites();
        }
        getMainActivity().getFragmentManager().popBackStack();
    }

    public void onBackPressed() {
        if (favoriteChanged) {
            new AlertDialog.Builder(getMainActivity())
                    .setTitle(R.string.flf_favorite_channels)
                    .setMessage(R.string.flf_save_message)
                    .setPositiveButton(R.string.yes, this)
                    .setNegativeButton(R.string.no, this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            //getMainActivity().back();
            getMainActivity().getFragmentManager().popBackStack();

        }
    }
}
