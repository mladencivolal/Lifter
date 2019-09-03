package hr.lalovic.mladen.lifter;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import hr.lalovic.mladen.lifter.fragments.SettingsFragment;

public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}
