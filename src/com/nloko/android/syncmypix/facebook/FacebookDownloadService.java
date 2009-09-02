//
//    FacebookDownloadService.java is part of SyncMyPix
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
import com.nloko.android.syncmypix.DownloadListener;
import com.nloko.android.syncmypix.GlobalConfig;
import com.nloko.android.syncmypix.HashUpdateService;
import com.nloko.android.syncmypix.SyncMyPix;
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Photos;
import android.widget.Toast;

// TODO this class should be refactored into something more general, so 
// new sources can easily extend it
public class FacebookDownloadService extends Service {
    
	private final String TAG = "FacebookDownloadService";
	
	// Handlers to run showErrorAndStop on UI thread
	private final Handler mainHandler = new Handler ();
	private final Runnable showDownloadError = new Runnable () {
		
		@SuppressWarnings("unchecked")
		public void run() {
			
			if (!resultsList.isEmpty()) {
				new updateResultsTable().execute(resultsList);
			}
			
			if (listener != null) {
				listener.error(0);
			}
			
			showErrorAndStop(R.string.facebookdownloadservice_downloaderror);
		}
	};
	
	private final Runnable showSessionError = new Runnable () {
		public void run() {
			if (listener != null) {
				listener.error(0);
			}
			
			showErrorAndStop(R.string.facebookdownloadservice_sessionerror);
		}
	};
	
	private final Runnable resetExecuting = new Runnable () {
		public void run() {
			executing = false;
		}
	};
	
    private DownloadListener listener;
    public void setListener (DownloadListener listener)
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

    private class updateResultsTable extends AsyncTask <List <ContentValues>, Integer, Long>
    {
		@Override
		protected Long doInBackground(List <ContentValues>... params) {
			
			List <ContentValues> list = params[0];
			
			Log.d(TAG, "Started" + Long.toString(System.currentTimeMillis()));
			for (ContentValues values : list) {
				if (values != null) {
					createResult(values);
				}
			}
			
			Log.d(TAG, "Ended" + Long.toString(System.currentTimeMillis()));
			
			return (long) list.size();
		}

		@Override
		protected void onPostExecute(Long result) {

			super.onPostExecute(result);
			resultsList.clear();
			
			long time = SystemClock.elapsedRealtime() + 90 * 1000;
			
            // Schedule the alarm!
            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            time, alarmSender);

            stopSelf();
		}
    	
		
    }
    
    private class Download extends AsyncTask <FacebookRestClient, Integer, Long>
    {
		@Override
		protected Long doInBackground(FacebookRestClient... clients) {
			
			long total = 0;
			int index = 0;
			
			FacebookRestClient client = clients[0];
			
			try {
				client = new FacebookRestClient(GlobalConfig.API_KEY, 
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("uid", null),
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("session_key", null),
							getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getString("secret", null));
				
				FacebookUsers users = new FacebookUsers (client);
				List<FacebookUser> userList = users.getUserInfo(users.getFriends());
				
				if (userList != null) {
					ContentResolver cr = getContentResolver();
					cr.delete(Sync.CONTENT_URI, null, null);
					Uri sync = cr.insert(Sync.CONTENT_URI, null);
					
					index = 1;
					for (FacebookUser user : userList) {
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
							mainHandler.post(showDownloadError);
							break;
						}
					}
					
					ContentValues syncValues = new ContentValues();
					syncValues.put(Sync.DATE_COMPLETED, System.currentTimeMillis());
					cr.update(sync, syncValues, null, null);
				}
				else {
					mainHandler.post(showDownloadError);
				}
				
				total = index;
			}
			catch (Exception ex) {
				Log.e(TAG, android.util.Log.getStackTraceString(ex));
				
				if (client == null) {
					mainHandler.post(showSessionError);
				}
				else {
					mainHandler.post(showDownloadError);
				}
			}
			finally {
				mainHandler.post(resetExecuting);
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
				cancelNotification(R.string.facebookdownloadservice_started, R.string.facebookdownloadservice_stopped);
				showNotification(R.string.facebookdownloadservice_stopped, android.R.drawable.stat_sys_download_done, true);
			}
			else {
				cancelNotification(R.string.facebookdownloadservice_started);
			}
			
			if (!resultsList.isEmpty()) {
				new updateResultsTable().execute(resultsList);
			}
		}
    }

    // just access directly. No IPC crap to deal with.
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
    	public FacebookDownloadService getService() {
            return FacebookDownloadService.this;
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
    
    private void createResult (ContentValues values)
    {
    	if (values == null) {
    		throw new IllegalArgumentException("values");
    	}

    	ContentResolver resolver = getContentResolver();
    	resolver.insert(Results.CONTENT_URI, values);
    }
    
    // Download class runs this method on a worker thread
    private void processUser(FacebookUser user, Uri sync) 
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
		
		if (user.picUrl == null || user.picUrl.equals("null") || user.picUrl == "") {
			values.put(Results.DESCRIPTION, "Picture not found");
			resultsList.add(values);
			return;
		}
		
		
		boolean skipIfConflict = getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getBoolean("skipIfConflict", false);
		
		String selection = Utils.buildNameSelection(People.NAME, user.firstName, user.lastName);  
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
					Uri contact = Uri.withAppendedPath(People.CONTENT_URI, id);
					
					if (updateContactPictures(cur, id)) {
							
						if (image == null) {
							image = Utils.downloadPicture(user.picUrl);
							hash = Utils.getMd5Hash(image);
						}
	
						if (image != null) {
							People.setPhotoData(this.getContentResolver(), contact, image);
							updateSyncContact(id, hash);
							
							/*Cursor photo = ContactServices.getPhoto(resolver, Photos.PERSON_ID + "='" + id + "'");
							if (photo.moveToFirst()) 
								Log.d(TAG, photo.getString(photo.getColumnIndex(Photos.LOCAL_VERSION)));
							
							photo.close();*/
						}
						else {
							values.put(Results.DESCRIPTION, "Picture download failed");
							break;
						}
					}
					else if (cur.getCount() == 1) {
						values.put(Results.DESCRIPTION, "Skipped: non-SyncMyPix picture exists");
					}

				} while (cur.moveToNext());
			}
		}

		resultsList.add(values);
		cur.close();
	}

    // called from processUser ie. worker thread
    private boolean updateContactPictures(Cursor cur, String id)
    {
    	if (cur == null) {
    		throw new IllegalArgumentException("cur");
    	}
    	
    	if (id == null) {
    		throw new IllegalArgumentException("id");
    	}
    	
    	ContentResolver resolver = getContentResolver();
    	
    	boolean skipIfExists = getSharedPreferences(GlobalConfig.PREFS_NAME, 0).getBoolean("skipIfExists", true);
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
    
    // called from processUser ie. worker thread
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
    
	private NotificationManager notifyManager;
	
    private updateResultsTable updateDbAsync;
    private List <ContentValues> resultsList = new ArrayList<ContentValues> ();
    private PendingIntent alarmSender;
	
    @Override
	public void onStart(Intent intent, int startId) {
    	
    	if (isExecuting()) {
    		return;
    	}
    	
		super.onStart(intent, startId);
		
		started = true;
		cancel = false;
		
		FacebookRestClient client = null;
		executing = true;
		new Download ().execute(client);
		
		notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifyManager.cancel(R.string.facebookdownloadservice_stopped);
		
		showNotification(R.string.facebookdownloadservice_started, android.R.drawable.stat_sys_download);
		launchProgress();
		
		alarmSender = PendingIntent.getService(FacebookDownloadService.this,
                0, new Intent(FacebookDownloadService.this, HashUpdateService.class), 0);

		handleHashUpdateService();
	}

    @Override
    public void onDestroy() {

    	unbindService(serviceConn);
    	
    	cancelNotification(R.string.facebookdownloadservice_started);
        unsetListener();

        started = false;
    	
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
    	
    	Intent i = new Intent(FacebookDownloadService.this, HashUpdateService.class);
    	if (bindService(i, serviceConn, 0)) {
    		if (boundService != null) {
    			boundService.cancelUpdate();
    		}
    	}
    }
    
    private void showErrorAndStop (int msg)
    {
    	cancelNotification(R.string.facebookdownloadservice_started, msg);
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
