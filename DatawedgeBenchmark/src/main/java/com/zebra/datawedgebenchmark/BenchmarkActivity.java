package com.zebra.datawedgebenchmark;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BenchmarkActivity extends AppCompatActivity {

    private static String TAG = "DWBenchmark";

    private TextView et_results;
    private ScrollView sv_results;
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

    private boolean profileCreated = false;
    private boolean firstWaitAfterProfileCreation = false;
    private boolean pendingStart = false;
    private boolean benchmarking = false;
    private boolean burstmode = false;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_benchmark);
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


        // Initialize datawedge settings
        DatawedgeSettings.initSettings(this);

        CreateProfileHelper.createProfile(this, DatawedgeSettings.mSetConfigSettingsScanner, new CreateProfileHelper.CreateProfileHelperCallback() {
            @Override
            public void onSuccess(String profileName) {
                addLineToResults("Profile creation of: " + profileName + " succeeded.");
                profileCreated = true;
                firstWaitAfterProfileCreation = true;
            }

            @Override
            public void onError(String profileName, String error, String errorMessage) {
                addLineToResults("Error while trying to create profile:" + profileName);
                addLineToResults("Error:" + error);
                addLineToResults("ErrorMessage:" + errorMessage);
            }

            @Override
            public void ondebugMessage(String profileName, String message) {
                Log.d(TAG, message);
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
                            if (pendingStart == true) {
                                // This is the first scan
                                // We start the counter
                                startInstant = Instant.now();
                                pendingStart = false;
                                updateResults(nbScans, "00:00:00.000", "N/A");
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
                            addLineToResults("Success scanning " + typology + " barcode.");
                        }
                        else {
                            addLineToResults("Source: " + source);
                            addLineToResults("Typology: " + typology+ ", Data: " + data);
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
                                addLineToResults("New scanner selected with success:" + selectedScanner.mName);
                                mScannerIndex = fPosition;
                            }
                            else
                            {
                                addLineToResults("Error while trying to switch to new scanner:" + selectedScanner.mName);
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
        mScanReceiver.startReceive();
        setupScannerStatusChecker();
        // Enumerate scanners
        enumerateScannerDevices();
    }

    @Override
    protected void onPause() {
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
        updateResults(nbScans, "00:00:00.000", "N/A");
        // Reset status edit text
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_start.setText("Start");
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
                bt_start.setText("Stop Benchmark");
            }
        });
        benchmarking = true;
        pendingStart = true;
        addLineToResults("Waiting for first scan to start Benchmark.");
    }

    private void stopBenchmark() {
        bt_reset.setEnabled(true);
        bt_burstmode.setEnabled(true);
        sp_ScannerDevices.setEnabled(true);
        addLineToResults("Benchmark stopped.");
        benchmarking = false;
        pendingStart = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bt_start.setText("Start Benchmark");
            }
        });
    }

    private void enumerateScannerDevices() {
        addLineToResults("Enumerating scanners.");
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
                    addLineToResults("Scanner enumeration succeeded, found " + scannerList.size() + " scanners.");
                    Iterator<DWEnumerateScanners.Scanner> it = scannerList.iterator();
                    while(it.hasNext()) {
                        DWEnumerateScanners.Scanner scanner = it.next();
                        friendlyNameList.add(scanner.mName);
                        ++spinnerIndex;
                    }
                }
                else {
                    addLineToResults("Failed to get the list of supported scanner devices! Please close and restart the application.");
                }

                // Add auto scanner selection
                DWEnumerateScanners.Scanner autoScanner = new DWEnumerateScanners.Scanner();
                autoScanner.mName = "AUTO";
                autoScanner.mScannerIdentifier = SC_E_SCANNER_IDENTIFIER.AUTO;
                friendlyNameList.add(0, autoScanner.mName);
                mScannerList.add(0, autoScanner);
                mScannerIndex = 0;

                final int fDefaultIndex = mScannerIndex;
                final List<String> fFriendlyNameList = friendlyNameList;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(BenchmarkActivity.this, android.R.layout.simple_spinner_item, fFriendlyNameList);
                        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        sp_ScannerDevices.setAdapter(spinnerAdapter);
                        sp_ScannerDevices.setSelection(fDefaultIndex);
                        mScannerIndex = fDefaultIndex;
                    }
                });
            }

            @Override
            public void timeOut(String profileName) {
                addLineToResults("Timeout while trying to enumerate scanners");
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
                            addLineToResults("Scanner is connected.");
                            break;
                        case DataWedgeConstants.SCAN_STATUS_DISABLED:
                            addLineToResults("Scanner is disabled.");
                            break;
                        case DataWedgeConstants.SCAN_STATUS_DISCONNECTED:
                            addLineToResults("Scanner is disconnected.");
                            break;
                        case DataWedgeConstants.SCAN_STATUS_SCANNING:
                            addLineToResults("Scanner is scanning.");
                            break;
                        case DataWedgeConstants.SCAN_STATUS_WAITING:
                            if(profileCreated == false)
                            {
                                return;
                            }
                            if(firstWaitAfterProfileCreation)
                            {
                                firstWaitAfterProfileCreation = false;
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
                            addLineToResults("Scanner is waiting.");

                            break;
                        case DataWedgeConstants.SCAN_STATUS_IDLE:
                            addLineToResults("Scanner is idle.");
                            break;
                    }
                }
            };
        }};

        addLineToResults("Setting up scanner status checking on package : " + profileStatusSettings.mPackageName + ".");

        mStatusReceiver = new DWStatusScanner(BenchmarkActivity.this, profileStatusSettings);
        mStatusReceiver.start();
    }

    private void stopScannerStatusChecker()
    {
        if(mStatusReceiver != null)
            mStatusReceiver.stop();
    }

    private void addLineToResults(final String lineToAdd)
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
                                    sv_results.fullScroll(ScrollView.FOCUS_DOWN);
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
                        addLineToResults("Scanner set to burst mode successfully");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                bt_burstmode.setText("Normal Mode");
                            }
                        });
                    }
                    else {
                        addLineToResults("Scanner set to normal mode successfully");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                bt_burstmode.setText("Burst Mode");
                            }
                        });
                    }
                    burstmode = targetMode;
                }
                else
                {
                    if(targetMode)
                        addLineToResults("Error while trying to set scanner to burst mode");
                    else
                        addLineToResults("Error while trying to set scanner to normal mode");
                }
            }

            @Override
            public void timeout(String profileName) {

            }
        });
    }
}