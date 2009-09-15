//
//    GlobalConfig.java is part of SyncMyPix
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

import com.nloko.android.Utils;
import com.nloko.android.syncmypix.facebook.FacebookSyncService;
import com.nloko.android.syncmypix.facebook.FacebookLoginWebView;
import com.nloko.android.syncmypix.R;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;

public class GlobalConfig extends PreferenceActivity {
	
	private static final String TAG = "GlobalConfig";
	
	public static final String PREFS_NAME = "SyncMyPixPrefs";
	
	// TODO move this elsewhere if we're going to add more social networks
	public static final String API_KEY = "d03f3dcb1ebb264e1ea701bd16f44e5a";
	
	private static boolean googleSyncing = false;
	public static boolean isGoogleSyncing()
	{
		return googleSyncing;
	}
	
	public static void setGoogleSyncing(boolean value)
	{
		googleSyncing = value;
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
    	//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        super.onCreate(savedInstanceState);
        
        setupViews(savedInstanceState);
    }
    
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		setupViews(null);
	}

    private void setupViews(Bundle savedInstanceState)
    {
        
    	getWindow().setBackgroundDrawableResource(R.drawable.background);
    	getListView().setBackgroundColor(Color.TRANSPARENT);
    	getListView().setCacheColorHint(Color.TRANSPARENT);

    	addPreferencesFromResource(R.layout.preferences);	
    	//getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.syncmypix_title);
    	
    	ListPreference schedule = (ListPreference) findPreference("sched_freq");
        schedule.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			public boolean onPreferenceChange(Preference preference,
					Object newValue) {

				int position = Integer.parseInt((String)newValue);
				
				long firstTriggerTime;
				long interval;
				
				Utils.setInt(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "sched_freq", position);
				
				if ((interval = GlobalConfig.getScheduleInterval(position)) > 0) {
					firstTriggerTime = System.currentTimeMillis() + interval;
					Utils.setLong(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "sched_time", firstTriggerTime);
					FacebookSyncService.updateSchedule(GlobalConfig.this, firstTriggerTime, interval);
				}
				else {
					FacebookSyncService.cancelSchedule(GlobalConfig.this);
				}
				return true;
			}
        	
        });

        if (savedInstanceState != null && savedInstanceState.containsKey("DIALOG")) {
        	showDialog(savedInstanceState.getInt("DIALOG"));
        }
        else {
            showDialog(ABOUT_DIALOG);
        }
    
        if (isLoggedIn()) {
        	setLoginStatus(R.string.loginStatus_loggedin);
        }
    }

    public static long getScheduleInterval(int pos)
    {
    	long interval;
    	switch (pos) {
    	
    		case 1:
				interval = AlarmManager.INTERVAL_DAY;
				break;
			case 2:
				interval = AlarmManager.INTERVAL_DAY * 7;
				break;
			case 3:
				interval = AlarmManager.INTERVAL_DAY * 30;
				break;
			default:
				interval = -1;
    	}
    	
    	return interval;
    }
    
	private void login()
    {
		startActivity(new Intent(GlobalConfig.this, FacebookLoginWebView.class));
    }
    
    // TODO Should probably kill social network API session too
    private void logout()
    {
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "session_key", null);
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "secret", null);
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "uid", null);
    	
		setLoginStatus(R.string.loginStatus_notloggedin);
    }  
    
    private void sync()
    {
    	if (!hashServiceConnected) {
    		//bindService(new Intent(GlobalConfig.this, HashUpdateService.class), hashServiceConn, 0);
    	}
    	
    	boolean hashServiceExecuting = hashService != null && hashService.isExecuting();
    	
/*    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		boolean skipIfExists = settings.getBoolean("skipIfExists", false);
		
    	if (skipIfExists && (isGoogleSyncing() || hashServiceExecuting)) {
    		Toast.makeText(this, "SyncMyPix disabled while Android is performing Google synchronization. Please try again momentarily.", Toast.LENGTH_LONG).show();
    		return;
    	}*/
    	
   		showDialog(FRIENDS_PROGRESS);
    	
    	Intent i = new Intent(GlobalConfig.this, FacebookSyncService.class);
   		startService(i);
    	bindService(i, syncServiceConn, Context.BIND_AUTO_CREATE);
    }
    
    private void showResults()
    {
    	Intent i = new Intent(this, SyncResults.class);
    	startActivity(i);
    }
    
    private void setLoginStatus(int status)
    {
   		EditTextPreference loginStatus = (EditTextPreference) findPreference("loginStatus");
   		loginStatus.setSummary(status);
    }
    
	private boolean isLoggedIn ()
    {
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		String session_key = settings.getString("session_key", null);
		String secret = settings.getString("secret", null);
		String uid = settings.getString("uid", null);
	
		return session_key != null && secret != null && uid != null;
    }

	private void hideDialogs(boolean remove)
	{
		if (!remove) {
			if (progress != null) {
				progress.setProgress(0);
				dismissDialog(SYNC_PROGRESS);
			}
			if (friendsProgress != null) {
				dismissDialog(FRIENDS_PROGRESS);
			}
		}
		else {
			if (progress != null) {
				removeDialog(SYNC_PROGRESS);
				progress = null;
			}
			if (friendsProgress != null) {
				removeDialog(FRIENDS_PROGRESS);
				friendsProgress = null;
			}
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (isLoggedIn()) {
			setLoginStatus(R.string.loginStatus_loggedin);
		}
		
		if (!syncServiceConnected) {
			Intent i = new Intent(GlobalConfig.this, FacebookSyncService.class);
			bindService(i, syncServiceConn, 0);
		}
		
		if (syncService != null && !syncService.isExecuting()) {
			hideDialogs(true);
		}
	}
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		
		unbindService(syncServiceConn);
		//unbindService(hashServiceConn);
		
		ThumbnailCache.destroy();
	}
    
    
    @Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		if (progress != null && progress.isShowing()) {
			outState.putInt("DIALOG", SYNC_PROGRESS);
		}
		else if (friendsProgress != null && friendsProgress.isShowing()) {
			outState.putInt("DIALOG", FRIENDS_PROGRESS);
		}
	}


	private final int MENU_LOGIN = 0;
    private final int MENU_SYNC = 1;
    private final int MENU_RESULTS = 2;
    private final int MENU_LOGOUT = 3;
    private final int MENU_ABOUT = 4;
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	MenuItem item;
    	item = menu.add(0, MENU_LOGIN, 0, "Login");
    	item.setIcon(android.R.drawable.ic_menu_myplaces);
    	
    	item = menu.add(0, MENU_SYNC, 0, "Sync");
    	item.setIcon(android.R.drawable.ic_menu_gallery);
    	
    	item = menu.add(0, MENU_RESULTS, 0, "Results");
    	item.setIcon(android.R.drawable.ic_menu_info_details);
    	
    	item = menu.add(0, MENU_LOGOUT, 0, "Logout");
    	item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    	
    	item = menu.add(0, MENU_ABOUT, 0, "About");
    	item.setIcon(android.R.drawable.ic_menu_help);
    	
    	return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_LOGIN:
				login();
				return true;
			case MENU_SYNC:
				sync();
				return true;
			case MENU_RESULTS:
				showResults();
				return true;
			case MENU_LOGOUT:
				logout();
				return true;
			case MENU_ABOUT:
				showDialog(ABOUT_DIALOG);
				return true;
	    }
		
	    return false;
	}

	private final int SYNC_PROGRESS = 0;
	private final int FRIENDS_PROGRESS = 1;
	private final int ABOUT_DIALOG = 2;
	private ProgressDialog progress;
	private ProgressDialog friendsProgress;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case SYNC_PROGRESS:
				progress = new ProgressDialog(GlobalConfig.this);
				progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progress.setMessage("Syncing contacts...");
				progress.setCancelable(false);
				progress.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						
						if (syncService != null && syncService.isExecuting()) {
							syncService.cancelOperation();
						}
					}
				});
				
				return progress;
			case FRIENDS_PROGRESS:
				friendsProgress = new ProgressDialog(GlobalConfig.this);
				friendsProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				friendsProgress.setMessage("Getting friends from social network...");
				friendsProgress.setCancelable(false);
				return friendsProgress;
				
			case ABOUT_DIALOG:
				return createAboutDialog();
		}
		
		return super.onCreateDialog(id);
	}

	private Dialog createAboutDialog()
	{
		Dialog about = new Dialog(this);
		about.requestWindowFeature(Window.FEATURE_NO_TITLE);
		about.setContentView(R.layout.about);
		
		Button ok = (Button)about.findViewById(R.id.ok);
		ok.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				removeDialog(ABOUT_DIALOG);
			}
			
		});
		
		return about;
	}
	
	private HashUpdateService hashService;
	private boolean hashServiceConnected = false;
	private ServiceConnection hashServiceConn = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder service) {
			hashService = ((HashUpdateService.LocalBinder) service).getService();
			hashServiceConnected = true;
		}

		public void onServiceDisconnected(ComponentName name) {
			hashServiceConnected = false;
			hashService = null;
		}
		
	};
	
	private FacebookSyncService syncService;
	private boolean syncServiceConnected = false;
	
	
	
    private ServiceConnection syncServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	
        	syncServiceConnected = true;
        	
        	syncService = ((FacebookSyncService.LocalBinder)service).getService();
        	syncService.setListener(new SyncServiceListener () {

				public void updateUI(int percentage, int index, int total) {
	
					if (friendsProgress != null && friendsProgress.isShowing()) {
						hideDialogs(true);
					}
					
					// catch and ignore stupid "Is activity running?" exception
					try {
						showDialog(SYNC_PROGRESS);
					}
					catch (Exception ex) {}
					
					if (progress != null) {
						if (percentage < 100) {
							progress.setMax(total);
							progress.setProgress(index);
						}
						else if (progress.isShowing()) {
					    	hideDialogs(true);
						}
					}
				}
				
				public void error (int id) {
					hideDialogs(false);
				}
            });

        }

        public void onServiceDisconnected(ComponentName className) {
            
        	syncServiceConnected = false;
        	syncService.unsetListener();
        	syncService = null;
        	
        	hideDialogs(true);
        }
    };
}