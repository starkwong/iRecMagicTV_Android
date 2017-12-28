package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.app.ListFragment;
import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by starkwong on 15/01/10.
 */
public class BasePreferenceFragment extends PreferenceFragment implements BaseFragmentInterface {
    private MainActivity activity;
    public boolean customBack=false;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity= (MainActivity) activity;
    }

    @Override
    public MainActivity getMainActivity() {
        return activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity.setTitle(R.string.app_name);
        getMainActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
    }
}
