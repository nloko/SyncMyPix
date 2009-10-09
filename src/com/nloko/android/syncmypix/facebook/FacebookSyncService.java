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

import java.util.List;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.SyncService;
import com.nloko.android.syncmypix.GlobalPreferences;
import com.nloko.android.syncmypix.SocialNetworkUser;
import com.nloko.android.syncmypix.R;
import com.nloko.simplyfacebook.net.FacebookRestClient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Message;

public class FacebookSyncService extends SyncService {
    
	private final String TAG = "FacebookSyncService";
	
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
				client = new FacebookRestClient(FacebookApi.API_KEY, 
							getSharedPreferences(GlobalPreferences.PREFS_NAME, 0).getString("uid", null),
							getSharedPreferences(GlobalPreferences.PREFS_NAME, 0).getString("session_key", null),
							getSharedPreferences(GlobalPreferences.PREFS_NAME, 0).getString("secret", null));
				
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
					handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
							R.string.syncservice_sessionerror, 
							0));

				}
				else {
					handler.sendMessage(handler.obtainMessage(MainHandler.SHOW_ERROR, 
							R.string.syncservice_downloaderror, 
							0));
				}
				
				handler.post(handler.resetExecuting);
			}
		}
	}
	
	
    @Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
		
		Log.d(TAG, "Staring " + TAG);
		
		new FacebookLogin(getMainHandler()).start();
	}

    // the below methods hide the corresponding ones from SyncService
	public static boolean isLoggedIn (Context context)
    {
    	SharedPreferences settings = context.getSharedPreferences(GlobalPreferences.PREFS_NAME, 0);
		String session_key = settings.getString("session_key", null);
		String secret = settings.getString("secret", null);
		String uid = settings.getString("uid", null);
	
		return session_key != null && secret != null && uid != null;
    }
	
    public static Class<?> getLoginClass()
    {
    	return FacebookLoginWebView.class;
    }
    
    public static String getSocialNetworkName()
    {
    	return "Facebook";
    }
 }
