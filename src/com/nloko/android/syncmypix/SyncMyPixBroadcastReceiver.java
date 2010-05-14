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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class SyncMyPixBroadcastReceiver extends BroadcastReceiver {

	private final static String TAG = "SyncMyPixBroadcastReceiver";
	private final static String SYNC_STATE_CHANGED = "android.intent.action.SYNC_STATE_CHANGED";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Log.d(TAG, "RECEIVED INTENT");

		String action = intent.getAction();
		if (action.equals(Intent.ACTION_BOOT_COMPLETED) ) {
			rescheduleAlarm(context.getApplicationContext());
		}
		
		else if (action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
			Log.d(TAG, "ACTION_PACKAGE_REPLACED");

			// show about dialog on upgrades
			Utils.setBoolean(context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "do_not_show_about", false);
			rescheduleAlarm(context.getApplicationContext());
		}
		
		else if(action.equals(SyncMyPix.SYNC_INTENT)) {
			Context appContext = context.getApplicationContext();
			SyncWakeLock.acquireWakeLock(appContext);
			appContext.startService(new Intent(appContext, MainActivity.getSyncSource(appContext)));
		}
		
		// this is undocumented stuff from android.content.SyncManager
		else if (action.equals(SYNC_STATE_CHANGED)) {
		}
	}
	
	private void rescheduleAlarm(Context context)
	{
		SharedPreferences settings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
		int freq = settings.getInt("sched_freq", 0);
		long time = settings.getLong("sched_time", 0);
		long interval = SettingsActivity.getScheduleInterval(freq);
		
		Log.d(TAG, "freq: " + freq);
		Log.d(TAG, "time: " + time);
		Log.d(TAG, "interval: " + interval);
		
		if (time < System.currentTimeMillis()) {
			time += interval;
			Log.d(TAG, "time + interval: " + time);
			Utils.setLong(context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "sched_time", time);
		}
		
		if (interval > 0) {
			Log.d(TAG, "Scheduling Service...");
			SyncService.updateSchedule(context, 
					MainActivity.getSyncSource(context), 
					time, 
					interval);
		}
	}

}
