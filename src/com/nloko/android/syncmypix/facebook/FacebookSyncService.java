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

import java.lang.ref.WeakReference;
import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.SyncService;
import com.nloko.android.syncmypix.SettingsActivity;
import com.nloko.android.syncmypix.SocialNetworkUser;
import com.nloko.android.syncmypix.R;
import com.nloko.android.syncmypix.SyncServiceListener;
import com.nloko.simplyfacebook.net.FacebookRestClient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Message;

public class FacebookSyncService extends SyncService {
    
	private final static String TAG = "FacebookSyncService";
	private FacebookLogin mLoginThread;
	
	private static class FacebookLogin extends Thread
	{
		private WeakReference<FacebookSyncService> mService;
		private boolean running = true;
		
		public FacebookLogin(FacebookSyncService service)
		{
			mService = new WeakReference<FacebookSyncService>(service);
			
			SyncServiceListener listener = service.mListener;
			if (listener != null) {
				listener.onFriendsDownloadStarted();
			}
		}
		
		public void stopRunning()
		{
			synchronized(this) {
				running = false;
			}
		}
		
		public void run()
		{
			FacebookSyncService service = mService.get();
			if (service == null) {
				return;
			}
			MainHandler handler = service.mMainHandler;
			if (handler == null) {
				return;
			}
			
			FacebookRestClient client = null;
			Log.d(TAG, service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("uid", null));
			Log.d(TAG, service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("session_key", null));
			Log.d(TAG, service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("secret", null));
			
			try {
				client = new FacebookRestClient(FacebookApi.API_KEY, 
							service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("uid", null),
							service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("session_key", null),
							service.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).getString("secret", null));
				
				FacebookApi api = new FacebookApi (client);
				List<SocialNetworkUser> userList = api.getUserInfo(api.getFriends(), service.mMaxQuality);
				synchronized(this) {
					if (running) {
						// start sync from main thread
						Message msg = handler.obtainMessage();
						msg.what = MainHandler.START_SYNC;
						msg.obj = userList;
						handler.sendMessage(msg);
					}
				}
			} catch(Exception ex) {
				Log.e(TAG, android.util.Log.getStackTraceString(ex));
				if (handler != null) {
					if (client == null) {
						handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
								R.string.syncservice_sessionerror, 
								0));
	
					} else {
						handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
								R.string.syncservice_downloaderror, 
								0));
					}
					
					handler.post(service.mMainHandler.resetExecuting);
				}
			}
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}
	
    @Override
	public void onDestroy() {
		super.onDestroy();
		if (mLoginThread != null) {
			mLoginThread.stopRunning();
		}
	}

	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
		
		Log.d(TAG, "Staring " + TAG);
		
		mLoginThread = new FacebookLogin(this);
		mLoginThread.start();
	}

    // the below methods hide the corresponding ones from SyncService
	public static boolean isLoggedIn (Context context)
    {
    	SharedPreferences settings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
		String session_key = settings.getString("session_key", null);
		String secret = settings.getString("secret", null);
		String uid = settings.getString("uid", null);
	
		return session_key != null && secret != null && uid != null;
    }
	
    public static Class<?> getLoginClass()
    {
    	return FacebookLoginWebView.class;
    }
    
    public String getSocialNetworkName()
    {
    	return "Facebook";
    }
 }
