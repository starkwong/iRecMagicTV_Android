package com.studiokuma.irecmagictv;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.EventLogTags;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Created by starkwong on 2/1/15.
 */
public class ProgrammeListFragment extends BaseListFragment implements IRecLibrary.OnIRecListResult, TextWatcher, View.OnClickListener {
    private class ProgrammeAdapter extends ArrayAdapter<IRecLibrary.Programme> implements View.OnClickListener {

        public ProgrammeAdapter(Context context, IRecLibrary.Programme[] programmes) {
            super(context, R.layout.channel_list_item, android.R.id.text1, programmes);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView=super.getView(position, convertView, parent);

            IRecLibrary.Programme programme=getItem(position);

            ((TextView)convertView.findViewById(android.R.id.text1)).setText(programme.progname);
            ((TextView)convertView.findViewById(android.R.id.text2)).setText(programme.progday + " " + programme.progtime);

            View button;

            for (int id: new int[] {R.id.recButton, R.id.altButton}) {
                (button = convertView.findViewById(id)).setVisibility(View.VISIBLE);
                button.setTag(position);
                button.setOnClickListener(this);
            }

            ((Button)convertView.findViewById(R.id.recButton)).setTextColor(getResources().getColor(recordings.contains(programme.href)?R.color.rec_text_selected:R.color.rec_text_normal));
            ((Button)convertView.findViewById(R.id.altButton)).setTextColor(getResources().getColor(alerts.contains(programme.href)?R.color.alt_text_selected:R.color.alt_text_normal));

            return convertView;
        }

        @Override
        public void onClick(View view) {
            int position=((Integer)view.getTag()).intValue();
            final IRecLibrary.Programme programme=getItem(position);

            if (view.getId()==R.id.recButton) {
                String payload="[]";
                try {
                    payload=ChannelListFragment.readAsString(getMainActivity(),AppBackupAgent.FILE_RECORDINGS);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    JSONArray jsonArray=new JSONArray(payload);
                    JSONObject jsonObject;
                    for (int c=0; c<jsonArray.length(); c++) {
                        IRecLibrary.Programme programme1=new IRecLibrary.Programme(jsonObject=jsonArray.getJSONObject(c));
                        if (programme1.duration>0 && (
                            programme.timestamp>=programme1.timestamp && programme.timestamp<(programme1.timestamp+programme1.duration) ||
                            (programme.timestamp+programme.duration)<programme1.timestamp && (programme.timestamp+programme.duration)>=(programme1.timestamp+programme1.duration))) {
                            Toast.makeText(getMainActivity(),getString(R.string.plf_overlap,jsonObject.optString("channel",""),programme1.progname),Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }


                if (recordings.contains(programme.href)) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.plf_exist_title)
                            .setMessage(R.string.plf_exist_message)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    currentProgramme = programme;
                                    forRecording = true;
                                    ProgressIndicator.showIndicator(getActivity());
                                    getMainActivity().iRecLibrary.prepareForRecording(programme.href, ProgrammeListFragment.this);
                                }
                            }).setNegativeButton(R.string.no, null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                } else {
                    currentProgramme = programme;
                    forRecording = true;
                    //getMainActivity().iRecLibrary.prepareForRecording(programme.href, ProgrammeListFragment.this);
                    ProgressIndicator.showIndicator(getActivity());
                    getMainActivity().iRecLibrary.performGenericURL(programme.href, ProgrammeListFragment.this);
                }
            } else {
                String channelUrl=ProgrammeListFragment.this.getArguments().getString("url");
                final String channel=channelUrl.substring(channelUrl.indexOf('=')+1);
                final String title=ProgrammeListFragment.this.getArguments().getString("title");

                if (alerts.contains(programme.href)) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.plf_exist2_title)
                            .setMessage(R.string.plf_exist2_message)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    scheduleAlert(getContext(), channel, title, programme);
                                }
                            }).setNegativeButton(android.R.string.no, null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                } else {
                    scheduleAlert(getContext(), channel, title, programme);

                    String payload="[]";
                    try {
                        payload=ChannelListFragment.readAsString(getMainActivity(),AppBackupAgent.FILE_ALERTS);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        JSONArray oriJsonArray=new JSONArray(payload);

                        JSONArray jsonArray=new JSONArray();
                        JSONObject jsonObject=programme.toJSONObject();
                        jsonObject.put("channel",channel);
                        jsonObject.put("channeltitle",title);
                        jsonArray.put(jsonObject);

                        for (int c=0; c<oriJsonArray.length(); c++) {
                            jsonObject=oriJsonArray.getJSONObject(c);
                            jsonArray.put(jsonObject);
                        }

                        try {
                            FileOutputStream fileOutputStream=getMainActivity().openFileOutput(AppBackupAgent.FILE_ALERTS,Context.MODE_PRIVATE);
                            fileOutputStream.write(jsonArray.toString().getBytes("UTF-8"));
                            fileOutputStream.close();

                            getMainActivity().triggerSync();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    new AlertDialog.Builder(getMainActivity())
                            .setTitle(R.string.plf_exist2_title)
                            .setMessage(R.string.plf_success2_message)
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton(android.R.string.ok,null)
                            .show();

                    readAlerts();
                    programmeAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private IRecLibrary.Programme[] allProgrammes;
    private IRecLibrary.Programme[] filteredProgrammes;
    private ProgrammeAdapter programmeAdapter;
    private IRecLibrary.Programme currentProgramme;
    private ArrayList<String> recordings;
    private ArrayList<String> alerts;
    private Button currentDoW;
    private boolean forRecording;
    private boolean initialDoW;
    private String lastHref;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return View.inflate(inflater.getContext(), R.layout.programme_list_fragment, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((EditText)getView().findViewById(android.R.id.edit)).addTextChangedListener(this);
        getView().findViewById(R.id.clearTextButton).setOnClickListener(this);

        setDoW();

        if (programmeAdapter==null) {
            ProgressIndicator.showIndicator(getActivity());
            getMainActivity().iRecLibrary.getProgrammeList(getArguments().getString("url"), this);
        } else {
            setListAdapter(programmeAdapter);
        }

        getMainActivity().setTitle(getArguments().getString("title"));
        getMainActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onIRecListResult(Class objectType, Object[] result) {
        ProgressIndicator.removeIndicator(getActivity());
        if (objectType==IRecLibrary.Programme.class) {
            allProgrammes = (IRecLibrary.Programme[]) result;

            ViewGroup viewGroup = (ViewGroup) getView().findViewById(R.id.dowLayout);

            // setListAdapter(new ProgrammeAdapter(getActivity(), allProgrammes=filteredProgrammes=(IRecLibrary.Programme[]) result));
            Calendar calendar = GregorianCalendar.getInstance();
            readRecordings();
            readAlerts();
            int dow = calendar.get(Calendar.DAY_OF_WEEK); // 1-7
            Button b = (Button) viewGroup.findViewWithTag(Integer.toString(dow));
            initialDoW = true;
            b.performClick();
        } else if (objectType== IRecLibrary.Href.class) {
            Log.i(this.getClass().getSimpleName(), "onIRecListResult: result=" + result);
            final String[] info=(String[])((Object[])result)[0];
            final IRecLibrary.Href[] hrefs= (IRecLibrary.Href[])((Object[])result)[1];

            DisplayMetrics displayMetrics = getActivity().getResources().getDisplayMetrics();
            final LinearLayout linearLayout = new LinearLayout(getActivity());
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding((int) (10.f * displayMetrics.density), (int) (10.f * displayMetrics.density), (int) (10.f * displayMetrics.density), 0);
            ScrollView scrollView=new ScrollView(getActivity());
            scrollView.setPadding(0,0,0,(int)(10.f*displayMetrics.density));
            //linearLayout.addView(scrollView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.f));

            TextView textView=new TextView(getActivity());
            textView.setPadding(0,0,0,(int)(10.f*displayMetrics.density));
            scrollView.addView(textView);
            //linearLayout.addView(textView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            View.OnClickListener onClickListener=new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LinearLayout linearLayout1=(LinearLayout)view.getParent();
                    AlertDialog alertDialog=(AlertDialog)linearLayout.getTag();
                    alertDialog.dismiss();
                    IRecLibrary.Href href=(IRecLibrary.Href) view.getTag();
                    lastHref=href.href;
                    ProgressIndicator.showIndicator(getActivity());
                    getMainActivity().iRecLibrary.performGenericURL(href.href,ProgrammeListFragment.this);
                }
            };

            LayoutInflater layoutInflater=getActivity().getLayoutInflater();

            for (IRecLibrary.Href href: hrefs) {
                /*
                Button button=new Button(getActivity());
                button.setth*/
                CheckedTextView checkedTextView= (CheckedTextView) layoutInflater.inflate(android.R.layout.simple_list_item_single_choice, null);
                checkedTextView.setText(href.title);
                checkedTextView.setTag(href);
                checkedTextView.setOnClickListener(onClickListener);
                linearLayout.addView(checkedTextView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            String durationText="N/A";
            if (currentProgramme.duration!=0) {
                SimpleDateFormat simpleDateFormat=new SimpleDateFormat("HH:mm");
                Calendar calendar=GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTimeInMillis(currentProgramme.duration);
                simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                durationText=simpleDateFormat.format(calendar.getTime());
            }

            textView.setText(getString(R.string.plf_info_normal,info[1],info[0], durationText, info[2],info[3]));

            linearLayout.setTag(new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.plf_info_title)
                            //.setMessage("Message")
                    /*.setAdapter(new ArrayAdapter<IRecLibrary.Href>(getActivity(), android.R.layout.select_dialog_singlechoice, android.R.id.text1, (IRecLibrary.Href[]) result), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            IRecLibrary.Href href=hrefs[i];

                            Log.i(this.getClass().getSimpleName(),"onIRecListResult: title="+href.title+" href="+href.href);
                            getMainActivity().iRecLibrary.performGenericURL(hrefs[i].href,ProgrammeListFragment.this);
                        }
                    })*/
                    .setView(linearLayout)
                    /*.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).setNegativeButton("Cancel",null)*/
                    .show());
        } else if (objectType==null) {
            // Successful
            Log.i(this.getClass().getSimpleName(), "onIRecListResult: successful");

            // Success
            String payload="[]";
            try {
                payload=ChannelListFragment.readAsString(getMainActivity(),AppBackupAgent.FILE_RECORDINGS);
            } catch (IOException e) {
                e.printStackTrace();
            }

            String channelUrl=currentProgramme.href;
            String channel=channelUrl.substring(channelUrl.indexOf('=')+1);
            String title=ProgrammeListFragment.this.getArguments().getString("title");

            try {
                JSONArray oriJsonArray=new JSONArray(payload);

                JSONArray jsonArray=new JSONArray();
                JSONObject jsonObject=currentProgramme.toJSONObject();

                jsonObject.put("channel",channel);
                jsonObject.put("channeltitle",title);
                jsonObject.put("duration",currentProgramme.duration);
                jsonObject.put("single",lastHref.contains("presingleRec.php"));
                jsonArray.put(jsonObject);

                for (int c=0; c<oriJsonArray.length(); c++) {
                    jsonArray.put(oriJsonArray.getJSONObject(c));
                }

                try {
                    FileOutputStream fileOutputStream=getMainActivity().openFileOutput(AppBackupAgent.FILE_RECORDINGS,Context.MODE_PRIVATE);
                    fileOutputStream.write(jsonArray.toString().getBytes("UTF-8"));
                    fileOutputStream.close();

                    getMainActivity().triggerSync();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            new AlertDialog.Builder(getMainActivity())
                    .setTitle(R.string.plf_info_title)
                    .setMessage(R.string.plf_success_mesage)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton(android.R.string.ok,null)
                    .show();

            readRecordings();
            programmeAdapter.notifyDataSetChanged();

        } else if (objectType==String.class) {
            // Recording info
            if (result==null || result.length==0) {
                new AlertDialog.Builder(getMainActivity())
                        .setTitle(R.string.plf_info_title)
                        .setMessage(R.string.plf_info_error)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                DialogInterface.OnClickListener ocl=new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {



                        if (i==DialogInterface.BUTTON_POSITIVE) {
                            // Single
                            ProgressIndicator.showIndicator(getActivity());
                            getMainActivity().iRecLibrary.performSingleRecording(new IRecLibrary.OnIRecListResult() {
                                @Override
                                public void onIRecListResult(Class objectType, Object[] result) {
                                    if (objectType==null) {
                                        // Success
                                        String payload="[]";
                                        try {
                                            payload=ChannelListFragment.readAsString(getMainActivity(),AppBackupAgent.FILE_RECORDINGS);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                        String channelUrl=currentProgramme.href;
                                        String channel=channelUrl.substring(channelUrl.indexOf('=')+1);
                                        String title=ProgrammeListFragment.this.getArguments().getString("title");

                                        try {
                                            JSONArray oriJsonArray=new JSONArray(payload);

                                            JSONArray jsonArray=new JSONArray();
                                            JSONObject jsonObject=currentProgramme.toJSONObject();

                                            jsonObject.put("channel",channel);
                                            jsonObject.put("channeltitle",title);
                                            jsonObject.put("duration",currentProgramme.duration);
                                            jsonArray.put(jsonObject);

                                            for (int c=0; c<oriJsonArray.length(); c++) {
                                                jsonArray.put(oriJsonArray.getJSONObject(c));
                                            }

                                            try {
                                                FileOutputStream fileOutputStream=getMainActivity().openFileOutput(AppBackupAgent.FILE_RECORDINGS,Context.MODE_PRIVATE);
                                                fileOutputStream.write(jsonArray.toString().getBytes("UTF-8"));
                                                fileOutputStream.close();

                                                getMainActivity().triggerSync();
                                            } catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            } catch (UnsupportedEncodingException e) {
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                        new AlertDialog.Builder(getMainActivity())
                                                .setTitle(R.string.plf_info_title)
                                                .setMessage(R.string.plf_success_mesage)
                                                .setIcon(android.R.drawable.ic_dialog_info)
                                                .setPositiveButton(android.R.string.ok,null)
                                                .show();

                                        readRecordings();
                                        programmeAdapter.notifyDataSetChanged();
                                    } else {
                                        // Fail
                                        new AlertDialog.Builder(getMainActivity())
                                                .setTitle(R.string.plf_info_title)
                                                .setMessage(getString(R.string.plf_record_error)+"\n\n"+result[0])
                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                .setPositiveButton(android.R.string.ok,null)
                                                .show();
                                    }
                                }
                            });
                        } else if (i==DialogInterface.BUTTON_NEGATIVE) {
                            // Repeat
                        }
                    }
                };

                AlertDialog.Builder builder=new AlertDialog.Builder(getMainActivity())
                        .setTitle(R.string.plf_info_title);

                String durationText="N/A";
                if (currentProgramme.duration!=0) {
                    SimpleDateFormat simpleDateFormat=new SimpleDateFormat("HH:mm");
                    Calendar calendar=GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
                    calendar.setTimeInMillis(currentProgramme.duration);
                    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    durationText=simpleDateFormat.format(calendar.getTime());
                }

                if (forRecording) {
                    builder.setMessage(getString(R.string.plf_info_prerecord,result[0],result[1], durationText, result[2]))
                            .setPositiveButton(R.string.plf_info_single,ocl)
                            .setNeutralButton(R.string.plf_info_repeat,ocl);
                } else {
                    builder.setMessage(getString(R.string.plf_info_normal,result[0],result[1],durationText, result[2],result[3]))
                            .setPositiveButton(android.R.string.ok,null);
                }
                builder.show();
            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        IRecLibrary.Programme programme=programmeAdapter.getItem(position);

        currentProgramme=programme;
        forRecording=false;
        ProgressIndicator.showIndicator(getActivity());
        getMainActivity().iRecLibrary.prepareForRecording(programme.href, ProgrammeListFragment.this);
    }

    void setDoW() {
        View rootView=getView();
        Button button;
        View.OnClickListener ocl=new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ViewGroup viewGroup=(ViewGroup)view.getParent();
                Button button;

                currentDoW=(Button) view;

                for (int c=0; c<viewGroup.getChildCount(); c++) {
                    button=(Button)viewGroup.getChildAt(c);
                    button.setSelected(button==view);
                }

                ArrayList<IRecLibrary.Programme> programmes=new ArrayList<IRecLibrary.Programme>();
                String criteria=new String[]{null,"Sun","Mon","Tue","Wed","Thu","Fri","Sat"}[Integer.parseInt((String)view.getTag())];

                int initialSelect=-1;
                int index=0;
                /*
                Calendar calendar=GregorianCalendar.getInstance();
                calendar.setTimeInMillis(System.currentTimeMillis());
                calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
                */
                long currentTime=System.currentTimeMillis();

                for (IRecLibrary.Programme programme: allProgrammes) {
                    if (programme.name.equals(criteria)) {
                        programmes.add(programme);
                        if (programme.timestamp<=currentTime) initialSelect=index;
                        index++;
                    }
                }

                filteredProgrammes=programmes.toArray(new IRecLibrary.Programme[programmes.size()]);
                setListAdapter(programmeAdapter=new ProgrammeAdapter(getMainActivity(), filteredProgrammes));

                if (initialDoW) {
                    initialDoW=false;
                    getListView().smoothScrollToPosition(initialSelect);
                    getListView().setSelection(initialSelect);
                }
            }
        };

        int ids[]={R.id.sunButton,R.id.monButton,R.id.tueButton,R.id.wedButton,R.id.thuButton,R.id.friButton,R.id.satButton};
        String labels[]=getResources().getStringArray(R.array.weekdays);

        for (int c=0; c<ids.length; c++) {
            (button=(Button)rootView.findViewById(ids[c])).setText(labels[c]);
            button.setOnClickListener(ocl);
        }


        Calendar calendar= GregorianCalendar.getInstance();
        int dow=calendar.get(Calendar.DAY_OF_WEEK); // 1-7

        for (int c=dow-1; c<dow+6; c++) {
            button= (Button) rootView.findViewWithTag(Integer.toString((c%7)+1));
            button.setText(button.getText()+"\n"+calendar.get(Calendar.DAY_OF_MONTH));
            if (currentDoW!=null && currentDoW.getTag().equals(button.getTag())) {
                button.setSelected(true);
            }
            calendar.add(Calendar.DAY_OF_MONTH,1);
        }

    }

    void readToList(String filename, ArrayList<String> list) {
        String payload="[]";
        try {
            payload=ChannelListFragment.readAsString(getMainActivity(),filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            JSONArray jsonArray = new JSONArray(payload);
            for (int c=0; c<jsonArray.length(); c++) {
                list.add(jsonArray.getJSONObject(c).getString("href"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void readRecordings() {
        recordings=new ArrayList<String>();

        readToList(AppBackupAgent.FILE_RECORDINGS,recordings);
    }

    void readAlerts() {
        alerts=new ArrayList<String>();

        readToList(AppBackupAgent.FILE_ALERTS,alerts);
    }

    public static void scheduleAlert(Context context, String channel, String title, IRecLibrary.Programme programme) {
        // String channelUrl=ProgrammeListFragment.this.getArguments().getString("url");
        // Intent intent=new Intent(context, AlarmEventActivity.class);
        Intent intent=new Intent(context, EventReceiver.class);
        intent.setAction("alarm");
        intent.putExtra("programme",programme.toJSONObject().toString());
        intent.putExtra("channel",channel/*channelUrl.substring(channelUrl.indexOf('=')+1)*/);
        intent.putExtra("title",title/*ProgrammeListFragment.this.getArguments().getString("title")*/);
         PendingIntent pendingIntent=PendingIntent.getBroadcast(context, (int)programme.timestamp, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        //PendingIntent pendingIntent=PendingIntent.getActivity(context, (int)programme.timestamp, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager= (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        // alarmManager.cancel(pendingIntent);
        alarmManager.set(AlarmManager.RTC_WAKEUP,programme.timestamp/*System.currentTimeMillis()+30000*/,pendingIntent);
    }

    // TextWatcher

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        ViewGroup dowLayout= (ViewGroup) getView().findViewById(R.id.dowLayout);
        boolean enable=editable.length()==0;

        for (int c=0; c<dowLayout.getChildCount(); c++) {
            dowLayout.getChildAt(c).setEnabled(enable);
        }

        if (editable.length()==0) {
            filteredProgrammes = allProgrammes;

            Calendar calendar= GregorianCalendar.getInstance();
            int dow=calendar.get(Calendar.DAY_OF_WEEK); // 1-7
            Button b=(Button)getView().findViewWithTag(Integer.toString(dow));
            initialDoW=true;
            b.performClick();
        } else {
            ArrayList<IRecLibrary.Programme> programmes=new ArrayList<IRecLibrary.Programme>();
            String criteria=editable.toString().toLowerCase();

            if (currentDoW!=null) {
                currentDoW.setSelected(false);
                currentDoW=null;
            }

            for (IRecLibrary.Programme programme : allProgrammes) {
                if (programme.progname.toLowerCase().contains(criteria) || programme.progday.toLowerCase().startsWith(criteria) || programme.progtime.toLowerCase().startsWith(criteria)) {
                    programmes.add(programme);
                }
            }

            filteredProgrammes=programmes.toArray(new IRecLibrary.Programme[programmes.size()]);
        }
        setListAdapter(programmeAdapter=new ProgrammeAdapter(getMainActivity(), filteredProgrammes));
    }

    @Override
    public void onClick(View view) {
        if (view.getId()==R.id.clearTextButton) {
            EditText editText=((EditText)getView().findViewById(android.R.id.edit));
            editText.setText("");
        }
    }
}
