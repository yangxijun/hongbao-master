package com.young.hongbao;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RemoteViews;

/**
 * Created by chenjishi on 15/2/12.
 */
public class MonitorService extends AccessibilityService {
    private boolean mLuckyClicked;
    private boolean mLuckyGot;
    private int mCount;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        final SharedPreferences sp = LuckyApplication.getSharedPreferences();

        // 检测是否需要处理
        if (!sp.getBoolean(LuckyApplication.SP_START, true)) {
            return;
        }

        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            unlockScreen();
            mLuckyClicked = false;

            /**
             * for API >= 18, we use NotificationListenerService to detect the notifications
             * below API_18 we use AccessibilityService to detect
             */

            if (Build.VERSION.SDK_INT < 18) {
                Notification notification = (Notification) event.getParcelableData();
                List<String> textList = getText(notification);
                if (null != textList && textList.size() > 0) {
                    for (String text : textList) {
                        if (!TextUtils.isEmpty(text) && text.contains("[微信红包]")) {
                            final PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                pendingIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 聊天框
        // TODO 待优化
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String clazzName = event.getClassName().toString();
            Log.e("yxj", "classname " + clazzName);
            if (clazzName.equals("com.tencent.mm.ui.LauncherUI")) {
                mLuckyGot = false;

                AccessibilityNodeInfo nodeInfo = event.getSource();
                if (null != nodeInfo) {
                    List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("领取红包");
                    if (null != list && list.size() > 0) {
                        AccessibilityNodeInfo node = list.get(list.size() - 1);
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        } else {
                            AccessibilityNodeInfo parentNode = node;
                            for (int i = 0; i < 5; i++) {
                                if (null != parentNode) {
                                    parentNode = parentNode.getParent();
                                    if (null != parentNode && parentNode.isClickable() && !mLuckyClicked) {
                                        parentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        mLuckyClicked = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (clazzName.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {
                // 判断是否需要抢红包
                boolean autoGet = sp.getBoolean(LuckyApplication.SP_AUTO_GET, true);
                if (!autoGet) {
                    return;
                }

                if (sp.getBoolean(LuckyApplication.SP_PLAY_SOUND, true)) {
                    playSound();
                }

                final AccessibilityNodeInfo nodeInfo = event.getSource();
                if (null == nodeInfo) {
                    return;
                }

                // 加入延时机制
                final int delay = sp.getInt(LuckyApplication.SP_GET_DELAY, 0);
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            Thread.sleep(delay);
                        } catch (Exception e) {
                        }

                        LuckyApplication.post(new Runnable() {

                            @Override
                            public void run() {
                                traverseNode(nodeInfo);
                                mLuckyGot = true;
                            }
                        });
                    }
                }).start();
            }

            if (clazzName.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
                if (mLuckyGot) {
                    sp.edit().putLong(LuckyApplication.SP_LAST_GET_TIME, System.currentTimeMillis()).commit();
                    mLuckyGot = false;
                }
            }
        }
    }

    private void playSound() {
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.undock);
        mCount = 1;
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                if (mCount >= 3) {
                    mp.release();
                }
                mCount++;
            }
        });
        mediaPlayer.start();
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (null == node) return;

        final int count = node.getChildCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo childNode = node.getChild(i);
                if (null != childNode && childNode.getClassName().equals("android.widget.Button") && childNode.isClickable()) {
                    childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }

                traverseNode(childNode);
            }
        }
    }

    private void unlockScreen() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        final KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("MyKeyguardLock");
        keyguardLock.disableKeyguard();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");

        wakeLock.acquire();
    }

    public List<String> getText(Notification notification) {
        if (null == notification) return null;

        RemoteViews views = notification.bigContentView;
        if (views == null) views = notification.contentView;
        if (views == null) return null;

        // Use reflection to examine the m_actions member of the given RemoteViews object.
        // It's not pretty, but it works.
        List<String> text = new ArrayList<String>();
        try {
            Field field = views.getClass().getDeclaredField("mActions");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field.get(views);

            // Find the setText() and setTime() reflection actions
            for (Parcelable p : actions) {
                Parcel parcel = Parcel.obtain();
                p.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);

                // The tag tells which type of action it is (2 is ReflectionAction, from the source)
                int tag = parcel.readInt();
                if (tag != 2) continue;

                // View ID
                parcel.readInt();

                String methodName = parcel.readString();
                if (null == methodName) {
                    continue;
                } else if (methodName.equals("setText")) {
                    // Parameter type (10 = Character Sequence)
                    parcel.readInt();

                    // Store the actual string
                    String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString().trim();
                    text.add(t);
                }
                parcel.recycle();
            }
        } catch (Exception e) {
        }

        return text;
    }

    @Override
    public void onInterrupt() {

    }
}
