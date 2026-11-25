package com.sazono.staff;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(EscposPrinterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
