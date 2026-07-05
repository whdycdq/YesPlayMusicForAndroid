package com.yesplaymusic.android;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AndroidMediaControlsPlugin.class);
        registerPlugin(NeteaseWebLoginPlugin.class);
        super.onCreate(savedInstanceState);
        getBridge()
            .getWebView()
            .getSettings()
            .setMediaPlaybackRequiresUserGesture(false);
    }
}
