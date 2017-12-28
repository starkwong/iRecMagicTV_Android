package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import java.util.Locale;

/**
 * Created by starkwong on 15/05/31.
 */
public class Application extends android.app.Application implements android.app.Application.ActivityLifecycleCallbacks {
    // public Locale locale;
    public Locale defaultLocale;

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(this);
        defaultLocale=Locale.getDefault();

        updateLocale();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        updateLocale();
    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    public void updateLocale() {
        SharedPreferences sharedPreferences=getSharedPreferences(IRecLibrary.class.getSimpleName(), MODE_PRIVATE);
        int lang=Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_language), "0"));
        Locale.setDefault(new Locale[]{defaultLocale,Locale.ENGLISH,Locale.TRADITIONAL_CHINESE}[lang]);

        Resources resources=getResources();
        Configuration configuration=resources.getConfiguration();
        configuration.locale=Locale.getDefault();
        resources.updateConfiguration(configuration,resources.getDisplayMetrics());
        //IRecLibrary.getsInstance(this).lang=configuration.locale.equals(Locale.TRADITIONAL_CHINESE)?"hk":"en";

        Log.i(getClass().getSimpleName(), "updateLocale: lang=" + lang + " Locale=" + Locale.getDefault());
    }
}
