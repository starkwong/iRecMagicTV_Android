package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by starkwong on 2/1/15.
 */
public class ChannelListFragment extends BaseListFragment implements IRecLibrary.OnIRecListResult, TextWatcher, View.OnClickListener {
    private class ChannelAdapter extends ArrayAdapter<Object> {
        // private View headerView;

        public ChannelAdapter(Context context, Object[] channels) {
            super(context, R.layout.channel_list_item, android.R.id.text1, channels);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object object=getItem(position);

            if (object instanceof IRecLibrary.Channel) {
                convertView=super.getView(position, convertView, parent);
                if (convertView instanceof TextView) {
                    LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView=layoutInflater.inflate(R.layout.channel_list_item, null);
                }
                IRecLibrary.Channel channel = (IRecLibrary.Channel) object;

                ((TextView) convertView.findViewById(android.R.id.text1)).setText(channel.chnumber + " " + channel.chname);
                ((TextView) convertView.findViewById(android.R.id.text2)).setText(channel.chlogo_alt);
            } else {
                convertView=super.getView(position, convertView, parent);
                if (!(convertView instanceof TextView)) {
                    LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = (TextView) layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
                }

                TextView textView= (TextView) convertView;
                textView.setText((String) object);
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            Object object=getItem(position);

            return (object instanceof IRecLibrary.Channel)?0:1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }
    }

    private IRecLibrary.Channel[] allChannels;
    private Object[] filteredChannels;
    private ChannelAdapter channelAdapter;
    private boolean showAllChannels;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getMainActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return View.inflate(inflater.getContext(), R.layout.channel_list_fragment, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((EditText)getView().findViewById(android.R.id.edit)).addTextChangedListener(this);
        getView().findViewById(R.id.clearTextButton).setOnClickListener(this);
        showAllChannels=false;

        if (channelAdapter!=null) {
            setListAdapter(channelAdapter);
        } else {
            getMainActivity().iRecLibrary.getChannelList(this);
        }

        ((EditText)getView().findViewById(android.R.id.edit)).clearFocus();

        // getMainActivity().setTitle(R.string.app_name);
        getMainActivity().setTitle("MER ["+getMainActivity().getSharedPreferences(IRecLibrary.class.getSimpleName(),Context.MODE_PRIVATE).getString(getString(R.string.pref_device),"-") +"]");
    }

    public static void showLoginDialog(final Activity context, final DialogInterface.OnClickListener ocl) {
        final View alertView=context.getLayoutInflater().inflate(R.layout.login_alert,null);
        DialogInterface.OnClickListener ocl2=new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (i==DialogInterface.BUTTON_POSITIVE) {
                    ((MainActivity)context).iRecLibrary.setLoginDetails(((EditText) alertView.findViewById(android.R.id.text1)).getText().toString(), ((EditText) alertView.findViewById(android.R.id.text2)).getText().toString());
                    ocl.onClick(dialogInterface, i);
                } else {
                    context.finish();
                }
            }
        };

        AlertDialog.Builder builder=new AlertDialog.Builder(context);
        builder.setTitle(R.string.clf_login_title);
        builder.setView(alertView);
        builder.setPositiveButton(R.string.clf_login_login,ocl2);
        builder.setNegativeButton(android.R.string.cancel,ocl2);
        builder.show();
    }

    @Override
    public void onIRecListResult(Class objectType, Object[] result) {
        if (objectType==IllegalAccessException.class) {
            // Bad login
            showLoginDialog(getMainActivity(), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    getMainActivity().iRecLibrary.getChannelList(ChannelListFragment.this);
                }
            });
        } if (objectType==IRecLibrary.Channel.class) {
            allChannels=(IRecLibrary.Channel[]) result;

            filterFavorites();

            getMainActivity().setTitle("MER ["+getMainActivity().getSharedPreferences(IRecLibrary.class.getSimpleName(),Context.MODE_PRIVATE).getString(getString(R.string.pref_device),"?") +"]");
        }
    }

    public static String readAsString(Context context, String file) throws IOException {
        InputStream inputStream=context.openFileInput(file);
        byte[] bytes=new byte[inputStream.available()];
        inputStream.read(bytes);
        return new String(bytes,"UTF-8");
    }

    public static HashMap<String, IRecLibrary.Channel> generateChannelMap(IRecLibrary.Channel[] allChannels) {
        HashMap<String, IRecLibrary.Channel> channelMap=new HashMap<String, IRecLibrary.Channel>(allChannels.length);

        for (IRecLibrary.Channel channel: allChannels) {
            channelMap.put(channel.channelsel, channel);
        }

        return channelMap;
    }

    private void filterFavorites() {
        JSONObject jsonObject=null;
        if (showAllChannels==false) {
            try {
                String json = ChannelListFragment.readAsString(getMainActivity(), AppBackupAgent.FILE_FAVORITES);
                jsonObject = new JSONObject(json);
            } catch (IOException e) {
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        ArrayList<Object> channels=new ArrayList<Object>();
        if (jsonObject!=null) {
            try {
                ArrayList<String> favorites = new ArrayList<String>();
                JSONArray jsonArray = jsonObject.getJSONArray("favorites");
                Map<String, IRecLibrary.Channel> channelMap=ChannelListFragment.generateChannelMap(allChannels);
                IRecLibrary.Channel channel;

                for (int c = 0; c < jsonArray.length(); c++) {
                    channel=channelMap.get(jsonArray.getString(c));
                    if (channel!=null) {
                        channels.add(channel);
                    }
                }
                channels.add(getString(R.string.clf_show_all));
                channels.add(getString(R.string.clf_edit_favorites));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (channels.size()==0) {
            if (showAllChannels==false) {
                channels.add(getString(R.string.clf_create_favorites));
            } else {
                channels.add(getString(R.string.clf_show_favorites));
                channels.add(getString(R.string.clf_edit_favorites));
            }
            channels.addAll(Arrays.asList(allChannels));
            /*
            if (showAllChannels) {
                channels.add("Show Favorites");
                channels.add("Edit Favorites");
            }*/
        }
        filteredChannels=channels.toArray();

        setListAdapter(channelAdapter=new ChannelAdapter(getMainActivity(), filteredChannels));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (hidden==false && getMainActivity().favoriteChanged) {
            getMainActivity().favoriteChanged=false;

            filterFavorites();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Object object=filteredChannels[position];
        if (object instanceof IRecLibrary.Channel) {
            IRecLibrary.Channel channel = (IRecLibrary.Channel) object;

            ProgrammeListFragment fragment = new ProgrammeListFragment();
            Bundle bundle = new Bundle();
            bundle.putString("url", channel.href);
            bundle.putString("title",channel.chnumber+" "+channel.chname);
            fragment.setArguments(bundle);
            getFragmentManager().beginTransaction().replace(R.id.container, fragment).addToBackStack("clf").commit();
        } else {
            if ((!showAllChannels && position==filteredChannels.length-2) || (showAllChannels && position==0)) {
                showAllChannels=!showAllChannels;
                filterFavorites();
            } else {
                FavoriteListFragment fragment = new FavoriteListFragment();
                fragment.allChannels = allChannels;
                getFragmentManager().beginTransaction().replace(R.id.container, fragment).addToBackStack("clf").commit();
            }
        }
    }

    // TextWatcher

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (editable.length()==0)
            //filteredChannels=allChannels;
            filterFavorites();
        else {
            ArrayList<IRecLibrary.Channel> channels=new ArrayList<IRecLibrary.Channel>();
            String criteria=editable.toString().toLowerCase();

            for (IRecLibrary.Channel channel : allChannels) {
                if (channel.chnumber.startsWith(criteria) || channel.chname.toLowerCase().startsWith(criteria) || channel.chlogo_alt.toLowerCase().startsWith(criteria)) {
                    channels.add(channel);
                }
            }

            filteredChannels=channels.toArray(new IRecLibrary.Channel[channels.size()]);
        }
        setListAdapter(channelAdapter=new ChannelAdapter(getMainActivity(), filteredChannels));
    }

    @Override
    public void onClick(View view) {
        if (view.getId()==R.id.clearTextButton) {
            EditText editText=((EditText)getView().findViewById(android.R.id.edit));
            editText.setText("");
        }
    }
}
