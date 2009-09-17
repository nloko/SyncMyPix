//
//    SyncMyPixBroadcastReceiver.java is part of SyncMyPix
//
//    Authors:
//        Neil Loknath <neil.loknath@gmail.com>
//
//    Copyright (c) 2009 Neil Loknath
//
//    SyncMyPix is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    SyncMyPix is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with SyncMyPix.  If not, see <http://www.gnu.org/licenses/>.
//


package com.nloko.android.syncmypix;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.facebook.FacebookSyncService;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Contacts;


public class SyncMyPixBroadcastReceiver extends BroadcastReceiver {

	private final static String TAG = "SyncMyPixBroadcastReceiver";
	private final static String SYNC_STATE_CHANGED = "android.intent.action.SYNC_STATE_CHANGED";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Log.d(TAG, "RECEIVED INTENT");

		String action = intent.getAction();
		if (action.equals(Intent.ACTION_BOOT_COMPLETED) ) {
			//beginGoogleSync(context);
			rescheduleAlarm(context);
		}
		
		else if (action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
			// show about dialog on upgrades
			Utils.setBoolean(context.getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "do_not_show_about", false);
			
			rescheduleAlarm(context);
		}
		
		// this is undocumented stuff from android.content.SyncManager
		else if (action.equals(SYNC_STATE_CHANGED)) {
			
/*			Bundle extras = intent.getExtras();
			boolean active = extras.getBoolean("active");
			Log.d(TAG, String.format("ACTION SYNC DETECTED %b", active));
			
			if (!active) {
				startHashUpdateService(context);
			}
			
			GlobalConfig.setGoogleSyncing(active);*/
		}
	}
	
	private void beginGoogleSync(Context context)
	{
		Bundle extras = new Bundle();
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		
		context.getContentResolver().startSync(Contacts.CONTENT_URI, extras);
	}
	
	private void startHashUpdateService(Context context)
	{
		if (context == null) {
			throw new IllegalArgumentException("context");
		}
		
		PendingIntent alarmSender = PendingIntent.getService(context,
                0, new Intent(context, HashUpdateService.class), 0);
		
		long time = SystemClock.elapsedRealtime();
		
        // Schedule the alarm!
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        time, alarmSender);
	}
	
	private void rescheduleAlarm(Context context)
	{
		
		SharedPreferences settings = context.getSharedPreferences(GlobalConfig.PREFS_NAME, 0);
		int freq = settings.getInt("sched_freq", 0);
		long time = settings.getLong("sched_time", 0);
		long interval = GlobalConfig.getScheduleInterval(freq);
		
		if (time < System.currentTimeMillis()) {
			time += interval;
			Utils.setLong(context.getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "sched_time", time);
		}
		
		if (interval > 0) {
			Log.d(TAG, "SCHEDULING SERVICE");
			FacebookSyncService.updateSchedule(context, time, interval);
		}
	}

}
