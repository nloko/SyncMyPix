//
//    SyncProgressActivity.java is part of SyncMyPix
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

import com.nloko.android.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageSwitcher;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;

public class SyncProgressActivity extends Activity {

	private ProgressBar progress;
	private ImageSwitcher imageSwitcher;
	private TextSwitcher textSwitcher;
	private Button cancelButton;
	
	private static final String TAG = "SyncProgressActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.syncprogress);
		
		progress = (ProgressBar) findViewById(R.id.syncProgress);
		imageSwitcher = (ImageSwitcher) findViewById(R.id.PhotoImageSwitcher);
		textSwitcher = (TextSwitcher) findViewById(R.id.NameTextSwitcher);
		cancelButton = (Button) findViewById(R.id.syncCancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (syncService != null && syncService.isExecuting()) {
					syncService.cancelOperation();
				}
			}
		});
	}

	
    @Override
	protected void onResume() {
		super.onResume();
		if (!syncServiceConnected) {
			Intent i = new Intent(SyncProgressActivity.this, MainActivity.getSyncSource(getBaseContext()));
			bindService(i, syncServiceConn, 0);
		}
		else if (syncService != null && !syncService.isExecuting()) {
			finish();
		}
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(syncServiceConn);
	}
    
    private final int FRIENDS_PROGRESS = 0;
    
    private ProgressDialog friendsProgress;
    
    @Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {

			case FRIENDS_PROGRESS:
				friendsProgress = new ProgressDialog(this);
				friendsProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				friendsProgress.setMessage(getString(R.string.main_friendsDialog));
				friendsProgress.setCancelable(false);
				return friendsProgress;
		}
		
		return super.onCreateDialog(id);
    }
				
	private SyncService syncService;
	private boolean syncServiceConnected = false;
	
    private ServiceConnection syncServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	
        	showDialog(FRIENDS_PROGRESS);
        	
        	syncServiceConnected = true;
        	
        	syncService = ((SyncService.LocalBinder)service).getService();
        	syncService.setListener(new SyncServiceListener () {

				public void onSyncProgress(int percentage, int index, int total) {
					if (friendsProgress != null && friendsProgress.isShowing()) {
						dismissDialog(FRIENDS_PROGRESS);
						progress.setVisibility(View.VISIBLE);
						cancelButton.setVisibility(View.VISIBLE);
					}
					
					if (progress != null) {
						if (percentage < 100) {
							progress.setMax(total);
							progress.setProgress(index);
						}
					}
				}
				
				public void onError (int id) {
					finish();
				}

				public void onPictureDownloaded(String name, Bitmap bitmap) {
					Log.d(TAG, String.format("onPictureDownloaded for %s", name));
					
					textSwitcher.setText(name);
					imageSwitcher.setImageDrawable(new BitmapDrawable(bitmap));
					if (imageSwitcher.getVisibility() != View.VISIBLE) {
						imageSwitcher.setVisibility(View.VISIBLE);
					}
				}

				public void onSyncCompleted() {
					startActivity(new Intent(SyncProgressActivity.this, SyncResults.class));
					finish();
				}
            });

        }

        public void onServiceDisconnected(ComponentName className) {
            
        	syncServiceConnected = false;
        	syncService.unsetListener();
        	syncService = null;
        	
        	finish();
        }
    };
}
