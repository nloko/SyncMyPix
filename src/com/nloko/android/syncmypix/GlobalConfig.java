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
import com.nloko.android.syncmypix.facebook.FacebookDownloadService;
import com.nloko.android.syncmypix.facebook.FacebookLoginWebView;
import com.nloko.android.syncmypix.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

public class GlobalConfig extends Activity {
	
	private static final String TAG = "GlobalConfig";
	
	public static final String PREFS_NAME = "SyncMyPixPrefs";
	
	// TODO move this elsewhere if we're going to add more social networks
	public static final String API_KEY = "d03f3dcb1ebb264e1ea701bd16f44e5a";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);	
        
        Spinner s = (Spinner) findViewById(R.id.source);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(
                this, R.array.sources, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);

        final CheckBox skipIfExists = (CheckBox) findViewById(R.id.skipIfExists);
        skipIfExists.setChecked(getSharedPreferences(PREFS_NAME, 0).getBoolean("skipIfExists", true));
        
        skipIfExists.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Utils.setBoolean (getSharedPreferences(PREFS_NAME, 0), "skipIfExists", skipIfExists.isChecked());
            }
        });

        final CheckBox skipIfConflict = (CheckBox) findViewById(R.id.skipIfConflict);
        skipIfConflict.setChecked(getSharedPreferences(PREFS_NAME, 0).getBoolean("skipIfConflict", false));
        
        skipIfConflict.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	Utils.setBoolean (getSharedPreferences(PREFS_NAME, 0), "skipIfConflict", skipIfConflict.isChecked());
            }
        });
        
        if (savedInstanceState != null) {
        	showDialog(savedInstanceState.getInt("DIALOG"));
        }
    }
    
    private void login()
    {
    	Intent i = new Intent(GlobalConfig.this, FacebookLoginWebView.class);
		startActivity(i);
    }
    
    // TODO Should probably kill social network API session too
    private void logout()
    {
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "session_key", null);
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "secret", null);
    	Utils.setString(getSharedPreferences(GlobalConfig.PREFS_NAME, 0), "uid", null);
    	
		TextView textview = (TextView) findViewById(R.id.loginStatus);
		textview.setText(R.string.loginStatus_notloggedin);
    }
    
    private void sync()
    {
   		showDialog(FRIENDS_PROGRESS);
    	
    	Intent i = new Intent(GlobalConfig.this, FacebookDownloadService.class);

   		startService(i);
    	bindService(i, serviceConn, Context.BIND_AUTO_CREATE);
    	
    	NotificationManager notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    	notifyManager.cancel(R.string.facebookdownloadservice_stopped);
    }
    
    private void showResults()
    {
    	Intent i = new Intent(this, SyncResults.class);
    	startActivity(i);
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
		
		if (isLoggedIn ()) {
			TextView textview = (TextView) findViewById(R.id.loginStatus);
			textview.setText(R.string.loginStatus_loggedin);
		}
		
		if (!serviceConnected) {
			Intent i = new Intent(GlobalConfig.this, FacebookDownloadService.class);
			bindService(i, serviceConn, 0);
		}
		
		if (boundService != null && !boundService.isExecuting()) {
			hideDialogs(true);
		}
	}
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		
		unbindService(serviceConn);
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
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_LOGIN, 0, "Login");
    	menu.add(0, MENU_SYNC, 0, "Sync");
    	menu.add(0, MENU_RESULTS, 0, "Results");
    	menu.add(0, MENU_LOGOUT, 0, "Logout");
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
	    }
		
	    return false;
	}

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
						
						if (boundService != null && boundService.isExecuting()) {
							boundService.cancelOperation();
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
		}
		
		return super.onCreateDialog(id);
	}

	private FacebookDownloadService boundService;
	private boolean serviceConnected = false;
	
	private final int SYNC_PROGRESS = 0;
	private final int FRIENDS_PROGRESS = 1;
	private ProgressDialog progress;
	private ProgressDialog friendsProgress;
	
    private ServiceConnection serviceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	
        	serviceConnected = true;
        	
        	boundService = ((FacebookDownloadService.LocalBinder)service).getService();
        	boundService.setListener(new DownloadListener () {

				public void updateUI(int percentage, int index, int total) {
	
					if (friendsProgress != null && friendsProgress.isShowing()) {
						hideDialogs(true);
						showDialog(SYNC_PROGRESS);
					}
					
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
            
        	serviceConnected = false;
        	boundService.unsetListener();
        	
        	hideDialogs(true);
        }
    };
}