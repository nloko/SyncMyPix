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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.NameMatcher.PhoneContact;
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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Contacts.People;
import android.widget.Toast;

public abstract class SyncService extends Service {

	private final static String TAG = "SyncService";
	public final static Object mSyncLock = new Object();
	
	private SyncServiceStatus mStatus = SyncServiceStatus.IDLE;
	private SyncTask mSyncOperation;
	private NotificationManager mNotifyManager;
	private WakeLock mWakeLock;
    
	private boolean mCancel = false;
    private boolean mExecuting = false;
    private boolean mStarted = false;
	
    protected boolean mSkipIfExists;
    protected boolean mSkipIfConflict;
    protected boolean mMaxQuality;
    protected boolean mCropSquare;
    protected boolean mIntelliMatch;
    protected SyncServiceListener mListener;
	
	private final MainHandler mMainHandler = new MainHandler(this);
	private final IBinder mBinder = new LocalBinder();
	private final List <ContentValues> mResultsList = new ArrayList<ContentValues> ();
	
	public enum SyncServiceStatus {
		IDLE,
		GETTING_FRIENDS,
		SYNCING
	}
	
	public SyncServiceStatus getStatus()
	{
		return mStatus;
	}
	
	protected void updateStatus(SyncServiceStatus status)
	{
		mStatus = status;
	}
	
	public MainHandler getMainHandler()
	{
		return mMainHandler;
	}
	
	protected static class MainHandler extends Handler
	{
		private final WeakReference<SyncService> mSyncService;
		public static final int START_SYNC = 0;
		public static final int SHOW_ERROR = 1;
		
		MainHandler(SyncService service) 
		{
			mSyncService = new WeakReference<SyncService>(service);
		}
		
		public final Runnable resetExecuting = new Runnable () {
			public void run() {
				final SyncService service = mSyncService.get();
				if (service != null) {
					service.mExecuting = false;
					service.updateStatus(SyncServiceStatus.IDLE);
				}
			}
		};
		
		public final Runnable finish = new Runnable () {
			public void run() {
				final SyncService service = mSyncService.get();
				if (service != null) {
					service.mResultsList.clear();
					service.mWakeLock.release();
					service.stopSelf();
				}
			}
		};
		
		public void handleError(int msg)
		{
			final SyncService service = mSyncService.get();
			if (service != null) {
				post(resetExecuting);
			
				if (service.mListener != null) {
					service.mListener.onError(msg);
				}
			
				service.showError(msg);
			}
		}
		
		@SuppressWarnings("unchecked")
		public void startSync(List<SocialNetworkUser> users)
		{
			final SyncService service = mSyncService.get();
			if (service != null) {
				service.updateStatus(SyncServiceStatus.SYNCING);
				service.mSyncOperation = new SyncTask(service);
				service.mSyncOperation.execute(users);
			}
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
    public void setListener (SyncServiceListener listener)
    {
    	if (listener == null) {
    		throw new IllegalArgumentException ("listener");
    	}
    	
    	mListener = listener;
    }
    
    public void unsetListener()
    {
    	mListener = null;
    }
    
    public SyncServiceListener getListener()
    {
    	return mListener;
    }

    private static class UpdateResultsTable extends Thread
    {
    	final private List<ContentValues> list;
    	final private WeakReference<SyncService> mService;
    	
    	public UpdateResultsTable(SyncService service, List<ContentValues> list)
    	{
    		mService = new WeakReference<SyncService>(service);
    		this.list = list;
    	}
    	
    	private void createResult (ContentValues values)
        {
        	if (values == null) {
        		throw new IllegalArgumentException("values");
        	}
        	final SyncService service = mService.get();
			if (service != null) {
				ContentResolver resolver = service.getContentResolver();
				resolver.insert(Results.CONTENT_URI, values);
			}
        }
     
		public void run() {
			
			Log.d(TAG, "mStarted updating results at " + Long.toString(System.currentTimeMillis()));
			for (ContentValues values : list) {
				if (values != null) {
					createResult(values);
				}
			}
			
			list.clear();
			Log.d(TAG, "Finished updating results at " + Long.toString(System.currentTimeMillis()));
			
			final SyncService service = mService.get();
			if (service != null) {
				service.mMainHandler.post(service.mMainHandler.finish);
			}
		}
    }
    
    private static class SyncTask extends AsyncTask <List<SocialNetworkUser>, Integer, Long>
    {
    	private final WeakReference<SyncService> mService;
    	private final SyncMyPixDbHelper dbHelper;
    	    	    	
    	public SyncTask (SyncService service)
    	{
    		mService = new WeakReference<SyncService>(service);
    		dbHelper = new SyncMyPixDbHelper(mService.get().getApplicationContext());
    	}
    	
        private void processUser(final SocialNetworkUser user, String contactId, Uri sync) 
        {
    		if (user == null) {
    			throw new IllegalArgumentException ("user");
    		}
    		
    		if (sync == null) {
    			throw new IllegalArgumentException ("sync");
    		}
    		
    		final SyncService service = mService.get();
    		if (service == null) {
    			return;
    		}
    		
    		Log.d(TAG, String.format("%s %s", user.name, user.picUrl));
    		
    		final String syncId = sync.getPathSegments().get(1);
    		ContentValues values = createResult(syncId, user.name, user.picUrl);
    		
    		if (user.picUrl == null) {
    			values.put(Results.DESCRIPTION, ResultsDescription.PICNOTFOUND.getDescription(service));
    			service.mResultsList.add(values);
    			return;
    		}
    		
    		if (contactId == null) {
    			Log.d(TAG, "Contact not found in database.");
    			values.put(Results.DESCRIPTION, ResultsDescription.NOTFOUND.getDescription(service));
    			service.mResultsList.add(values);
    			return;
    		}
    		
    		InputStream is = null;
    		Bitmap bitmap = null, originalBitmap = null;
    		byte[] image = null;

    		String contactHash = null;
    		String hash = null;

    		//ContentValues is an immutable object
    		final ContentValues valuesCopy = new ContentValues(values);

    		try {
    			//String id = cur.getString(cur.getColumnIndex(People._ID));
    			DBHashes hashes = dbHelper.getHashes(contactId);

    			Uri contact = Uri.withAppendedPath(People.CONTENT_URI, contactId);
    			is = People.openContactPhotoInputStream(service.getContentResolver(), contact);
    			// photo is set, so let's get its hash
    			if (is != null) {
    				contactHash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
    			}

    			if (dbHelper.isSyncablePicture(contactId, hashes.updatedHash, contactHash, service.mSkipIfExists)) {

    				if (image == null) {
    					try {
    						bitmap = Utils.downloadPictureAsBitmap(user.picUrl);
    						originalBitmap = bitmap;
    						image = Utils.bitmapToJpeg(bitmap, 100);
    						hash = Utils.getMd5Hash(image);
    					}
    					catch (Exception e) {}
    				}

    				if (image != null) {
    					// picture is a new one and we should sync it
    					if ((hash != null && !hash.equals(hashes.networkHash)) || is == null) {

    						String updatedHash = hash;

    						if (service.mCropSquare) {
    							bitmap = Utils.centerCrop(bitmap, 96, 96);
    							image = Utils.bitmapToJpeg(bitmap, 100);
    							updatedHash = Utils.getMd5Hash(image);
    						}

    						ContactServices.updateContactPhoto(service.getContentResolver(), image, contactId);
    						dbHelper.updateHashes(contactId, hash, updatedHash);
    					}
    					else {
    						valuesCopy.put(Results.DESCRIPTION, 
    								ResultsDescription.SKIPPED_UNCHANGED.getDescription(service));
    					}

    					// send picture to listener for progress display
						final Bitmap tmp = originalBitmap;
						service.mMainHandler.post(new Runnable() {
							public void run() {
								if (service.mListener != null) {
	    							service.mListener.onContactSynced(user.name, tmp, valuesCopy.getAsString(Results.DESCRIPTION));
	    						}
							}
						});
						
    					valuesCopy.put(Results.CONTACT_ID, contactId);
    				}
    				else {
    					valuesCopy.put(Results.DESCRIPTION, 
    							ResultsDescription.DOWNLOAD_FAILED.getDescription(service));
    					//break;
    				}
    			}
    			else {
    				valuesCopy.put(Results.DESCRIPTION, 
    						ResultsDescription.SKIPPED_EXISTS.getDescription(service));
    			}

    		}
    		catch (Exception e) {
    			valuesCopy.put(Results.DESCRIPTION, ResultsDescription.ERROR.getDescription(service));
    		}
    		finally {
    			service.mResultsList.add(valuesCopy);
    		}
    	}

        private ContentValues createResult(String id, String name, String url)
        {
        	ContentValues values = new ContentValues();
    		values.put(Results.SYNC_ID, id);
    		values.put(Results.NAME, name);
    		values.put(Results.PIC_URL, url);
    		values.put(Results.DESCRIPTION, ResultsDescription.UPDATED.getDescription(mService.get()));
    		
    		return values;
        }
        
		@Override
		protected Long doInBackground(List<SocialNetworkUser>... users) {
			
			long total = 0;
			int index = 0, size = 0;
			
			List<SocialNetworkUser> userList = users[0];
			NameMatcher matcher = null;
			
    		final SyncService service = mService.get();
    		if (service == null) {
    			return 0l;
    		}
    		
			synchronized(mSyncLock) {
				try {
					matcher = new NameMatcher(service.getApplicationContext(), service.getResources().openRawResource(R.raw.diminutives));
					
					// clear previous results, if any
					service.getContentResolver().delete(Sync.CONTENT_URI, null, null);
					Uri sync = service.getContentResolver().insert(Sync.CONTENT_URI, null);
	
					index = 1;
					size = userList.size();
					
					for(int i=size-1; i>=0; i--) {
						SocialNetworkUser user = userList.remove(i);
						PhoneContact contact = null;
						if (service.mIntelliMatch) {
							contact = matcher.match(user.name, true);
						}
						else {
							contact = matcher.exactMatch(user.name);
						}
						
						processUser(user, contact == null ? null : contact.id, sync);
						publishProgress((int) ((index++ / (float) size) * 100), index, size);
	
						if (service.mCancel) {
							service.mMainHandler.sendMessage(service.mMainHandler.obtainMessage(MainHandler.SHOW_ERROR, 
									R.string.syncservice_canceled, 
									0));
	
							break;
						}
					}
	
					ContentValues syncValues = new ContentValues();
					syncValues.put(Sync.DATE_COMPLETED, System.currentTimeMillis());
					service.getContentResolver().update(sync, syncValues, null, null);
					
					total = index;
				
				} catch (Exception ex) {
					Log.e(TAG, android.util.Log.getStackTraceString(ex));
					service.mMainHandler.sendMessage(service.mMainHandler.obtainMessage(MainHandler.SHOW_ERROR, 
							R.string.syncservice_fatalsyncerror, 
							0));
	
				} finally {
					if (matcher != null) {
						matcher.destroy();
					}
					if (userList != null) {
						userList.clear();
					}
					service.mMainHandler.post(service.mMainHandler.resetExecuting);
				}
			}
			
			return total;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			final SyncService service = mService.get();
    		if (service != null) {
    			if (service.mListener != null) {
    				service.mListener.onSyncProgressUpdated(values[0], values[1], values[2]);
    			}
    		}
		}

		@Override
		protected void onPostExecute(Long result) {
			final SyncService service = mService.get();
    		if (service == null) {
    			return;
    		}
    		
			if (result > 0 && !service.mCancel) {
				if (service.mListener != null) {
					service.mListener.onSyncCompleted();
				}
				
				Intent i = new Intent(service.getApplicationContext(), SyncResultsActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				service.cancelNotification(R.string.syncservice_started, R.string.syncservice_stopped);
				service.showNotification(R.string.syncservice_stopped, 
						android.R.drawable.stat_sys_download_done, 
						i,
						true);
			}
			else {
				service.cancelNotification(R.string.syncservice_started);
			}
			
			if (!service.mResultsList.isEmpty()) {
				new UpdateResultsTable(service, service.mResultsList).start();
			}
		}
    }

    public void cancelOperation()
    {
    	if (mExecuting) {
    		if (mListener != null) {
    			mListener.onSyncCancelled();
    		}
    		mCancel = true;
    	}
    }
    
    public boolean isExecuting() { 	return mExecuting; }
    public boolean ismStarted () { 	return mStarted; }
    
    private void getPreferences()
    {
		SyncMyPixPreferences prefs = new SyncMyPixPreferences(getApplicationContext());
		
		mSkipIfConflict = prefs.getSkipIfConflict();
		mMaxQuality = prefs.getMaxQuality();
		mCropSquare = prefs.getCropSquare();
    	mSkipIfExists = prefs.getSkipIfExists();
    	mIntelliMatch = prefs.getIntelliMatch();
    }
    
    @Override
	public void onCreate() {
		super.onCreate();
		
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncMyPix WakeLock");
	}

	@Override
	public void onStart(Intent intent, int startId) {
    	super.onStart(intent, startId);

    	// keep CPU alive until we're done
    	mWakeLock.acquire();
    	
		mExecuting = true;
		mStarted = true;
		mCancel = false;

		updateStatus(SyncServiceStatus.GETTING_FRIENDS);
		getPreferences();
		
		mNotifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mNotifyManager.cancel(R.string.syncservice_stopped);
		
		showNotification(R.string.syncservice_started, android.R.drawable.stat_sys_download);
	}

    @Override
    public void onDestroy() {

    	cancelNotification(R.string.syncservice_started);
        unsetListener();

        mStarted = false;
        // ensure this is released
    	if (mWakeLock != null && mWakeLock.isHeld()) {
    		mWakeLock.release();
    	}
        super.onDestroy();
    }

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}
	
    private void showNotification(int msg, int icon)
    {
    	showNotification(msg, icon, false);
    }
    
    private void showNotification(int msg, int icon, boolean autoCancel) 
    {
        // The PendingIntent to launch our activity if the user selects this notification
        Intent i = new Intent(this, SyncProgressActivity.class);
        //i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        showNotification(msg, icon, i, autoCancel);
    }
    
    private void showNotification(int msg, int icon, Intent i, boolean autoCancel) 
    {
    	CharSequence text = getText(msg);
        Notification notification = new Notification(icon, text,
                System.currentTimeMillis());
        
        if (autoCancel) {
        	notification.flags = Notification.FLAG_AUTO_CANCEL;
        }

   
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                i, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(msg),
                       text, contentIntent);

        mNotifyManager.notify(msg, notification);
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
    	if (mStarted) {
    		mNotifyManager.cancel(msg);
    	}

        if (toastMsg >= 0) {
        	Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

	// just access directly. No IPC crap to deal with.
    public class LocalBinder extends Binder {
    	public SyncService getService() {
            return SyncService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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
