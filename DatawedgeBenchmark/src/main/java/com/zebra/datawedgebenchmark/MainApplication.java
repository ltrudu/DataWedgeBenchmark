package com.zebra.datawedgebenchmark;

import android.app.Application;
import android.util.Log;

import com.zebra.datawedgeprofileintents.DWProfileChecker;
import com.zebra.datawedgeprofileintents.DWProfileCheckerSettings;
import com.zebra.datawedgeprofileintentshelpers.CreateProfileHelper;

public class MainApplication extends Application {
    public static boolean profileExists = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize datawedge settings
        DatawedgeSettings.initSettings(this);

        // Check if profile exists if not, create a new one
        /*
        The profile checker will check if the profile already exists
         */
        DWProfileChecker checker = new DWProfileChecker(this);

        // Setup profile checker parameters
        DWProfileCheckerSettings profileCheckerSettings = new DWProfileCheckerSettings()
        {{
            mProfileName = DatawedgeSettings.mDemoProfileName;
            mTimeOutMS = DatawedgeSettings.mDemoTimeOutMS;
        }};

        checker.execute(profileCheckerSettings, new DWProfileChecker.onProfileExistResult() {
            @Override
            public void result(String profileName, boolean exists) {
                if(exists == false)
                {
                    CreateProfileHelper.createProfile(MainApplication.this, DatawedgeSettings.mSetConfigSettingsScanner, new CreateProfileHelper.CreateProfileHelperCallback() {
                        @Override
                        public void onSuccess(String profileName) {
                            profileExists = true;
                            if (BenchmarkActivity.benchmarkActivity != null) {
                                BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_profile_creation_success, profileName));
                                BenchmarkActivity.benchmarkActivity.onProfileCreated();
                            }
                        }

                        @Override
                        public void onError(String profileName, String error, String errorMessage) {
                            if (BenchmarkActivity.benchmarkActivity != null) {
                                BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_error_create_profile, profileName));
                                BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_error, error));
                                BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_error_message, errorMessage));
                            }
                        }

                        @Override
                        public void ondebugMessage(String profileName, String message) {
                            Log.d(BenchmarkActivity.TAG, message);
                        }
                    });
                }
                else
                {
                    profileExists = true;
                    if (BenchmarkActivity.benchmarkActivity != null) {
                        BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_profile_already_exists));
                        BenchmarkActivity.benchmarkActivity.onProfileCreated();
                    }
                }
            }

            @Override
            public void timeOut(String profileName) {
                if (BenchmarkActivity.benchmarkActivity != null) {
                    BenchmarkActivity.benchmarkActivity.addLineToResults(getString(R.string.msg_profile_check_timeout));
                }
            }
        });
    }
}
