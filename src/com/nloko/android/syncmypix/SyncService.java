//
//    SyncService.java is part of SyncMyPix
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
import java.util.ArrayList;
import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.ResultsDescription;
import com.nloko.android.syncmypix.SyncMyPix.Sync;
import com.nloko.android.syncmypix.SyncMyPixDbHelper.DBHashes;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Contacts.People;
import android.widget.Toast;

public abstract class SyncService extends Service {

	private final static String TAG = "SyncService";
	
	public final static Object syncLock = new Object();
	
	private final MainHandler mainHandler = new MainHandler ();
	public MainHandler getMainHandler()
	{
		return mainHandler;
	}
	
	protected class MainHandler extends Handler
	{
		public static final int START_SYNC = 0;
		public static final int SHOW_ERROR = 1;
		
		MainHandler() {}
		
		public final Runnable resetExecuting = new Runnable () {
			public void run() {
				executing = false;
			}
		};
		
		public final Runnable finishResults = new Runnable () {
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
		
		public void handleError(int msg)
		{
			if (listener != null) {
				listener.error(msg);
			}
			
			showError(msg);
		}
		
		@SuppressWarnings("unchecked")
		public void startSync(List<SocialNetworkUser> users)
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

        	//Log.d(TAG, String.format("Creating result for %s with contact id %s", values.getAsString(Results.NAME), values.getAsString(Results.CONTACT_ID)));
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
    	private final SyncMyPixDbHelper dbHelper = new SyncMyPixDbHelper(getBaseContext());
    	
    	private final ContentResolver resolver = getContentResolver();
    	private final StringBuilder sb = new StringBuilder();
    	    	
        private void processUser(SocialNetworkUser user, Uri sync) 
        {
    		if (user == null) {
    			throw new IllegalArgumentException ("user");
    		}
    		
    		if (sync == null) {
    			throw new IllegalArgumentException ("sync");
    		}
    		
    		sb.delete(0, sb.length());
    		sb.append(user.firstName);
    		sb.append(" ");
    		sb.append(user.lastName);
    		
    		// escape single quotes for SQL
    		final String name = sb.toString().replace("'", "''");
    		Log.d(TAG, String.format("%s %s", name, user.picUrl));
    		
    		final String syncId = sync.getPathSegments().get(1);
    		
    		ContentValues values = createResult(syncId, name, user.picUrl);
    		
    		if (user.picUrl == null) {
    			values.put(Results.DESCRIPTION, "Picture not found");
    			resultsList.add(values);
    			return;
    		}
    		
    		final String selection;
    		if (!reverseNames) {
    			selection = Utils.buildNameSelection(People.NAME, user.firstName, user.lastName);
    		}
    		else {
    			selection = Utils.buildNameSelection(People.NAME, user.lastName, user.firstName);
    		}
    		
    		final Cursor cur = ContactServices.getContact(resolver, selection);
    		if (!cur.moveToFirst()) {
    			Log.d(TAG, "Contact not found in database.");
    			values.put(Results.DESCRIPTION, ResultsDescription.NOTFOUND.getDescription(getBaseContext()));
    			resultsList.add(values);
    		}
    		else {
    			boolean ok = true;
    			
    			if (cur.getCount() > 1) {
    				Log.d(TAG, String.format("Multiple contacts found %d", cur.getCount()));
    				
    				if (skipIfConflict) {
    					values.put(Results.DESCRIPTION, ResultsDescription.SKIPPED_MULTIPLEFOUND.getDescription(getBaseContext()));
    					resultsList.add(values);
    					ok = false;
    				}
    			}
    			
    			if (ok) {
    				
    				InputStream is = null;
    				Bitmap bitmap = null;
    				byte[] image = null;
    				
    				String contactHash = null;
    				String hash = null;

    				do {
    					//ContentValues is an immutable object
    					ContentValues valuesCopy = new ContentValues(values);
    					
    					try {
	    					String id = cur.getString(cur.getColumnIndex(People._ID));
	    					DBHashes hashes = dbHelper.getHashes(id);
	    					
	    					Uri contact = Uri.withAppendedPath(People.CONTENT_URI, id);
	    	        		is = People.openContactPhotoInputStream(resolver, contact);
	    	        		// photo is set, so let's get its hash
	    	        		if (is != null) {
	    	        			contactHash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
	    	        		}
	    	        		
	    					if (dbHelper.isSyncablePicture(id, hashes.updatedHash, contactHash, skipIfExists)) {
	    							
	    						if (image == null) {
	    							try {
	    								bitmap = Utils.downloadPictureAsBitmap(user.picUrl);
	    								image = Utils.bitmapToJpeg(bitmap, 100);
	    								hash = Utils.getMd5Hash(image);
	    							}
	    							catch (Exception e) {}
	    						}
	    	
	    						if (image != null) {
	    							// picture is a new one and we should sync it
	    							if ((hash != null && !hash.equals(hashes.networkHash)) || is == null) {
	    								
	    								String updatedHash = hash;
	    								
	    								if (cropSquare) {
	    									bitmap = Utils.centerCrop(bitmap, 96, 96);
	    									image = Utils.bitmapToJpeg(bitmap, 100);
	    									updatedHash = Utils.getMd5Hash(image);
	    								}
	    								
		    							ContactServices.updateContactPhoto(getContentResolver(), image, id);
		    							dbHelper.updateHashes(id, hash, updatedHash);
	    							}
	    							else {
	    								valuesCopy.put(Results.DESCRIPTION, 
	    										ResultsDescription.SKIPPED_UNCHANGED.getDescription(getBaseContext()));
	    							}
	    							
	    							valuesCopy.put(Results.CONTACT_ID, id);
	    						}
	    						else {
	    							valuesCopy.put(Results.DESCRIPTION, 
	    									ResultsDescription.DOWNLOAD_FAILED.getDescription(getBaseContext()));
	    							break;
	    						}
	    					}
	    					else {
	    						valuesCopy.put(Results.DESCRIPTION, 
	    								ResultsDescription.SKIPPED_EXISTS.getDescription(getBaseContext()));
	    					}

    					}
    					catch (Exception e) {
    						valuesCopy.put(Results.DESCRIPTION, ResultsDescription.ERROR.getDescription(getBaseContext()));
    					}
    					finally {
    						resultsList.add(valuesCopy);
    					}
    					 
    				} while (cur.moveToNext());
    			}
    		}

    		cur.close();
    	}

        private ContentValues createResult(String id, String name, String url)
        {
        	ContentValues values = new ContentValues();
    		values.put(Results.SYNC_ID, id);
    		values.put(Results.NAME, name);
    		values.put(Results.PIC_URL, url);
    		values.put(Results.DESCRIPTION, ResultsDescription.UPDATED.getDescription(getBaseContext()));
    		
    		return values;
        }
        
        
		@Override
		protected Long doInBackground(List<SocialNetworkUser>... users) {
			
			long total = 0;
			int index = 0;
			
			List<SocialNetworkUser> userList = users[0];
			
			synchronized(syncLock) {
				
				try {
					// clear previous results, if any
					resolver.delete(Sync.CONTENT_URI, null, null);
					Uri sync = resolver.insert(Sync.CONTENT_URI, null);
	
					index = 1;
					for (SocialNetworkUser user : userList) {
						processUser(user, sync);
						publishProgress((int) ((index++ / (float) userList.size()) * 100), index, userList.size());
	
						if (cancel) {
							mainHandler.sendMessage(mainHandler.obtainMessage(MainHandler.SHOW_ERROR, 
									R.string.syncservice_canceled, 
									0));
	
							break;
						}
					}
	
					ContentValues syncValues = new ContentValues();
					syncValues.put(Sync.DATE_COMPLETED, System.currentTimeMillis());
					resolver.update(sync, syncValues, null, null);
					
					total = index;
				
				} catch (Exception ex) {
					Log.e(TAG, android.util.Log.getStackTraceString(ex));
					mainHandler.sendMessage(mainHandler.obtainMessage(MainHandler.SHOW_ERROR, 
							R.string.syncservice_fatalsyncerror, 
							0));
	
				} finally {
					mainHandler.post(mainHandler.resetExecuting);
				}
				
			}
			
			return total;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (listener != null) {
				listener.updateUI(values[0], values[1], values[2]);
			}
		}

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
	
    protected boolean skipIfExists;
    protected boolean skipIfConflict;
    protected boolean reverseNames;
    protected boolean maxQuality;
    protected boolean cropSquare;
    
    private void getPreferences()
    {
		SyncMyPixPreferences prefs = new SyncMyPixPreferences(getBaseContext());
		
		skipIfConflict = prefs.getSkipIfConflict();
		reverseNames = prefs.getReverseNames();
		maxQuality = prefs.getMaxQuality();
    	skipIfExists = prefs.getSkipIfExists();
    	cropSquare = prefs.getCropSquare();
    }
    
    @Override
	public void onStart(Intent intent, int startId) {
    	
    	super.onStart(intent, startId);
		
		executing = true;
		started = true;
		cancel = false;

		getPreferences();
		
		notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifyManager.cancel(R.string.syncservice_stopped);
		
		showNotification(R.string.syncservice_started, android.R.drawable.stat_sys_download);
		launchProgress();
		
		alarmSender = PendingIntent.getService(getBaseContext(),
                0, new Intent(getBaseContext(), HashUpdateService.class), 0);

	}

    @Override
    public void onDestroy() {

    	cancelNotification(R.string.syncservice_started);
        unsetListener();

        started = false;
    	
        super.onDestroy();
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
        Intent i = new Intent(this, MainActivity.class);
        //i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                i, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(msg),
                       text, contentIntent);

        notifyManager.notify(msg, notification);
    }
    
    private void launchProgress()
    {
    	Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

	// just access directly. No IPC crap to deal with.
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
    	public SyncService getService() {
            return SyncService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public static <T extends SyncService> void cancelSchedule(Context context, Class<T> cls)
    {
    	PendingIntent alarmSender = PendingIntent.getService(context,
                0, new Intent(context, cls), 0);
    	
    	AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
    	am.cancel(alarmSender);
    }
    
    public static <T extends SyncService> void updateSchedule(Context context, Class<T> cls, long startTime, long interval)
    {
    	if (context == null) {
    		throw new IllegalArgumentException("context");
    	}
    	
    	PendingIntent alarmSender = PendingIntent.getService(context,
                0, new Intent(context, cls), 0);
    	
    	AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
    	am.setRepeating(AlarmManager.RTC_WAKEUP, startTime, interval, alarmSender);
    }
    
    // Hide the below static methods with an appropriate implementation 
    // in your derived SyncService class
    public static boolean isLoggedIn (Context context)
    {
    	return false;
    }
    
    public static Class<?> getLoginClass()
    {
    	return null;
    }
    
    public static String getSocialNetworkName()
    {
    	return "Default";
    }
}
