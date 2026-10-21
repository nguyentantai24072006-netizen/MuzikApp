package com.example.muzzicapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class AudioOutputDialogHelper {

    private static int lastNonZeroVolume = 5;

    public static void showAudioOutputDialog(Context context) {
        if (context == null) return;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_output, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        dialog.setOnShowListener(d -> {
            View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                bottomSheetInternal.setBackground(null);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        int colorAccent = ContextCompat.getColor(context, R.color.coral_primary);
        int colorAccentLight = ContextCompat.getColor(context, R.color.coral_light);
        int colorSecondary = ContextCompat.getColor(context, R.color.text_secondary_adaptive);
        int colorTertiary = ContextCompat.getColor(context, R.color.text_tertiary_adaptive);

        ImageView btnClose = view.findViewById(R.id.dialog_audio_btn_close);
        ImageView btnMute = view.findViewById(R.id.dialog_audio_btn_mute);
        ImageView headerIcon = view.findViewById(R.id.dialog_audio_header_icon);
        SeekBar volumeSeekBar = view.findViewById(R.id.dialog_audio_volume_seekbar);
        TextView tvVolumePercent = view.findViewById(R.id.dialog_audio_tv_volume_percent);

        View itemSpeaker = view.findViewById(R.id.dialog_audio_item_speaker);
        FrameLayout boxSpeaker = view.findViewById(R.id.dialog_audio_box_speaker);
        ImageView iconSpeaker = view.findViewById(R.id.dialog_audio_icon_speaker);
        TextView tvSpeakerSub = view.findViewById(R.id.dialog_audio_tv_speaker_sub);
        ImageView checkSpeaker = view.findViewById(R.id.dialog_audio_check_speaker);

        View itemHeadset = view.findViewById(R.id.dialog_audio_item_headset);
        FrameLayout boxHeadset = view.findViewById(R.id.dialog_audio_box_headset);
        ImageView iconHeadset = view.findViewById(R.id.dialog_audio_icon_headset);
        TextView tvHeadsetName = view.findViewById(R.id.dialog_audio_tv_headset_name);
        TextView tvHeadsetSub = view.findViewById(R.id.dialog_audio_tv_headset_sub);
        ImageView checkHeadset = view.findViewById(R.id.dialog_audio_check_headset);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // --- Volume Slider Setup ---
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume > 0) lastNonZeroVolume = currentVolume;

        volumeSeekBar.setMax(maxVolume);
        volumeSeekBar.setProgress(currentVolume);

        Runnable updateVolumeUI = () -> {
            int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int percent = (int) Math.round((vol * 100.0) / maxVolume);
            tvVolumePercent.setText(percent + "%");
            tvVolumePercent.setTextColor(colorAccent);

            if (vol == 0) {
                btnMute.setImageResource(R.drawable.ic_volume_mute);
                btnMute.setColorFilter(colorTertiary);
                headerIcon.setImageResource(R.drawable.ic_volume_mute);
            } else {
                btnMute.setImageResource(R.drawable.ic_volume_up);
                btnMute.setColorFilter(colorAccent);
                headerIcon.setImageResource(R.drawable.ic_volume_up);
            }
        };
        updateVolumeUI.run();

        final boolean[] isTracking = new boolean[]{false};
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    if (progress > 0) lastNonZeroVolume = progress;
                    updateVolumeUI.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking[0] = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking[0] = false;
            }
        });

        btnMute.setOnClickListener(v -> {
            int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (vol > 0) {
                lastNonZeroVolume = vol;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                volumeSeekBar.setProgress(0);
            } else {
                int restoreVol = lastNonZeroVolume > 0 ? lastNonZeroVolume : Math.max(1, maxVolume / 2);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0);
                volumeSeekBar.setProgress(restoreVol);
            }
            updateVolumeUI.run();
        });

        // Listen for hardware volume button changes while dialog is open
        BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!isTracking[0]) {
                    int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    volumeSeekBar.setProgress(vol);
                    if (vol > 0) lastNonZeroVolume = vol;
                    updateVolumeUI.run();
                }
            }
        };
        context.registerReceiver(volumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));

        // --- Audio Output Devices Setup ---
        Runnable refreshDevicesUI = new Runnable() {
            @Override
            public void run() {
                AudioDeviceInfo speakerDevice = null;
                AudioDeviceInfo connectedHeadset = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo dev : devices) {
                        int type = dev.getType();
                        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            speakerDevice = dev;
                        } else if (isHeadsetType(type)) {
                            connectedHeadset = dev;
                        }
                    }
                }

                AudioDeviceInfo preferred = MusicPlayerManager.getInstance().getPreferredAudioDevice();
                boolean isSpeakerActive = true;

                if (connectedHeadset != null) {
                    itemHeadset.setVisibility(View.VISIBLE);

                    CharSequence name = connectedHeadset.getProductName();
                    if (name != null && name.length() > 0) {
                        tvHeadsetName.setText(name);
                    } else if (connectedHeadset.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || connectedHeadset.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        tvHeadsetName.setText("Tai nghe Bluetooth");
                    } else if (connectedHeadset.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                        tvHeadsetName.setText("Tai nghe USB Type-C");
                    } else {
                        tvHeadsetName.setText("Tai nghe có dây");
                    }

                    // Determine active device
                    if (preferred != null) {
                        isSpeakerActive = (preferred.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
                    } else {
                        // System default routes to headset when connected
                        isSpeakerActive = false;
                    }
                } else {
                    itemHeadset.setVisibility(View.GONE);
                    isSpeakerActive = true;
                }

                // Update Speaker UI
                if (isSpeakerActive) {
                    itemSpeaker.setBackgroundResource(R.drawable.bg_audio_card_active);
                    boxSpeaker.setBackgroundResource(R.drawable.bg_audio_icon_box_active);
                    iconSpeaker.setColorFilter(colorAccent);
                    tvSpeakerSub.setText("Đang phát âm thanh");
                    tvSpeakerSub.setTextColor(colorAccentLight);
                    checkSpeaker.setVisibility(View.VISIBLE);
                } else {
                    itemSpeaker.setBackgroundResource(R.drawable.bg_audio_card);
                    boxSpeaker.setBackgroundResource(R.drawable.bg_audio_icon_box_inactive);
                    iconSpeaker.setColorFilter(colorSecondary);
                    tvSpeakerSub.setText("Thiết bị khả dụng");
                    tvSpeakerSub.setTextColor(colorTertiary);
                    checkSpeaker.setVisibility(View.GONE);
                }

                // Update Headset UI
                if (connectedHeadset != null) {
                    if (!isSpeakerActive) {
                        itemHeadset.setBackgroundResource(R.drawable.bg_audio_card_active);
                        boxHeadset.setBackgroundResource(R.drawable.bg_audio_icon_box_active);
                        iconHeadset.setColorFilter(colorAccent);
                        tvHeadsetSub.setText("Đang phát âm thanh");
                        tvHeadsetSub.setTextColor(colorAccentLight);
                        checkHeadset.setVisibility(View.VISIBLE);
                    } else {
                        itemHeadset.setBackgroundResource(R.drawable.bg_audio_card);
                        boxHeadset.setBackgroundResource(R.drawable.bg_audio_icon_box_inactive);
                        iconHeadset.setColorFilter(colorSecondary);
                        tvHeadsetSub.setText("Đã kết nối");
                        tvHeadsetSub.setTextColor(colorTertiary);
                        checkHeadset.setVisibility(View.GONE);
                    }
                }

                final AudioDeviceInfo finalSpeaker = speakerDevice;
                final AudioDeviceInfo finalHeadset = connectedHeadset;

                // Click speaker item
                itemSpeaker.setOnClickListener(v -> {
                    MusicPlayerManager.getInstance().setPreferredAudioDevice(finalSpeaker);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && finalSpeaker != null) {
                        audioManager.setCommunicationDevice(finalSpeaker);
                    }
                    audioManager.setSpeakerphoneOn(true);
                    Toast.makeText(context, "🔊 Đã chuyển âm thanh sang Loa ngoài", Toast.LENGTH_SHORT).show();
                    run();
                });

                // Click headset item
                if (connectedHeadset != null) {
                    itemHeadset.setOnClickListener(v -> {
                        MusicPlayerManager.getInstance().setPreferredAudioDevice(finalHeadset);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && finalHeadset != null) {
                            audioManager.setCommunicationDevice(finalHeadset);
                        }
                        audioManager.setSpeakerphoneOn(false);
                        Toast.makeText(context, "🎧 Đã chuyển âm thanh sang " + tvHeadsetName.getText(), Toast.LENGTH_SHORT).show();
                        run();
                    });
                }
            }
        };

        refreshDevicesUI.run();

        // Listen for plug/unplug of audio devices in real-time
        AudioDeviceCallback audioDeviceCallback = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    new Handler(Looper.getMainLooper()).post(refreshDevicesUI);
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    new Handler(Looper.getMainLooper()).post(refreshDevicesUI);
                }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));
        }

        final AudioDeviceCallback finalCallback = audioDeviceCallback;
        dialog.setOnDismissListener(d -> {
            try {
                context.unregisterReceiver(volumeReceiver);
            } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && finalCallback != null) {
                try {
                    audioManager.unregisterAudioDeviceCallback(finalCallback);
                } catch (Exception ignored) {}
            }
        });

        dialog.show();
    }

    private static boolean isHeadsetType(int type) {
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return true;
            }
        }
        return false;
    }
}
