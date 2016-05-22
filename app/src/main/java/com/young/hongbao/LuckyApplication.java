package com.young.hongbao;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

public class LuckyApplication extends Application {

	public static final String SP_START = "sp_start";
	public static final String SP_AUTO_GET = "sp_auto_get";
	public static final String SP_GET_DELAY = "sp_get_delay";
	public static final String SP_PLAY_SOUND = "sp_play_sound";
	public static final String SP_LAST_GET_TIME = "sp_last_get_time";
	private static Handler sHandler = new Handler();
	private static SharedPreferences sSp;

    @Override
    public void onCreate() {
        super.onCreate();
		sSp = getSharedPreferences("default_settings", Context.MODE_MULTI_PROCESS);
    }

	public static void post(Runnable runnable) {
		sHandler.post(runnable);
	}

	public static SharedPreferences getSharedPreferences() {
		return sSp;
	}

	public static String getDateByTime(long time) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateString = formatter.format(new Date(time));
		return dateString;
	}
}
