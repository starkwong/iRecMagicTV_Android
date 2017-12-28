package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.util.HashMap;

/**
 * Created by starkwong on 15/06/06.
 */
public class ProgressIndicator {
    private final static String LOGTAG=ProgressIndicator.class.getSimpleName();
    private static HashMap<Activity, View> mapping=new HashMap<Activity, View>();

    public static void showIndicator(Activity activity) {
        if (mapping.containsKey(activity)) {
            Log.e(LOGTAG,"showIndicator: Already added!");
        } else {
            ProgressBar progressBar=new ProgressBar(activity);
            FrameLayout frameLayout= (FrameLayout) activity.findViewById(android.R.id.content);
            FrameLayout.LayoutParams layoutParams=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

            frameLayout.addView(progressBar,layoutParams);
            mapping.put(activity, progressBar);
        }
    }

    public static void removeIndicator(Activity activity) {
        if (mapping.containsKey(activity)) {
            FrameLayout frameLayout= (FrameLayout) activity.findViewById(android.R.id.content);
            frameLayout.removeView(mapping.get(activity));
            mapping.remove(activity);
        } else {
            Log.e(LOGTAG,"showIndicator: Not found!");
        }
    }
}
