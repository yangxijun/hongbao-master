package com.young.hongbao;

import java.util.List;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final Intent sSettingsIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);

    private TextView mAccessibleLabel;
    private TextView mNotificationLabel;
    private CheckBox mStart;
    private TextView mLabelText;
    private CheckBox mAutoGet;
    private CheckBox mPlaySound;
    private EditText mDelay;
    private TextView mLastGetTime;

    private SharedPreferences mSp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mAccessibleLabel = (TextView) findViewById(R.id.label_accessible);
        mNotificationLabel = (TextView) findViewById(R.id.label_notification);
        mLabelText = (TextView) findViewById(R.id.label_text);

        if (Build.VERSION.SDK_INT >= 18) {
            mNotificationLabel.setVisibility(View.VISIBLE);
            findViewById(R.id.button_notification).setVisibility(View.VISIBLE);
        } else {
            mNotificationLabel.setVisibility(View.GONE);
            findViewById(R.id.button_notification).setVisibility(View.GONE);
        }

        mSp = LuckyApplication.getSharedPreferences();

        mStart = (CheckBox) findViewById(R.id.start);
        mStart.setChecked(mSp.getBoolean(LuckyApplication.SP_START, true));
        mStart.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSp.edit().putBoolean(LuckyApplication.SP_START, isChecked).commit();
            }
        });

        mAutoGet = (CheckBox) findViewById(R.id.auto_get);
        mAutoGet.setChecked(mSp.getBoolean(LuckyApplication.SP_AUTO_GET, true));

        mAutoGet.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSp.edit().putBoolean(LuckyApplication.SP_AUTO_GET, isChecked)
                        .commit();

                mDelay.setVisibility(isChecked ? View.VISIBLE
                        : View.INVISIBLE);
            }
        });

        mDelay = (EditText) findViewById(R.id.delay);
        mDelay.setText(String.valueOf(mSp.getInt(LuckyApplication.SP_GET_DELAY,
                0)));
        mDelay.setVisibility(mAutoGet.isChecked() ? View.VISIBLE
                : View.INVISIBLE);
        mDelay.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                mSp.edit().putInt(LuckyApplication.SP_GET_DELAY, Integer.valueOf(v.getText().toString()))
                        .commit();

                Toast.makeText(MainActivity.this, "延时为 " + v.getText().toString() + " 毫秒", Toast.LENGTH_SHORT).show();
                return false;
            }
        });

        mPlaySound = (CheckBox) findViewById(R.id.play_music);
        mPlaySound.setChecked(mSp.getBoolean(LuckyApplication.SP_PLAY_SOUND,
                true));
        mPlaySound.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSp.edit().putBoolean(LuckyApplication.SP_PLAY_SOUND, isChecked)
                        .commit();
            }
        });

        findViewById(R.id.stop).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        });

        mLastGetTime = (TextView) findViewById(R.id.last_get_time);
        long time = mSp.getLong(LuckyApplication.SP_LAST_GET_TIME, 0);
        mLastGetTime.setText("上次抢红包时间 " + LuckyApplication.getDateByTime(time));
    }


    @Override
    protected void onResume() {
        super.onResume();
        changeLabelStatus();
    }

    private void changeLabelStatus() {
        boolean isAccessibilityEnabled = isAccessibleEnabled();
        mAccessibleLabel.setTextColor(isAccessibilityEnabled ? 0xFF009588 : Color.RED);
        mAccessibleLabel.setText(isAccessibleEnabled() ? "辅助功能已打开" : "辅助功能未打开");
        mLabelText.setText(isAccessibilityEnabled ? "好了~你可以去做其他事情了，我会自动给你抢红包的" : "请打开开关开始抢红包");

        if (Build.VERSION.SDK_INT >= 18) {
            boolean isNotificationEnabled = isNotificationEnabled();
            mNotificationLabel.setTextColor(isNotificationEnabled ? 0xFF009588 : Color.RED);
            mNotificationLabel.setText(isNotificationEnabled ? "接收通知已打开" : "接收通知未打开");

            if (isAccessibilityEnabled && isNotificationEnabled) {
                mLabelText.setText("配置完毕,可以正常使用了");
            } else {
                mLabelText.setText("请把两个开关都打开开始抢红包");
            }
        }

        long time = mSp.getLong(LuckyApplication.SP_LAST_GET_TIME, 0);
        mLastGetTime.setText("上次抢红包时间 " + LuckyApplication.getDateByTime(time));
    }

    public void onNotificationEnableButtonClicked(View view) {
        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    public void onSettingsClicked(View view) {
        startActivity(sSettingsIntent);
    }

    private boolean isAccessibleEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        List<AccessibilityServiceInfo> runningServices = manager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
        for (AccessibilityServiceInfo info : runningServices) {
            if (info.getId().equals(getPackageName() + "/.MonitorService")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotificationEnabled() {
        ContentResolver contentResolver = getContentResolver();
        String enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");

        if (!TextUtils.isEmpty(enabledListeners)) {
            return enabledListeners.contains(getPackageName() + "/" + getPackageName() + ".NotificationService");
        } else {
            return false;
        }
    }
}
