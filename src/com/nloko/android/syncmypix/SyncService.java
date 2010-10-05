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

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.PhotoCache;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;
import com.nloko.android.syncmypix.SyncMyPixDbHelper.DBHashes;
import com.nloko.android.syncmypix.contactutils.ContactUtils;
import com.nloko.android.syncmypix.namematcher.NameMatcher;
import com.nloko.android.syncmypix.namematcher.NameMatcherFactory;

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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.Toast;

public abstract class SyncService extends Service {

	private final static String TAG = "SyncService";
	public final static Object mSyncLock = new Object();
	
	public final static int IDLE = 0;
	public final static int GETTING_FRIENDS = 1;
	public final static int SYNCING = 2;
	//private SyncServiceStatus mStatus = SyncServiceStatus.IDLE;
	private int mStatus = IDLE;
	
	private SyncTask mSyncOperation;
	private NotificationManager mNotifyManager;
	    
	private boolean mCancel = false;
    private boolean mExecuting = false;
    private boolean mStarted = false;
	
    protected boolean mAllowGoogleSync;
    protected boolean mSkipIfExists;
    protected boolean mSkipIfConflict;
    protected boolean mOverrideReadOnlyCheck;
    protected boolean mMaxQuality;
    protected boolean mCropSquare;
    protected boolean mIntelliMatch;
    protected boolean mPhoneOnly;
    protected boolean mCacheOn;
    protected SyncServiceListener mListener;
	protected final MainHandler mMainHandler = new MainHandler(this);

	private final IBinder mBinder = new LocalBinder(this);
	private final List <ContentValues> mResultsList = new ArrayList<ContentValues> ();
	
	private final int RESULTS_THRESH = 100;
	
	public enum SyncServiceStatus {
		IDLE,
		GETTING_FRIENDS,
		SYNCING
	}
	
	public int getStatus()
	{
		return mStatus;
	}
	
	protected void updateStatus(int status)
	{
		mStatus = status;
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
					service.updateStatus(IDLE);
				}
			}
		};
		
		public final Runnable finish = new Runnable () {
			public void run() {
				final SyncService service = mSyncService.get();
				if (service != null) {
					SyncServiceListener listener = service.mListener;
					if (listener != null) {
						listener.onSyncCompleted();
					}
					service.mResultsList.clear();
					SyncWakeLock.releaseWakeLock();
					service.stopSelf();
				}
			}
		};
		
		public void handleError(int msg)
		{
			final SyncService service = mSyncService.get();
			if (service != null) {
				post(resetExecuting);

				SyncServiceListener listener = service.mListener;
				if (listener != null) {
					listener.onError(msg);
				}
			
				service.showError(msg);
			}
		}
		
		@SuppressWarnings("unchecked")
		public void startSync(List<SocialNetworkUser> users)
		{
			final SyncService service = mSyncService.get();
			if (service != null) {
				service.updateStatus(SYNCING);
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
    
    private static class UpdateResultsTable extends Thread
    {
    	final private List<ContentValues> list;
    	final private WeakReference<SyncService> mService;
    	final private boolean finish;
    	
    	public UpdateResultsTable(SyncService service, List<ContentValues> list)
    	{
    		this (service, list, true);
    	}
    	
    	public UpdateResultsTable(SyncService service, List<ContentValues> list, boolean finish)
    	{
    		mService = new WeakReference<SyncService>(service);
    		this.list = list;
    		this.finish = finish;
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
			synchronized (list) {
				//for (int i=0;i<6;i++) {
				for (ContentValues values : list) {
					if (values != null) {
						createResult(values);
					}
				}
				//}
				list.clear();
			}
			
			Log.d(TAG, "Finished updating results at " + Long.toString(System.currentTimeMillis()));
			
			if (finish) {
				final SyncService service = mService.get();
				if (service != null) {
					service.mMainHandler.post(service.mMainHandler.finish);
				}
			}
		}
    }
    
    private static class SyncTask extends AsyncTask <List<SocialNetworkUser>, Integer, Long>
    {
    	private final WeakReference<SyncService> mService;
    	private final SyncMyPixDbHelper dbHelper;
    	private final ContactUtils mContactUtils;
    	private final PhotoCache mCache;
    	
    	private int mUpdated = 0;
    	private int mSkipped = 0;
    	private int mNotFound = 0;
    	    	    	
    	public SyncTask (SyncService service)
    	{
    		mContactUtils = new ContactUtils();
    		
    		mCache = new PhotoCache(service.getApplicationContext());
    		mCache.setDeleteOrder(PhotoCache.DELETE_NEWEST);
    		
    		mService = new WeakReference<SyncService>(service);
    		dbHelper = new SyncMyPixDbHelper(mService.get().getApplicationContext());
    		
    		// if the last sync op allowed sync with Google, but the current sync op doesn't
    		// picture tracking hashs need to be cleared, as the hashes between the phone pics and 
    		// social network pics will not match up
    		boolean lastSyncedWithGoogle = service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getBoolean("last_googlesync", false);
    		if (service.mAllowGoogleSync) {
				if (!lastSyncedWithGoogle) {
					Log.d(TAG, "Resetting hashes...");
					dbHelper.resetHashes(service.getSocialNetworkName(), true, false);
				}
				Utils.setBoolean(service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "last_googlesync", true);
			} else {
				if (lastSyncedWithGoogle) {
					Log.d(TAG, "Resetting hashes...");
					dbHelper.resetHashes(service.getSocialNetworkName());
				}
				Utils.setBoolean(service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0), "last_googlesync", false);
			}
    	}
    	
        private void processUser(final SocialNetworkUser user, PhoneContact contact, Uri sync) 
        {
    		if (user == null) {
    			throw new IllegalArgumentException ("user");
    		} else 	if (sync == null) {
    			throw new IllegalArgumentException ("sync");
    		}
    		
    		final SyncService service = mService.get();
    		if (service == null) {
    			return;
    		}
    		final ContentResolver resolver = service.getContentResolver();
    		if (resolver == null) {
    			return;
    		}
    		
    		Log.i(TAG, String.format("%s %s %s", user.name, user.email, user.picUrl));
    		
    		final String syncId = sync.getPathSegments().get(1);
    		ContentValues values = createResult(syncId, user);
    		
    		if (user.picUrl == null) {
    			mNotFound++;
    			values.put(Results.DESCRIPTION, service.getString(R.string.resultsdescription_picnotfound));
    			addResult(values);
    			return;
    		}
    		
    		// For Android < 2.0, aggregatedId is always the same contactId
			String contactId = null;
			String lookup = null;
			String aggregatedId = null;
			String name = null;

			// For Android 2.x, need to ensure the contact id has not changed
			if (contact != null) {
				contactId = aggregatedId = contact.id;
				name = contact.name;
				contact = mContactUtils.confirmContact(resolver, contact.id, contact.lookup);
				if (contact != null) {
					aggregatedId = contact.id;
					lookup = contact.lookup;
				}
			}
			
    		if (contact == null || (!mContactUtils.isContactUpdatable(resolver, aggregatedId) && !service.mOverrideReadOnlyCheck)) {
    			Log.d(TAG, "Contact not found in database.");
    			mNotFound++;
    			values.put(Results.DESCRIPTION, service.getString(R.string.resultsdescription_notfound));
    			addResult(values);
    			return;
    		}
    		
    		Log.i(TAG, String.format("Matched to %s with aggregated id %s and lookup %s", name, aggregatedId, lookup));
    		
    		InputStream is = null;
    		InputStream friend = null;
    		byte[] image = null;

    		String contactHash = null;
    		String hash = null;

    		//ContentValues is an immutable object
    		final ContentValues valuesCopy = new ContentValues(values);

    		try {
    			DBHashes hashes = dbHelper.getHashes(contactId);
    			is = mContactUtils.getPhoto(resolver, aggregatedId);
    			// photo is set, so let's get its hash
    			if (is != null) {
    				//Log.d(TAG, "CONTACT PIC IS NOT NULL!!");
    				contactHash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
    			}

    			if (dbHelper.isSyncablePicture(contactId, hashes.updatedHash, contactHash, service.mSkipIfExists)) {
   					try {
   						String filename =  Uri.parse(user.picUrl).getLastPathSegment();
   						friend = mCache.get(filename);
   						if (friend == null) {
   							Log.d(TAG, "cache miss");
   							friend = Utils.downloadPictureAsStream(user.picUrl, 2);
   						}
   						
   						image = Utils.getByteArrayFromInputStream(friend);
   						friend.close();
   						
   						if (service.mCacheOn) {
   							mCache.add(filename, image);
   						}
   						
   						hash = Utils.getMd5Hash(image);
   					} catch (Exception e) {
   						e.printStackTrace();
   					}

    				if (image != null) {
    					final Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
    					// picture is a new one and we should sync it
    					if ((hash != null && !hash.equals(hashes.networkHash)) || is == null) {
    						String updatedHash = hash;
    						
    						if (service.mCropSquare) {
    							image = Utils.bitmapToPNG(Utils.centerCrop(bitmap, 96, 96));
    							updatedHash = Utils.getMd5Hash(image);
    						}
    						mContactUtils.updatePhoto(resolver, image, aggregatedId, service.mAllowGoogleSync);
    						dbHelper.updateHashes(aggregatedId, lookup, hash, updatedHash);
    						dbHelper.updateLink(aggregatedId, lookup, user, service.getSocialNetworkName());
    						mUpdated++;
    					} else {
    						mSkipped++;
    						valuesCopy.put(Results.DESCRIPTION, 
    								service.getString(R.string.resultsdescription_skippedunchanged));
    					}
    					// send picture to listener for progress display
						
						MainHandler handler = service.mMainHandler;
						if (handler != null) {
							handler.post(new Runnable() {
								public void run() {
									SyncServiceListener listener = service.mListener;
									if (listener != null) {
		    							listener.onContactSynced(user.name, bitmap, valuesCopy.getAsString(Results.DESCRIPTION));
		    						}
								}
							});
						} else {
							// try to force GC
							bitmap.recycle();
						}
						
    					valuesCopy.put(Results.CONTACT_ID, aggregatedId);
    					valuesCopy.put(Results.LOOKUP_KEY, lookup);
    				} else {
    					valuesCopy.put(Results.DESCRIPTION, 
    							service.getString(R.string.resultsdescription_downloadfailed));
    					//break;
    				}
    			} else {
    				mSkipped++;
    				valuesCopy.put(Results.DESCRIPTION, 
    						service.getString(R.string.resultsdescription_skippedexists));
    			}
    		} catch (Exception e) {
    			valuesCopy.put(Results.DESCRIPTION, service.getString(R.string.resultsdescription_error));
    		} finally {
    			addResult(valuesCopy);
    			try {
	    			if (is != null) {
	    				is.close();
	    			}
    			} catch(IOException e) {}
    		}
    	}

        private void addResult (ContentValues value)
        {
        	final SyncService service = mService.get();
    		if (service != null) {
    			synchronized (service.mResultsList) {
    				service.mResultsList.add(value);
    			}
    		}
        }
        
        private ContentValues createResult(String id, SocialNetworkUser user)
        {
        	SyncService service = mService.get();
        	
        	ContentValues values = new ContentValues();
    		values.put(Results.SYNC_ID, id);
    		values.put(Results.NAME, user.name);
    		values.put(Results.PIC_URL, user.picUrl);
    		values.put(Results.FRIEND_ID, user.uid);
    		values.put(Results.DESCRIPTION, service == null ? "" : service.getString(R.string.resultsdescription_updated));
    		
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
    		final ContentResolver resolver = service.getContentResolver();
    		if (resolver == null) {
    			return 0l;
    		}
    		MainHandler handler = service.mMainHandler;
    		final String source = service.getSocialNetworkName();
    		
			synchronized(mSyncLock) {
				try {
					matcher = NameMatcherFactory.create(service.getApplicationContext(), 
							service.getResources().openRawResource(R.raw.diminutives),
							service.mPhoneOnly
						);
					
					//matcher.dump();
					
					// clear previous results, if any
					//mCache.deleteAll();
					dbHelper.deleteResults(source);
					ContentValues syncValues = new ContentValues();
					syncValues.put(Sync.SOURCE, source);
					Uri sync = resolver.insert(Sync.CONTENT_URI, syncValues);
	
					index = 1;
					size = userList.size();
					
					for(int i=size-1; i>=0; i--) {
						SocialNetworkUser user = userList.remove(i);
						
						PhoneContact linked = dbHelper.getLinkedContact(user.uid, source);
						PhoneContact contact = null;
						if (linked.id == null) {
							if (service.mIntelliMatch) {
								contact = matcher.match(user.name, true);
							} else {
								contact = matcher.exactMatch(user.name);
							}
							if (contact != null && dbHelper.hasLink(contact.id, service.getSocialNetworkName())) {
								contact = null;
							}
						} else {
							contact = linked;
						}
						
						processUser(user, contact, sync);
						publishProgress((int) ((index++ / (float) size) * 100), index, size);
	
						if (service.mCancel) {
							if (handler != null) {
								handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
										R.string.syncservice_canceled, 
										0));
							}
							break;
						} else if (service.mResultsList.size() == service.RESULTS_THRESH) {
							service.updateResults(false);
						}
					}
	
					syncValues.clear();
					syncValues.put(Sync.DATE_COMPLETED, System.currentTimeMillis());
					syncValues.put(Sync.UPDATED, mUpdated);
					syncValues.put(Sync.NOT_FOUND, mNotFound);
					syncValues.put(Sync.SKIPPED, mSkipped);
					resolver.update(sync, syncValues, null, null);
					
					total = index;
				
				} catch (Exception ex) {
					Log.e(TAG, android.util.Log.getStackTraceString(ex));
					handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
							R.string.syncservice_fatalsyncerror, 
							0));
	
				} finally {
					if (matcher != null) {
						matcher.destroy();
					}
					if (userList != null) {
						userList.clear();
					}
					if (mCache != null) {
						mCache.releaseResources();
					}
					handler.post(handler.resetExecuting);
				}
			}
			
			return total;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			final SyncService service = mService.get();
    		if (service != null) {
    			SyncServiceListener listener = service.mListener;
    			if (listener != null) {
    				listener.onSyncProgressUpdated(values[0], values[1], values[2]);
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
				Intent i = new Intent(service.getApplicationContext(), SyncResultsActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				service.cancelNotification(R.string.syncservice_started, R.string.syncservice_stopped);
				service.showNotification(R.string.syncservice_stopped, 
						android.R.drawable.stat_sys_download_done, 
						i,
						true);
			} else {
				service.cancelNotification(R.string.syncservice_started);
			}
			
			service.updateResults(true);
		}
    }

    public void cancelOperation()
    {
    	if (mExecuting) {
    		SyncServiceListener listener = mListener;
    		if (listener != null) {
    			listener.onSyncCancelled();
    		}
    		mCancel = true;
    	}
    }
    
    public boolean isExecuting() { 	return mExecuting; }
    public boolean isStarted () { 	return mStarted; }
    
    private void getPreferences()
    {
		SyncMyPixPreferences prefs = new SyncMyPixPreferences(getApplicationContext());
		
		mAllowGoogleSync = prefs.getAllowGoogleSync();
		mSkipIfConflict = prefs.getSkipIfConflict();
		mMaxQuality = prefs.getMaxQuality();
		mCropSquare = prefs.getCropSquare();
    	mSkipIfExists = prefs.getSkipIfExists();
    	mOverrideReadOnlyCheck = prefs.overrideReadOnlyCheck();
    	mIntelliMatch = prefs.getIntelliMatch();
    	mPhoneOnly = prefs.getPhoneOnly();
    	mCacheOn = prefs.getCache();
    	
    	Log.d(TAG, "PhoneOnly is " + mPhoneOnly);
    }
    
    private void updateResults(boolean finish)
    {
    	if (!mResultsList.isEmpty()) {
			new UpdateResultsTable(this, mResultsList, finish).start();
		}
    }
    
    @Override
	public void onLowMemory() {
		super.onLowMemory();
		// update results table and clear list
		updateResults(false);
		// remove notification in case kernel kills our process
		cancelNotification(R.string.syncservice_started);
	}

	@Override
	public void onStart(Intent intent, int startId) {
    	super.onStart(intent, startId);

    	// keep CPU alive until we're done
    	SyncWakeLock.acquireWakeLock(getApplicationContext());
    	
    	
		mExecuting = true;
		mStarted = true;
		mCancel = false;

		updateStatus(GETTING_FRIENDS);
		getPreferences();
		
		mNotifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mNotifyManager.cancel(R.string.syncservice_stopped);
		
		showNotification(R.string.syncservice_started, android.R.drawable.stat_sys_download);
	}

    @Override
    public void onDestroy() {
    	Log.d(TAG, "onDestroy");
    	cancelNotification(R.string.syncservice_started);
        unsetListener();

        mStarted = false;
        SyncWakeLock.releaseWakeLock();
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
        Intent i = new Intent(getApplicationContext(), SyncProgressActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        //i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
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
        	Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

	// just access directly. No IPC crap to deal with.
    public static class LocalBinder extends Binder {
    	private final WeakReference<SyncService> mService;
    	public LocalBinder(SyncService service)
    	{
    		mService = new WeakReference<SyncService>(service);
    	}
    	
    	public SyncService getService() {
    		return mService.get(); 
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
    	
    	//PendingIntent alarmSender = PendingIntent.getService(context,
        //        0, new Intent(context, cls), 0);
    	
    	PendingIntent alarmSender = PendingIntent.getBroadcast(context, 
    			0, 
    			new Intent(SyncMyPix.SYNC_INTENT), 
    			0);
    	AlarmManager am = (AlarmManager)context.getSystemService(ALARM_SERVICE);
    	am.setRepeating(AlarmManager.RTC_WAKEUP, startTime, interval, alarmSender);
    }
    
    // Hide/Override the below static methods with an appropriate implementation 
    // in your derived SyncService class
    public static boolean isLoggedIn (Context context)
    {
    	return false;
    }
    
    public static Class<?> getLoginClass()
    {
    	return null;
    }
    
    public String getSocialNetworkName()
    {
    	return "Default";
    }
}
