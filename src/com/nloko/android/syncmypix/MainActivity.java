//
//    MainActivity.java is part of SyncMyPix
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

import java.lang.reflect.Method;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.facebook.FacebookLoginWebView;
import com.nloko.android.syncmypix.facebook.FacebookSyncService;
import com.nloko.android.syncmypix.views.ConfirmSyncDialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	
	public static <T extends SyncService> Class<T> getSyncSource(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String source = prefs.getString("source", null);
		
		try {
			Class<?> cls = Class.forName(source);
			return (Class<T>) cls;
		}
		catch(ClassNotFoundException classException) {
			Log.e(TAG, "Could not get class from XML. Defaulting to FacebookSyncService.class");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return (Class<T>) FacebookSyncService.class;
	}
	
	public static <T extends SyncService> boolean isLoggedInFromSyncSource(Context context, Class<T> source)
	{
		try {
			Method m = source.getMethod("isLoggedIn", Context.class);
			return (Boolean) m.invoke(null, context);

		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	public <T extends SyncService> Class<?> getLoginClassFromSyncSource(Class<T> source)
	{
		try {
			Method m = source.getMethod("getLoginClass");
			return (Class<?>) m.invoke(null);

		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// default to Facebook
		return FacebookLoginWebView.class;
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
    	//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        if (savedInstanceState != null && savedInstanceState.containsKey("DIALOG")) {
        	showDialog(savedInstanceState.getInt("DIALOG"));
        }
        else if (!getSharedPreferences(GlobalPreferences.PREFS_NAME, 0).getBoolean("do_not_show_about", false)) {
        	showDialog(ABOUT_DIALOG);
        }
        
        ImageButton sync = (ImageButton) this.findViewById(R.id.syncButton);
        sync.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (isLoggedInFromSyncSource(getBaseContext(), getSyncSource(getBaseContext()))) {
					sync();
					//showDialog(CONFIRM_DIALOG);
				}
				else {
					login();
				}
			}
        	
        });
        
        ImageButton settings = (ImageButton) this.findViewById(R.id.settingsButton);
        settings.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				Intent i = new Intent(MainActivity.this, GlobalPreferences.class);
				startActivity(i);
			}
        	
        });
        
        ImageButton results = (ImageButton) this.findViewById(R.id.resultsButton);
        results.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				showResults();
			}
        	
        });

    }
    
    
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (resultCode != Activity.RESULT_OK) {
			return;
		}
		
		switch(requestCode) {
			case SOCIAL_NETWORK_LOGIN:
				sync();
				break;
		}
	}


	private final static int SOCIAL_NETWORK_LOGIN = 0;
    
	private void login()
    {
		startActivityForResult(new Intent(MainActivity.this, 
				getLoginClassFromSyncSource(getSyncSource(getBaseContext()))), 
				SOCIAL_NETWORK_LOGIN);
    }
    
    // TODO This is needless filler, REMOVE
    private void logout()
    {
    	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "session_key", null);
    	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "secret", null);
    	Utils.setString(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "uid", null);
    }  
    
    private void sync()
    {
    	if (!Utils.hasInternetConnection(getBaseContext())) {
    		Toast.makeText(this, R.string.syncservice_networkerror, Toast.LENGTH_LONG).show();
    		return;
    	}
    	
   		//showDialog(FRIENDS_PROGRESS);
    	
    	Intent i = new Intent(MainActivity.this, getSyncSource(getBaseContext()));
    	bindService(i, syncServiceConn, Context.BIND_AUTO_CREATE);
    	
    	if (syncService == null || !syncService.isExecuting()) {
    		startService(i);
    		startActivity(new Intent(MainActivity.this, SyncProgressActivity.class));
    	}
    }
    
    private void showResults()
    {
    	Intent i = new Intent(this, SyncResults.class);
    	startActivity(i);
    }

	@Override
	protected void onResume() {
		super.onResume();
		
		if (!syncServiceConnected) {
			Intent i = new Intent(MainActivity.this, getSyncSource(getBaseContext()));
			bindService(i, syncServiceConn, 0);
		}
		
		if (syncService != null && syncService.isExecuting()) {
			startActivity(new Intent(MainActivity.this, SyncProgressActivity.class));
		}
	}
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(syncServiceConn);
	}
    
	private final int MENU_LOGOUT = 3;
    private final int MENU_ABOUT = 4;
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	MenuItem item;
    	
    	item = menu.add(0, MENU_LOGOUT, 0, R.string.main_logoutButton);
    	item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    	
    	item = menu.add(0, MENU_ABOUT, 0, R.string.main_aboutButton);
    	item.setIcon(android.R.drawable.ic_menu_help);
    	
    	return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_LOGOUT:
				logout();
				return true;
			case MENU_ABOUT:
				showDialog(ABOUT_DIALOG);
				return true;
	    }
		
	    return false;
	}

	private final int ABOUT_DIALOG = 2;
	private final int CONFIRM_DIALOG = 3;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case ABOUT_DIALOG:
				return createAboutDialog();
				
			case CONFIRM_DIALOG:
				ConfirmSyncDialog dialog = new ConfirmSyncDialog(this);
				dialog.setProceedButtonListener(new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						sync();
						
					}
					
				});
				
				dialog.setCancelButtonListener(null);
				return dialog;
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
		
		CheckBox show = (CheckBox)about.findViewById(R.id.do_not_show_about);
		show.setChecked(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0).getBoolean("do_not_show_about", false));
		show.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {

				Utils.setBoolean(getSharedPreferences(GlobalPreferences.PREFS_NAME, 0), "do_not_show_about", isChecked);
			}
			
		});
		
		return about;
	}

	private SyncService syncService;
	private boolean syncServiceConnected = false;
	
    private ServiceConnection syncServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	
        	syncServiceConnected = true;
        	syncService = ((SyncService.LocalBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            
        	syncServiceConnected = false;
        	syncService.unsetListener();
        	syncService = null;
        }
    };
}
