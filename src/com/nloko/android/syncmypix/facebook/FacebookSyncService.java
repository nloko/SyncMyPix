//
//    FacebookSyncService.java is part of SyncMyPix
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

package com.nloko.android.syncmypix.facebook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.ContactServices;
import com.nloko.android.syncmypix.SyncServiceListener;
import com.nloko.android.syncmypix.GlobalConfig;
import com.nloko.android.syncmypix.HashUpdateService;
import com.nloko.android.syncmypix.SocialNetworkUser;
import com.nloko.android.syncmypix.SyncMyPix;
import com.nloko.android.syncmypix.ThumbnailCache;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;
import com.nloko.android.syncmypix.R;
import com.nloko.simplyfacebook.net.FacebookRestClient;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Photos;
import android.widget.Toast;

// TODO this class should be refactored into something more general, so 
// new sources can easily extend it
public class FacebookSyncService extends Service {
    
	private final String TAG = "FacebookSyncService";
	
	private final MainHandler mainHandler = new MainHandler ();

	private class MainHandler extends Handler
	{
		public static final int START_SYNC = 0;
		public static final int SHOW_ERROR = 1;
		
		MainHandler() {}
		
		private final Runnable resetExecuting = new Runnable () {
			public void run() {
				executing = false;
			}
		};
		
		private final Runnable finishResults = new Runnable () {
			public void run() {
				
				resultsList.clear();
				
				long time = SystemClock.elapsedRealtime() + 120 * 1000;
				
	            // Schedule the alarm!
	            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
	            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
	                            time, alarmSender);
	
	            stopSelf();
	
			}
		};
		
		private void handleError(int msg)
		{
			if (listener != null) {
				listener.error(0);
			}
			
			showError(msg);
		}
		
		@SuppressWarnings("unchecked")
		private void startSync(List<SocialNetworkUser> users)
		{
			new SyncTask().execute(users);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case START_SYNC:
					List<SocialNetworkUser> users = (List<SocialNetworkUser>)msg.obj;
					startSync(users);
					break;
				case SHOW_ERROR:
					handleError(msg.arg1);
					break;
			}
		}
	}
	
	private class FacebookLogin extends Thread
	{
		private MainHandler handler;
		public FacebookLogin(MainHandler handler)
		{
			this.handler = handler;
		}
		
		public void run()
		{
			FacebookRestClient client = null;
			
			try {
				client = new FacebookRestClient(GlobalConfig.API_KEY, 
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("uid", null),
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("session_key", null),
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("secret", null));
				
				FacebookApi api = new FacebookApi (client);
				List<SocialNetworkUser> userList = api.getUserInfo(api.getFriends(), maxQuality);
				
				// start sync from main thread
				Message msg = handler.obtainMessage();
				msg.what = MainHandler.START_SYNC;
				msg.obj = userList;
				
				handler.sendMessage(msg);
			}
			catch(Exception ex) {
				Log.e(TAG, android.util.Log.getStackTraceString(ex));
				
				if (client == null) {
					mainHandler.sendMessage(mainHandler.obtainMessage(mainHandler.SHOW_ERROR, 
							R.string.syncservice_sessionerror, 
							0));

				}
				else {
					mainHandler.sendMessage(mainHandler.obtainMessage(mainHandler.SHOW_ERROR, 
							R.string.syncservice_downloaderror, 
							0));
				}
				
				mainHandler.post(mainHandler.resetExecuting);
			}
		}
	}
	
	// This listener is used for communication of sync results to other activities
    private SyncServiceListener listener;
    public void setListener (SyncServiceListener listener)
    {
    	if (listener == null) {
    		throw new IllegalArgumentException ("listener");
    	}
    	
    	this.listener = listener;
    }
    
    public void unsetListener()
    {
    	listener = null;
    }

    private class UpdateResultsTable extends Thread
    {
    	private List<ContentValues> list;
    	public UpdateResultsTable(List<ContentValues> list)
    	{
    		this.list = list;
    	}
    	
    	private void createResult (ContentValues values)
        {
        	if (values == null) {
        		throw new IllegalArgumentException("values");
        	}

        	ContentResolver resolver = getContentResolver();
        	resolver.insert(Results.CONTENT_URI, values);
        }
     
		public void run() {
			
			Log.d(TAG, "Started updating results at " + Long.toString(System.currentTimeMillis()));
			for (ContentValues values : list) {
				if (values != null) {
					createResult(values);
				}
			}
			
			Log.d(TAG, "Finished updating results at " + Long.toString(System.currentTimeMillis()));
			
			mainHandler.post(mainHandler.finishResults);
		}
    }
    
    private class SyncTask extends AsyncTask <List<SocialNetworkUser>, Integer, Long>
    {
        private void processUser(SocialNetworkUser user, Uri sync) 
        {
    		if (user == null) {
    			throw new IllegalArgumentException ("user");
    		}
    		
    		if (sync == null) {
    			throw new IllegalArgumentException ("sync");
    		}
    		
    		Log.d(TAG, user.firstName + " " + user.lastName + " " + user.picUrl);
    		
    		ContentResolver resolver = getContentResolver();
    		String syncId = sync.getPathSegments().get(1);
    		
    		ContentValues values = new ContentValues();
    		String name = user.firstName + " " + user.lastName;
    		
    		values.put(Results.SYNC_ID, syncId);
    		values.put(Results.NAME, name);
    		values.put(Results.PIC_URL, user.picUrl);
    		values.put(Results.DESCRIPTION, "Picture Updated");
    		
    		if (user.picUrl == null) {
    			values.put(Results.DESCRIPTION, "Picture not found");
    			resultsList.add(values);
    			return;
    		}
    		
        		
    		String selection;
    		if (!reverseNames) {
    			selection = Utils.buildNameSelection(People.NAME, user.firstName, user.lastName);
    		}
    		else {
    			selection = Utils.buildNameSelection(People.NAME, user.lastName, user.firstName);
    		}
    		
    		Cursor cur = ContactServices.getContact(resolver, selection);
    		
    		if (!cur.moveToFirst()) {
    			Log.d(TAG, "Contact not found in database.");
    			values.put(Results.DESCRIPTION, "Contact not found");
    		}
    		else {
    			boolean ok = true;
    			
    			if (cur.getCount() > 1) {
    				Log.d(TAG, String.format("Multiple contacts found %d", cur.getCount()));
    				
    				if (skipIfConflict) {
    					values.put(Results.DESCRIPTION, "Skipped: multiple found");
    					ok = false;
    				}
    				else {
    					values.put(Results.DESCRIPTION, "Multiple contacts processed; conflicts may have occurred");
    				}
    			}
    			
    			if (ok) {
    				
    				byte[] image = null;
    				String hash = null;

    				do {
    					String id = cur.getString(cur.getColumnIndex(People._ID));
    					final Uri contact = Uri.withAppendedPath(People.CONTENT_URI, id);
    					
    					if (updateContactPictures(cur, id)) {
    							
    						if (image == null) {
    							try {
    								image = Utils.downloadPicture(user.picUrl);
    								hash = Utils.getMd5Hash(image);
    							}
    							catch (Exception e) {}
    						}
    	
    						if (image != null) {
    							//People.setPhotoData(resolver, contact, image);
    							
/*    							// nudge the Google sync operation along 
    							Bundle extras = new Bundle();
    							extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
    							extras.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true);
    							resolver.startSync(contact, extras);*/
    	    							
    							ContactServices.updateContactPhoto(getContentResolver(), image, id);
    							
    							values.put(Results.CONTACT_ID, Long.parseLong(id));
    							updateSyncContact(id, hash);
    						}
    						else {
    							values.put(Results.DESCRIPTION, "Picture download failed");
    							break;
    						}
    					}
    					else if (cur.getCount() == 1) {
    						values.put(Results.DESCRIPTION, "Skipped: non-SyncMyPix picture exists");
    					}

    					// TODO This is such crap, I hate it. There must be a better way.
    					// track last processed to HashUpdateService doesn't update too many hashes
    					// if sync was cancelled
    					//Utils.setInt(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "last_contact_processed", Integer.parseInt(id));
    					
    				} while (cur.moveToNext());
    			}
    		}

    		resultsList.add(values);
    		cur.close();
    	}

        private boolean updateContactPictures(Cursor cur, String id)
        {
        	if (cur == null) {
        		throw new IllegalArgumentException("cur");
        	}
        	
        	if (id == null) {
        		throw new IllegalArgumentException("id");
        	}
        	
        	ContentResolver resolver = getContentResolver();
        	

        	boolean ok = true;

        	Uri contact = Uri.withAppendedPath(People.CONTENT_URI, id);
        	    	
        	if (skipIfExists) {
        		Uri syncUri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
        		Cursor syncC = resolver.query(syncUri, 
        				new String[] { SyncMyPix.Contacts._ID,
        				SyncMyPix.Contacts.PHOTO_HASH }, 
        				null, 
        				null, 
        				null);
        		
        		String hash = null;
        		InputStream is = People.openContactPhotoInputStream(resolver, contact);
        		
        		// photo is set, so let's get its hash
        		if (is != null) {
        			hash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
        		}

        		// not tracking any hashes for contact and photo is set for contact
        		if (!syncC.moveToFirst() && hash != null) {
        			ok = false;
        		}
        		
        		// we are tracking a hash and there is a photo for this contact
        		else if (hash != null) {
        			String dbHash = syncC.getString(syncC.getColumnIndex(SyncMyPix.Contacts.PHOTO_HASH));
        			Log.d(TAG, String.format("dbhash %s hash %s", dbHash, hash));

        			// hashes do not match, so we don't need to track this hash anymore
        			if (!hash.equals(dbHash)) {
       					resolver.delete(syncUri, null, null);
        				ok = false;
        			}
        		}

        		syncC.close();
        	}
        	
        	return ok;
        }
        
        private void updateSyncContact (String id, String hash)
        {
        	if (id == null) {
        		throw new IllegalArgumentException("id");
        	}
        	
        	ContentResolver resolver = getContentResolver();
        	Uri uri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
        	
    		ContentValues values = new ContentValues();
    		values.put(SyncMyPix.Contacts._ID, id);
    		values.put(SyncMyPix.Contacts.PHOTO_HASH, hash);
    		
        	Cursor cur = resolver.query(uri, new String[] { SyncMyPix.Contacts._ID }, null, null, null);
    		if (cur.getCount() == 0) {
    			resolver.insert(SyncMyPix.Contacts.CONTENT_URI, values);
    		}
    		else {
    			resolver.update(uri, values, null, null);
    		}
    		
    		cur.close();
        }

		@Override
		protected Long doInBackground(List<SocialNetworkUser>... users) {
			
			long total = 0;
			int index = 0;
			
			List<SocialNetworkUser> userList = users[0];
			try {
				ContentResolver cr = getContentResolver();
				cr.delete(Sync.CONTENT_URI, null, null);
				Uri sync = cr.insert(Sync.CONTENT_URI, null);

				index = 1;
				for (SocialNetworkUser user : userList) {
					String name = user.firstName + " " + user.lastName;

					// keep going if exception during sync
					try {
						processUser(user, sync);
					}
					catch (Exception processException) {

						Log.e(TAG, android.util.Log.getStackTraceString(processException));

						ContentValues values = new ContentValues();
						String syncId = sync.getPathSegments().get(1);
						values.put(Results.SYNC_ID, syncId);
						values.put(Results.NAME, name);
						values.put(Results.PIC_URL, user.picUrl);
						values.put(Results.DESCRIPTION, "Exception caught during sync");

						resultsList.add(values);
					}

					publishProgress((int) ((index++ / (float) userList.size()) * 100), index, userList.size());

					if (cancel) {
						mainHandler.sendMessage(mainHandler.obtainMessage(mainHandler.SHOW_ERROR, 
								R.string.syncservice_canceled, 
								0));

						break;
					}
				}

				ContentValues syncValues = new ContentValues();
				syncValues.put(Sync.DATE_COMPLETED, System.currentTimeMillis());
				cr.update(sync, syncValues, null, null);
				
				total = index;
			}
			catch (Exception ex) {
				Log.e(TAG, android.util.Log.getStackTraceString(ex));
				mainHandler.sendMessage(mainHandler.obtainMessage(mainHandler.SHOW_ERROR, 
						R.string.syncservice_fatalsyncerror, 
						0));

			}
			finally {
				mainHandler.post(mainHandler.resetExecuting);
			}
			
			return total;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (listener != null) {
				listener.updateUI(values[0], values[1], values[2]);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void onPostExecute(Long result) {
			if (result > 0 && !cancel) {
				cancelNotification(R.string.syncservice_started, R.string.syncservice_stopped);
				showNotification(R.string.syncservice_stopped, android.R.drawable.stat_sys_download_done, true);
			}
			else {
				cancelNotification(R.string.syncservice_started);
			}
			
			if (!resultsList.isEmpty()) {
				new UpdateResultsTable(resultsList).start();
			}
			
			ContentResolver resolver = getContentResolver();
			
/*			// nudge Google sync
			Bundle extras = new Bundle();
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
			resolver.startSync(Contacts.CONTENT_URI, new Bundle());*/
		}
    }

    // just access directly. No IPC crap to deal with.
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
    	public FacebookSyncService getService() {
            return FacebookSyncService.this;
        }
    }

    private boolean cancel = false;
    public void cancelOperation()
    {
    	if (isExecuting()) {
    		cancel = true;
    	}
    }
    
    private boolean executing = false;
    public boolean isExecuting()
    {
    	return executing;
    }
    
    private boolean started = false;
    public boolean isStarted () 
    {
    	return started;
    }
    
    
	private NotificationManager notifyManager;
	
    private List <ContentValues> resultsList = new ArrayList<ContentValues> ();
    private PendingIntent alarmSender;
	
    private boolean skipIfExists;
    private boolean skipIfConflict;
    private boolean reverseNames;
    private boolean maxQuality;
    private void getPreferences()
    {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		skipIfConflict = prefs.getBoolean("skipIfConflict", false);
		reverseNames = prefs.getBoolean("reverseNames", false);
		maxQuality = prefs.getBoolean("maxQuality", false);
    	skipIfExists = prefs.getBoolean("skipIfExists", true);
    }
    
    @Override
	public void onStart(Intent intent, int startId) {
    	
    	if (isExecuting()) {
    		return;
    	}
    	
		super.onStart(intent, startId);
		
		executing = true;
		started = true;
		cancel = false;

		getPreferences();
		
		// cancel Google sync, if running
		//ContentResolver().cancelSync(Contacts.CONTENT_URI);
		
		new FacebookLogin(mainHandler).start();
		
		notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifyManager.cancel(R.string.syncservice_stopped);
		
		showNotification(R.string.syncservice_started, android.R.drawable.stat_sys_download);
		launchProgress();
		
		alarmSender = PendingIntent.getService(getBaseContext(),
                0, new Intent(getBaseContext(), HashUpdateService.class), 0);

		handleHashUpdateService();
	}

    @Override
    public void onDestroy() {

    	unbindService(serviceConn);
    	
    	cancelNotification(R.string.syncservice_started);
        unsetListener();

        started = false;
    	
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public static void cancelSchedule(Context context)
    {
    	PendingIntent alarmSender = PendingIntent.getService(context,
                0, new Intent(context, FacebookSyncService.class), 0);
    	
    	AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
    	am.cancel(alarmSender);
    }
    
    public static void updateSchedule(Context context, long startTime, long interval)
    {
    	if (context == null) {
    		throw new IllegalArgumentException("context");
    	}
    	
    	PendingIntent alarmSender = PendingIntent.getService(context,
                0, new Intent(context, FacebookSyncService.class), 0);
    	
    	AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
    	am.setRepeating(AlarmManager.RTC_WAKEUP, startTime, interval, alarmSender);
    }
    
    private void launchProgress()
    {
    	Intent i = new Intent(this, GlobalConfig.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    
    private void handleHashUpdateService()
    {
    	AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
    	am.cancel(alarmSender);
    	
    	Intent i = new Intent(getBaseContext(), HashUpdateService.class);
    	if (bindService(i, serviceConn, 0)) {
    		if (boundService != null) {
    			boundService.cancelUpdate();
    		}
    	}
    }
    
    private void showError (int msg)
    {
    	cancelNotification(R.string.syncservice_started, msg);
    }
    
    private void cancelNotification (int msg)
    {
    	cancelNotification(msg, -1);
    }
    
    private void cancelNotification (int msg, int toastMsg)
    {
    	if (isStarted ()) {
    		notifyManager.cancel(msg);
    	}

        if (toastMsg >= 0) {
        	Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showNotification(int msg, int icon)
    {
    	showNotification(msg, icon, false);
    }
    
    private void showNotification(int msg, int icon, boolean autoCancel) 
    {
        CharSequence text = getText(msg);
        Notification notification = new Notification(icon, text,
                System.currentTimeMillis());
        
        if (autoCancel) {
        	notification.flags = Notification.FLAG_AUTO_CANCEL;
        }

        // The PendingIntent to launch our activity if the user selects this notification
        Intent i = new Intent(this, GlobalConfig.class);
        //i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                i, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(msg),
                       text, contentIntent);

        notifyManager.notify(msg, notification);
    }
    
	private HashUpdateService boundService;
	private boolean serviceConnected = false;
    private ServiceConnection serviceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	
        	serviceConnected = true;
        	
        	boundService = ((HashUpdateService.LocalBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            
        	serviceConnected = false;
        	boundService = null;
        }
    };
}
