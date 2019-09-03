package hr.lalovic.mladen.lifter.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import hr.lalovic.mladen.lifter.R;

public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lifter_settings);
    }
}
