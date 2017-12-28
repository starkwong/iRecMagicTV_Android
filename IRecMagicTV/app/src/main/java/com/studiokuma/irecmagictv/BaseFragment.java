package com.studiokuma.irecmagictv;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;

/**
 * Created by starkwong on 15/01/10.
 */
public class BaseFragment extends Fragment implements BaseFragmentInterface {
    private MainActivity activity;
    public boolean customBack=false;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity= (MainActivity) activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity.setTitle(R.string.app_name);
        getMainActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public MainActivity getMainActivity() {
        return activity;
    }

}
