package com.zebra.datawedgebenchmark;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.core.widget.NestedScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.zebra.datawedgeprofileenums.MB_E_CONFIG_MODE;
import com.zebra.datawedgeprofileenums.SC_E_AIM_TYPE;
import com.zebra.datawedgeprofileenums.SC_E_SCANNER_IDENTIFIER;
import com.zebra.datawedgeprofileintents.DWEnumerateScanners;
import com.zebra.datawedgeprofileintents.DWEnumerateScannersSettings;
import com.zebra.datawedgeprofileintents.DWProfileBaseSettings;
import com.zebra.datawedgeprofileintents.DWProfileCommandBase;
import com.zebra.datawedgeprofileintents.DWProfileSetConfig;
import com.zebra.datawedgeprofileintents.DWScanReceiver;
import com.zebra.datawedgeprofileintents.DWScannerPluginDisable;
import com.zebra.datawedgeprofileintents.DWScannerPluginEnable;
import com.zebra.datawedgeprofileintents.DWScannerResumePlugin;
import com.zebra.datawedgeprofileintents.DWScannerSuspendPlugin;
import com.zebra.datawedgeprofileintents.DWStatusScanner;
import com.zebra.datawedgeprofileintents.DWStatusScannerCallback;
import com.zebra.datawedgeprofileintents.DWStatusScannerSettings;
import com.zebra.datawedgeprofileintents.DataWedgeConstants;
import com.zebra.datawedgeprofileintents.IProfileCommandResult;
import com.zebra.datawedgeprofileintentshelpers.CreateProfileHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class BenchmarkActivity extends AppCompatActivity {

    public static String TAG = "DWBenchmark";

    public static BenchmarkActivity benchmarkActivity = null;

    private TextView et_results;
    private NestedScrollView sv_results;
    private String mResults = "";

    private TextView tv_nbScans;
    private TextView tv_timeElapsed;
    private TextView tv_nbScansPerS;

    private Button bt_reset;
    private Button bt_start;
    private Button bt_burstmode;

    private Spinner sp_ScannerDevices = null;
    private List<DWEnumerateScanners.Scanner> mScannerList = null;
    private int mScannerIndex = 0;

    private boolean pendingStart = false;
    private boolean benchmarking = false;
    private boolean burstmode = false;
    private boolean burstModeFirstScan = false;
    private int nbScans = 0;
    private Instant startInstant;
    private Instant lastScanInstant;

    /**
     * Scanner data receiver
     */
    DWScanReceiver mScanReceiver;

    DWStatusScanner mStatusReceiver;

    /*
        Handler and runnable to scroll down textview
     */
    private Handler mScrollDownHandler = null;
    private Runnable mScrollDownRunnable = null;

    /*
        Activity log expand/collapse
     */
    private LinearLayout ll_log_header;
    private LinearLayout ll_log_content;
    private ImageView iv_expand_icon;
    private boolean isLogExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_benchmark);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        et_results = findViewById(R.id.et_results);
        sv_results = findViewById(R.id.sv_status);
        tv_nbScans = findViewById(R.id.tv_nbScans);
        tv_timeElapsed = findViewById(R.id.tv_timeElapsed);
        tv_nbScansPerS = findViewById(R.id.tv_nbScansPerS);
        bt_reset = findViewById(R.id.bt_reset);
        bt_start = findViewById(R.id.bt_start);
        sp_ScannerDevices = (Spinner)findViewById(R.id.spinnerScannerDevices);
        bt_burstmode = findViewById(R.id.bt_burstmode);

        bt_reset.setEnabled(false);
        bt_start.setEnabled(false);
        bt_burstmode.setEnabled(false);
        sp_ScannerDevices.setEnabled(false);

        // Set up expandable activity log
        ll_log_header = findViewById(R.id.ll_log_header);
        ll_log_content = findViewById(R.id.ll_log_content);
        iv_expand_icon = findViewById(R.id.iv_expand_icon);

        ll_log_header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLogExpand();
            }
        });

        /**
         * Initialize the scan receiver
         */
        mScanReceiver = new DWScanReceiver(this,
                DatawedgeSettings.mDemoIntentAction,
                DatawedgeSettings.mDemoIntentCategory,
                false,
                new DWScanReceiver.onScannedData() {
                    @Override
                    public void scannedData(String source, String data, String typology) {
                        if(benchmarking) {
                            nbScans++;
                            if (pendingStart == true || burstModeFirstScan == true) {
                                if(burstModeFirstScan == true)
                                {
                                    // Force nbScans to 1 since we don't know if the scan has been relaunched
                                    // for the second time
                                    nbScans = 1;
                                }
                                // This is the first scan
                                // We start the counter
                                startInstant = Instant.now();
                                pendingStart = false;
                                burstModeFirstScan = false;
                                updateResults(nbScans, getString(R.string.default_time), getString(R.string.default_na));
                            } else {
                                // Calculate time elapsed since start date and number of scans per seconds
                                lastScanInstant = Instant.now();
                                Duration duration = Duration.between(startInstant, lastScanInstant);
                                String formattedTime = String.format("%02d:%02d:%02d.%03d",
                                        duration.toHours(),
                                        duration.toMinutesPart(),
                                        duration.toSecondsPart(),
                                        duration.toMillisPart());
                                double totalSeconds = duration.toMillis() / 1000.0;
                                double scansPerSeconds = (totalSeconds > 0) ? (nbScans / totalSeconds) : 0.0;
                                String scanPerSecondsFormatted = String.format("%.2f", scansPerSeconds);
                                updateResults(nbScans, formattedTime, scanPerSecondsFormatted);
                            }
                            addLineToResults(getString(R.string.msg_scan_success, typology));
                        }
                        else {
                            addLineToResults(getString(R.string.msg_scan_source, source));
                            addLineToResults(getString(R.string.msg_scan_typology_data, typology, data));
                        }
                    }
                }
        );

        sp_ScannerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View arg1, int position, long arg3) {
                if (mScannerIndex != position) {
                    final DWEnumerateScanners.Scanner selectedScanner = mScannerList.get(position);
                    DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.scanner_selection_by_identifier = selectedScanner.mScannerIdentifier;
                    DatawedgeSettings.mSetConfigSettingsScanner.MainBundle.CONFIG_MODE = MB_E_CONFIG_MODE.OVERWRITE;

                    DWProfileSetConfig dwProfileSetConfig = new DWProfileSetConfig(BenchmarkActivity.this);
                    final int fPosition = position;
                    dwProfileSetConfig.execute(DatawedgeSettings.mSetConfigSettingsScanner, new IProfileCommandResult() {
                        @Override
                        public void result(String profileName, String action, String command, String result, String resultInfo, String commandidentifier) {
                            if(result.equalsIgnoreCase(DataWedgeConstants.COMMAND_RESULT_SUCCESS)) {
                                addLineToResults(getString(R.string.msg_scanner_selected, selectedScanner.mName));
                                mScannerIndex = fPosition;
                            }
                            else
                            {
                                addLineToResults(getString(R.string.msg_error_switch_scanner, selectedScanner.mName));
                                BenchmarkActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        BenchmarkActivity.this.sp_ScannerDevices.setSelection(mScannerIndex);
                                    }
                                });
                            }
                        }

                        @Override
                        public void timeout(String profileName) {

                        }
                    });
                }
            }
            @Override
            public void onNothingSelected (AdapterView < ? > arg0){
            }
        });

        bt_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(pendingStart == true || benchmarking == true)
                {
                    // We are in the process of scanning, so this is a stop action
                    stopBenchmark();
                    return;
                }
                startBenchmark();
            }
        });

        bt_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetAll();
            }
        });

        bt_burstmode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleBurstMode();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(((MainApplication)getApplication()).profileExists)
        {
            addLineToResults(getString(R.string.msg_profile_already_exists));
            // The main application has already created the profile
            onProfileCreated();
        }
        benchmarkActivity = this;
    }

    public void onProfileCreated()
    {
        mScanReceiver.startReceive();
        setupScannerStatusChecker();
        // Enumerate scanners
        enumerateScannerDevices();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_reset.setEnabled(true);
                bt_start.setEnabled(true);
                bt_burstmode.setEnabled(true);
                sp_ScannerDevices.setEnabled(true);
            }
        });
    }

    @Override
    protected void onPause() {
        benchmarkActivity = null;
        mScanReceiver.stopReceive();
        if(mScrollDownRunnable != null)
        {
            mScrollDownHandler.removeCallbacks(mScrollDownRunnable);
            mScrollDownRunnable = null;
            mScrollDownHandler = null;
        }
        stopScannerStatusChecker();
        // Stop the benchmark
        stopBenchmark();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_benchmark, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset_profile) {
            resetProfile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void resetProfile() {
        addLineToResults(getString(R.string.msg_resetting_profile));
        // Reset settings class to initial values in case we were in BurstMode
        DatawedgeSettings.initSettings(this);

        // Recreate profile with initial settings
        CreateProfileHelper.createProfile(BenchmarkActivity.this, DatawedgeSettings.mSetConfigSettingsScanner, new CreateProfileHelper.CreateProfileHelperCallback() {
            @Override
            public void onSuccess(String profileName) {
                addLineToResults(getString(R.string.msg_profile_reset_success));
                // Reset burst mode
                burstmode = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bt_burstmode.setText(R.string.btn_normal_mode);
                    }
                });
            }

            @Override
            public void onError(String profileName, String error, String errorMessage) {
                addLineToResults(getString(R.string.msg_error_reset_profile, profileName));
                addLineToResults(getString(R.string.msg_error, error));
                addLineToResults(getString(R.string.msg_error_message, errorMessage));
            }

            @Override
            public void ondebugMessage(String profileName, String message) {
                Log.d(BenchmarkActivity.TAG, message);
            }
        });
    }

    private void updateResults(int nbScans, String formattedTime, String nbScansPerS)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_nbScans.setText(String.valueOf(nbScans));
                tv_timeElapsed.setText(formattedTime);
                tv_nbScansPerS.setText(nbScansPerS);
            }
        });
    }

    private void resetAll()
    {
        pendingStart = false;
        benchmarking = false;
        nbScans = 0;
        startInstant = null;
        lastScanInstant = null;
        mResults = "";
        updateResults(nbScans, getString(R.string.default_time), getString(R.string.default_na));
        // Reset status edit text
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_start.setText(R.string.btn_start);
                et_results.setText(mResults);
            }
        });
    }

    private void startBenchmark()
    {
        // Disable spinner
        bt_burstmode.setEnabled(false);
        bt_reset.setEnabled(false);
        sp_ScannerDevices.setEnabled(false);
        // Reset everything
        resetAll();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_start.setText(R.string.btn_stop_benchmark);
            }
        });
        benchmarking = true;
        pendingStart = true;
        addLineToResults(getString(R.string.msg_waiting_first_scan));
    }

    private void stopBenchmark() {
        bt_reset.setEnabled(true);
        bt_burstmode.setEnabled(true);
        sp_ScannerDevices.setEnabled(true);
        addLineToResults(getString(R.string.msg_benchmark_stopped));
        benchmarking = false;
        pendingStart = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_start.setText(R.string.btn_start_benchmark);
            }
        });
    }

    private void enumerateScannerDevices() {
        addLineToResults(getString(R.string.msg_enumerating_scanners));
        DWEnumerateScanners dwEnumerateScanners = new DWEnumerateScanners(this);
        DWEnumerateScannersSettings settings = new DWEnumerateScannersSettings()
        {{
            mProfileName = DatawedgeSettings.mDemoProfileName;
        }};
        dwEnumerateScanners.execute(settings, new DWEnumerateScanners.onEnumerateScannerResult() {
            @Override
            public void result(String profileName, List<DWEnumerateScanners.Scanner> scannerList) {
                List<String> friendlyNameList = new ArrayList<String>();
                mScannerList = new ArrayList<DWEnumerateScanners.Scanner>(scannerList);
                int spinnerIndex = 0;
                if ((scannerList != null) && (scannerList.size() != 0)) {
                    addLineToResults(getString(R.string.msg_enumeration_success, scannerList.size()));
                    Iterator<DWEnumerateScanners.Scanner> it = scannerList.iterator();
                    while(it.hasNext()) {
                        DWEnumerateScanners.Scanner scanner = it.next();
                        friendlyNameList.add(scanner.mName);
                        ++spinnerIndex;
                    }
                }
                else {
                    addLineToResults(getString(R.string.msg_error_scanner_list));
                }

                // Add auto scanner selection
                DWEnumerateScanners.Scanner autoScanner = new DWEnumerateScanners.Scanner();
                autoScanner.mName = getString(R.string.scanner_auto);
                autoScanner.mScannerIdentifier = SC_E_SCANNER_IDENTIFIER.AUTO;
                friendlyNameList.add(0, autoScanner.mName);
                mScannerList.add(0, autoScanner);
                mScannerIndex = 0;

                final int fDefaultIndex = mScannerIndex;
                final List<String> fFriendlyNameList = friendlyNameList;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(BenchmarkActivity.this, R.layout.spinner_item, fFriendlyNameList);
                        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        sp_ScannerDevices.setAdapter(spinnerAdapter);
                        sp_ScannerDevices.setSelection(fDefaultIndex);
                        mScannerIndex = fDefaultIndex;
                    }
                });
            }

            @Override
            public void timeOut(String profileName) {
                addLineToResults(getString(R.string.msg_enumeration_timeout));
            }
        });
    }

    private void setupScannerStatusChecker()
    {
        DWStatusScannerSettings profileStatusSettings = new DWStatusScannerSettings()
        {{
            mPackageName = getPackageName();
            mScannerCallback = new DWStatusScannerCallback() {
                @Override
                public void result(String status) {
                    switch(status)
                    {
                        case DataWedgeConstants.SCAN_STATUS_CONNECTED:
                            addLineToResults(getString(R.string.msg_scanner_connected));
                            break;
                        case DataWedgeConstants.SCAN_STATUS_DISABLED:
                            addLineToResults(getString(R.string.msg_scanner_disabled));
                            break;
                        case DataWedgeConstants.SCAN_STATUS_DISCONNECTED:
                            addLineToResults(getString(R.string.msg_scanner_disconnected));
                            break;
                        case DataWedgeConstants.SCAN_STATUS_SCANNING:
                            addLineToResults(getString(R.string.msg_scanner_scanning));
                            break;
                        case DataWedgeConstants.SCAN_STATUS_WAITING:
                            if(burstmode)
                            {
                                burstModeFirstScan = true;
                            }
                            addLineToResults(getString(R.string.msg_scanner_waiting));

                            break;
                        case DataWedgeConstants.SCAN_STATUS_IDLE:
                            addLineToResults(getString(R.string.msg_scanner_idle));
                            if(burstmode)
                            {
                                burstModeFirstScan = true;
                            }
                            break;
                    }
                }
            };
        }};

        addLineToResults(getString(R.string.msg_status_checker_setup, profileStatusSettings.mPackageName));

        mStatusReceiver = new DWStatusScanner(BenchmarkActivity.this, profileStatusSettings);
        mStatusReceiver.start();
    }

    private void stopScannerStatusChecker()
    {
        if(mStatusReceiver != null)
            mStatusReceiver.stop();
    }

    public void addLineToResults(final String lineToAdd)
    {
        mResults += lineToAdd + "\n";
        updateAndScrollDownTextView();
    }

    private void updateAndScrollDownTextView() {
        if(mScrollDownHandler == null)
        {
            mScrollDownHandler = new Handler(Looper.getMainLooper());
        }
        if (mScrollDownRunnable == null) {
            mScrollDownRunnable = new Runnable() {
                @Override
                public void run() {
                    BenchmarkActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            et_results.setText(mResults);
                            sv_results.post(new Runnable() {
                                @Override
                                public void run() {
                                    sv_results.fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    });
                }
            };
        } else {
            // A new line has been added while we were waiting to scroll down
            // reset handler to repost it....
            mScrollDownHandler.removeCallbacks(mScrollDownRunnable);
        }
        mScrollDownHandler.postDelayed(mScrollDownRunnable, 300);
    }

    private void toggleLogExpand() {
        isLogExpanded = !isLogExpanded;

        if (isLogExpanded) {
            ll_log_content.setVisibility(View.VISIBLE);
            // Rotate arrow up
            RotateAnimation rotateAnimation = new RotateAnimation(0, 180,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(200);
            rotateAnimation.setFillAfter(true);
            iv_expand_icon.startAnimation(rotateAnimation);
        } else {
            ll_log_content.setVisibility(View.GONE);
            // Rotate arrow down
            RotateAnimation rotateAnimation = new RotateAnimation(180, 0,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(200);
            rotateAnimation.setFillAfter(true);
            iv_expand_icon.startAnimation(rotateAnimation);
        }
    }

    private void toggleBurstMode()
    {
        final boolean targetMode = !burstmode;
        DatawedgeSettings.mSetConfigSettingsScanner.MainBundle.CONFIG_MODE = MB_E_CONFIG_MODE.OVERWRITE;
        if(targetMode)
        {
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.aim_type = SC_E_AIM_TYPE.CONTINUOUS_READ;
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.different_barcode_timeout = 0;
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.same_barcode_timeout = 0;
        }
        else
        {
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.aim_type = SC_E_AIM_TYPE.TRIGGER;
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.different_barcode_timeout = 500;
            DatawedgeSettings.mSetConfigSettingsScanner.ScannerPlugin.ReaderParams.same_barcode_timeout = 500;
        }

        DWProfileSetConfig dwProfileSetConfig = new DWProfileSetConfig(BenchmarkActivity.this);
        dwProfileSetConfig.execute(DatawedgeSettings.mSetConfigSettingsScanner, new IProfileCommandResult() {
            @Override
            public void result(String profileName, String action, String command, String result, String resultInfo, String commandidentifier) {
                if(result.equalsIgnoreCase(DataWedgeConstants.COMMAND_RESULT_SUCCESS)) {
                    if(targetMode) {
                        addLineToResults(getString(R.string.msg_scanner_burst_mode_success));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                bt_burstmode.setText(R.string.btn_normal_mode);
                            }
                        });
                    }
                    else {
                        addLineToResults(getString(R.string.msg_scanner_normal_mode_success));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                bt_burstmode.setText(R.string.btn_burst_mode);
                            }
                        });
                    }
                    burstmode = targetMode;
                }
                else
                {
                    if(targetMode)
                        addLineToResults(getString(R.string.msg_error_burst_mode));
                    else
                        addLineToResults(getString(R.string.msg_error_normal_mode));
                }
            }

            @Override
            public void timeout(String profileName) {

            }
        });
    }
}