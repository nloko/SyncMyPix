//
//    HashUpdateService.java is part of SyncMyPix
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

import java.io.InputStream;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Contacts;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Contacts.People;

// TODO this is a dirty hack because after a sync, the phone syncs the pics with Google
// which changes the pic, and thus, changes the pic hash

// v0.4 uses the _sync_version field to avoid the need for this
// will remove after awhile if all is well
public class HashUpdateService extends Service {

	public final static String TAG = "HashUpdateService";
	
	public final static int maxRuns = 3;
	
	private int count = 0;

	private Handler mainHandler;
	
	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		
		/*super.onStart(intent, startId);
		
		cancel = false;
		
		if (!isExecuting() && hasDownloadServiceRun(1000*300)) {
			
			executing = true;
			hashThread = new HashThread();
			hashThread.start();
		}
	
		mainHandler = new Handler();*/
	}

	
	private Runnable resetExecuting = new Runnable() {

		public void run() {
			executing = false;					
		}
	};

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		
		count = 0;
	}


	// just access directly. No IPC crap to deal with.
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
    	public HashUpdateService getService() {
            return HashUpdateService.this;
        }
    }
    
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return binder;
	}
	
	protected boolean hasDownloadServiceRun(long time)
	{
		Cursor cur = getContentResolver().query(Results.CONTENT_URI, 
				new String[] { Results._ID, Sync.DATE_COMPLETED }, 
				null, 
				null, 
				null);
		
		long now = System.currentTimeMillis();
		long completed = 0;
		
		if (cur.moveToFirst()) {
			completed = cur.getLong(cur.getColumnIndex(Sync.DATE_COMPLETED));
		}
		
		cur.close();
		
		Log.d(TAG, String.format("Time is %d and last sync at %d", now, completed));
		
		return (now - completed) <= time; 
	}
	
	private boolean cancel = false;
	public void cancelUpdate()
	{
		count = 0;
		cancel = true;
	}
	
	private boolean executing = false;
	public boolean isExecuting()
	{
		return executing;
	}
	
	private HashThread hashThread;
	
	private class HashThread extends Thread
	{
		HashThread () {}
		
		public void run()
		{
			Log.d(TAG, "Updating hashes after sync");

			Cursor cur = null;
			try {
				ContentResolver resolver = getContentResolver();
				cur = resolver.query(Contacts.CONTENT_URI, 
						new String[] { Contacts._ID, Contacts.PHOTO_HASH }, 
						null, 
						null, 
						null);
				
				
				while (cur.moveToNext() && !cancel) {
					
					String id = cur.getString(cur.getColumnIndex(Contacts._ID));
					Uri uri = Uri.withAppendedPath(People.CONTENT_URI, id);
					
					InputStream is = People.openContactPhotoInputStream(resolver, uri);
					if (is != null) {
						String hash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
						
						Uri contacts = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
						ContentValues values = new ContentValues();
						values.put(Contacts._ID, id);
						values.put(Contacts.PHOTO_HASH, hash);
						resolver.update(contacts, values, null, null);
					}
				}
			}
			finally {
				
				if (cur != null) {
					cur.close();
				}
				
				Log.d(TAG, String.format("Finished updating hashes after sync %d", count));
	
				mainHandler.post(resetExecuting);
				
				if (cancel || count++ == maxRuns) {
					count = 0;
					stopSelf();
				}
				else {
					rescheduleService();
				}
			}
			
		}
		
		private void rescheduleService()
		{
			AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
			PendingIntent alarmSender = PendingIntent.getService(HashUpdateService.this,
	                0, new Intent(HashUpdateService.this, HashUpdateService.class), 0);
			
			long time = SystemClock.elapsedRealtime() + 15 * 1000;
			am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    time, alarmSender);
	
		}
		
	}

}
