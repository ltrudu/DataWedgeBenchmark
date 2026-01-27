package com.zebra.datawedgebenchmark;

import android.content.Context;

import com.zebra.datawedgeprofileenums.INT_E_DELIVERY;
import com.zebra.datawedgeprofileenums.MB_E_CONFIG_MODE;
import com.zebra.datawedgeprofileenums.SC_E_AIM_TYPE;
import com.zebra.datawedgeprofileenums.SC_E_SCANNER_IDENTIFIER;
import com.zebra.datawedgeprofileintents.DWProfileSetConfigSettings;
import com.zebra.datawedgeprofileintents.SettingsPlugins.PluginScanner;

import java.util.HashMap;

public class DatawedgeSettings {
    protected static String mDemoProfileName = "com.symbol.datawedgebenchmark";
    protected static String mDemoIntentAction = "com.symbol.datawedgebenchmark.RECVR";
    protected static String mDemoIntentCategory = "android.intent.category.DEFAULT";
    protected static long mDemoTimeOutMS = 30000; //30s timeout...

    /**
     * This member will hold the profile settings that will be used to setup
     * the DataWedge configuration profile when doing a SetProfileConfig
     * for Scanner input
     */
    protected static DWProfileSetConfigSettings mSetConfigSettingsScanner;

    public static void initSettings(final Context context)
    {
        mSetConfigSettingsScanner = new DWProfileSetConfigSettings(){{
            mProfileName = mDemoProfileName;
            mTimeOutMS = mDemoTimeOutMS;
            MainBundle.APP_LIST = new HashMap<>();
            MainBundle.APP_LIST.put(context.getPackageName(), null);
            MainBundle.CONFIG_MODE = MB_E_CONFIG_MODE.CREATE_IF_NOT_EXIST;
            IntentPlugin.intent_action = mDemoIntentAction;
            IntentPlugin.intent_category = mDemoIntentCategory;
            IntentPlugin.intent_output_enabled = true;
            IntentPlugin.intent_delivery = INT_E_DELIVERY.BROADCAST;
            KeystrokePlugin.keystroke_output_enabled = false;
            RFIDPlugin.rfid_input_enabled = false;
            ScannerPlugin.scanner_selection_by_identifier = SC_E_SCANNER_IDENTIFIER.AUTO;
            ScannerPlugin.scanner_input_enabled = true;
            ScannerPlugin.Decoders.decoder_australian_postal = false;
            ScannerPlugin.Decoders.decoder_aztec = true;
            ScannerPlugin.Decoders.decoder_canadian_postal = false;
            ScannerPlugin.Decoders.decoder_chinese_2of5 = false;
            ScannerPlugin.Decoders.decoder_codabar = false;
            ScannerPlugin.Decoders.decoder_code11 = false;
            ScannerPlugin.Decoders.decoder_code128 = false;
            ScannerPlugin.Decoders.decoder_code39 = false;
            ScannerPlugin.Decoders.decoder_code93 = false;
            ScannerPlugin.Decoders.decoder_composite_ab = false;
            ScannerPlugin.Decoders.decoder_composite_c = false;
            ScannerPlugin.Decoders.decoder_d2of5 = false;
            ScannerPlugin.Decoders.decoder_datamatrix = false;
            ScannerPlugin.Decoders.decoder_dutch_postal = false;
            ScannerPlugin.Decoders.decoder_ean13 = false;
            ScannerPlugin.Decoders.decoder_ean8 = false;
            ScannerPlugin.Decoders.decoder_gs1_databar = false;
            ScannerPlugin.Decoders.decoder_gs1_qrcode = false;
            ScannerPlugin.Decoders.decoder_hanxin = false;
            ScannerPlugin.Decoders.decoder_i2of5 = false;
            ScannerPlugin.Decoders.decoder_japanese_postal = false;
            ScannerPlugin.Decoders.decoder_korean_3of5 = false;
            ScannerPlugin.Decoders.decoder_mailmark = false;
            ScannerPlugin.Decoders.decoder_matrix_2of5 = false;
            ScannerPlugin.Decoders.decoder_maxicode = false;
            ScannerPlugin.Decoders.decoder_micropdf = false;
            ScannerPlugin.Decoders.decoder_microqr = false;
            ScannerPlugin.Decoders.decoder_msi = false;
            ScannerPlugin.Decoders.decoder_pdf417 = false;
            ScannerPlugin.Decoders.decoder_qrcode = false;
            ScannerPlugin.Decoders.decoder_signature = false;
            ScannerPlugin.Decoders.decoder_tlc39 = false;
            ScannerPlugin.Decoders.decoder_trioptic39 = false;
            ScannerPlugin.Decoders.decoder_uk_postal = false;
            ScannerPlugin.Decoders.decoder_upca = false;
            ScannerPlugin.Decoders.decoder_upce0 = false;
            ScannerPlugin.Decoders.decoder_upce1 = false;
            ScannerPlugin.Decoders.decoder_us4state = false;
            ScannerPlugin.Decoders.decoder_usplanet = false;
            ScannerPlugin.Decoders.decoder_uspostnet = false;
            ScannerPlugin.Decoders.decoder_webcode = false;
        }};
    }
    
}
