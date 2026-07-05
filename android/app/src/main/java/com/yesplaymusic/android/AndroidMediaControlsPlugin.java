package com.yesplaymusic.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AndroidMediaControls")
public class AndroidMediaControlsPlugin extends Plugin {
    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra(PlaybackService.EXTRA_MEDIA_ACTION);
            if (action == null) {
                return;
            }
            JSObject payload = new JSObject();
            payload.put("action", action);
            if (intent.hasExtra(PlaybackService.EXTRA_POSITION)) {
                payload.put(
                    "position",
                    intent.getDoubleExtra(PlaybackService.EXTRA_POSITION, 0.0)
                );
            }
            if (intent.hasExtra(PlaybackService.EXTRA_ERROR_CODE)) {
                payload.put(
                    "errorCode",
                    intent.getIntExtra(PlaybackService.EXTRA_ERROR_CODE, 0)
                );
            }
            if (intent.hasExtra(PlaybackService.EXTRA_PLAYING)) {
                payload.put(
                    "playing",
                    intent.getBooleanExtra(PlaybackService.EXTRA_PLAYING, false)
                );
            }
            if (intent.hasExtra(PlaybackService.EXTRA_SOURCE)) {
                payload.put(
                    "source",
                    intent.getStringExtra(PlaybackService.EXTRA_SOURCE)
                );
            }
            notifyListeners("mediaAction", payload, true);
        }
    };

    @Override
    public void load() {
        super.load();
        IntentFilter filter = new IntentFilter(PlaybackService.ACTION_MEDIA_EVENT);
        ContextCompat.registerReceiver(
            getContext(),
            mediaActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @PluginMethod
    public void setMetadata(PluginCall call) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_METADATA);
        intent.putExtra(PlaybackService.EXTRA_TITLE, call.getString("title", ""));
        intent.putExtra(PlaybackService.EXTRA_ARTIST, call.getString("artist", ""));
        intent.putExtra(PlaybackService.EXTRA_ALBUM, call.getString("album", ""));
        intent.putExtra(PlaybackService.EXTRA_ARTWORK, call.getString("artwork", ""));
        intent.putExtra(PlaybackService.EXTRA_DURATION, call.getDouble("duration", 0.0));
        ContextCompat.startForegroundService(getContext(), intent);
        call.resolve();
    }

    @PluginMethod
    public void setPlaybackState(PluginCall call) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_PLAYBACK_STATE);
        intent.putExtra(PlaybackService.EXTRA_PLAYING, call.getBoolean("playing", false));
        intent.putExtra(PlaybackService.EXTRA_POSITION, call.getDouble("position", 0.0));
        intent.putExtra(PlaybackService.EXTRA_DURATION, call.getDouble("duration", 0.0));
        ContextCompat.startForegroundService(getContext(), intent);
        call.resolve();
    }

    @PluginMethod
    public void setSource(PluginCall call) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_SOURCE);
        intent.putExtra(PlaybackService.EXTRA_SOURCE, call.getString("source", ""));
        intent.putExtra(PlaybackService.EXTRA_VOLUME, call.getDouble("volume", 1.0));
        ContextCompat.startForegroundService(getContext(), intent);
        call.resolve();
    }

    @PluginMethod
    public void play(PluginCall call) {
        startPlayerAction(PlaybackService.ACTION_APP_PLAY);
        call.resolve();
    }

    @PluginMethod
    public void pause(PluginCall call) {
        startPlayerAction(PlaybackService.ACTION_APP_PAUSE);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        startPlayerAction(PlaybackService.ACTION_APP_STOP);
        call.resolve();
    }

    @PluginMethod
    public void seek(PluginCall call) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_APP_SEEK);
        intent.putExtra(PlaybackService.EXTRA_POSITION, call.getDouble("position", 0.0));
        ContextCompat.startForegroundService(getContext(), intent);
        call.resolve();
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_VOLUME);
        intent.putExtra(PlaybackService.EXTRA_VOLUME, call.getDouble("volume", 1.0));
        ContextCompat.startForegroundService(getContext(), intent);
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        PlaybackService.StateSnapshot state = PlaybackService.getStateSnapshot();
        JSObject payload = new JSObject();
        if (state != null) {
            payload.put("source", state.source);
            payload.put("playing", state.playing);
            payload.put("position", state.positionSeconds);
            payload.put("duration", state.durationSeconds);
        }
        call.resolve(payload);
    }

    private void startPlayerAction(String action) {
        Intent intent = new Intent(getContext(), PlaybackService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(getContext(), intent);
    }

    @Override
    protected void handleOnDestroy() {
        try {
            getContext().unregisterReceiver(mediaActionReceiver);
        } catch (IllegalArgumentException ignored) {
            // The receiver was already removed with the Activity.
        }
        super.handleOnDestroy();
    }
}
