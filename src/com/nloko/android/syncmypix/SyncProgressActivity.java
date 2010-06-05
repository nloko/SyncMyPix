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

import java.lang.ref.WeakReference;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.SyncService.SyncServiceStatus;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

public class SyncProgressActivity extends Activity {
	private WeakReference<SyncService> mSyncService;
	private ProgressBar mTitleProgress;
	private ProgressBar mProgress;
	private ImageSwitcher mImageSwitcher;
	private TextSwitcher mTextSwitcher;
	private TextSwitcher mStatusSwitcher;
	private ImageButton mCancelButton;
	private ImageButton mHomeButton;
	private TextView mStatus;

    private final int FRIENDS_PROGRESS = 0;
    private final int CANCELLING_DIALOG = 1;
	
	private boolean mSyncServiceBound = false;
	
	private static final String TAG = "SyncProgressActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.syncprogress);
		
		mProgress = (ProgressBar) findViewById(R.id.syncProgress);
		mTitleProgress = (ProgressBar) findViewById(R.id.progress);
		mImageSwitcher = (ImageSwitcher) findViewById(R.id.PhotoImageSwitcher);
		mTextSwitcher = (TextSwitcher) findViewById(R.id.NameTextSwitcher);
		mStatusSwitcher = (TextSwitcher) findViewById(R.id.syncStatusSwitcher);
		mStatus = (TextView) findViewById(R.id.status);
		
		mHomeButton = (ImageButton) findViewById(R.id.home);
		mHomeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
				finish();
			}
		});
		
		mCancelButton = (ImageButton) findViewById(R.id.syncCancel);
		mCancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				SyncService service = mSyncService.get();
				if (service != null && service.isExecuting()) {
					service.cancelOperation();
				}
			}
		});
	}

    @Override
	protected void onStart() {
		super.onStart();
		if (!mSyncServiceBound) {
			Intent i = new Intent(getApplicationContext(), MainActivity.getSyncSource(getApplicationContext()));
			mSyncServiceBound = bindService(i, mSyncServiceConn, Context.BIND_AUTO_CREATE);
		}
	}

    
	@Override
	protected void onResume() {
		super.onResume();
		mTitleProgress.setVisibility(View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mSyncServiceConn);
		if (mSyncService != null) {
			SyncService service = mSyncService.get();
			if (service != null) {
				service.unsetListener();
			}
		}
		mSyncServiceBound = false;
		mSyncServiceConn = null;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}
    
    @Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case FRIENDS_PROGRESS:
				//ProgressDialog mFriendsProgress = new ProgressDialog(this);
				//mFriendsProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				//mFriendsProgress.setMessage(getString(R.string.main_friendsDialog));
				//mFriendsProgress.setCancelable(false);
				//return mFriendsProgress;
			case CANCELLING_DIALOG:
				ProgressDialog cancelling = new ProgressDialog(this);
				cancelling.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				cancelling.setCancelable(false);
				cancelling.setMessage(getString(R.string.syncprogress_cancel));
				return cancelling;
		}
		return super.onCreateDialog(id);
    }
				
    private ServiceConnection mSyncServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	mSyncService = new WeakReference<SyncService>(((SyncService.LocalBinder)service).getService());
        	SyncService s = mSyncService.get();
        	if (s == null) {
        		return;
        	}
        	
        	int status = s.getStatus();
        	if (status == SyncService.GETTING_FRIENDS) {
        		mStatus.setText(R.string.main_friendsDialog);
        	} else if (status == SyncService.IDLE) {
        		Toast.makeText(getApplicationContext(), 
        				R.string.syncprogress_nosync, 
        				Toast.LENGTH_SHORT).show();
        		finish();
        		return;
        	} else {
        		mCancelButton.setVisibility(View.VISIBLE);
				mHomeButton.setVisibility(View.VISIBLE);
        	}
        	
        	s.setListener(new SyncServiceListener() {
				public void onSyncProgressUpdated(int percentage, int index, int total) {
					mStatus.setVisibility(View.GONE);
					mTitleProgress.setVisibility(View.INVISIBLE);
					mProgress.setVisibility(View.VISIBLE);
					mCancelButton.setVisibility(View.VISIBLE);
					mHomeButton.setVisibility(View.VISIBLE);
					
					if (mProgress != null) {
						if (percentage < 100) {
							mProgress.setMax(total);
							mProgress.setProgress(index);
						}
					}
				}
				
				public void onError (int id) {
					finish();
				}

				public void onContactSynced(String name, Bitmap bitmap, String status) {
					Log.d(TAG, String.format("onPictureDownloaded for %s", name));
					
					mTextSwitcher.setText(name);
					mStatusSwitcher.setText(status);
					mImageSwitcher.setImageDrawable(new BitmapDrawable(bitmap));
					if (mImageSwitcher.getVisibility() != View.VISIBLE) {
						mImageSwitcher.setVisibility(View.VISIBLE);
					}
				}

				public void onSyncCompleted() {
					Intent i = new Intent(getApplicationContext(), SyncResultsActivity.class);
					i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
					startActivity(i);
					finish();
				}

				public void onFriendsDownloadStarted() {
					mStatus.setText(R.string.main_friendsDialog);
				}

				public void onSyncCancelled() {
					showDialog(CANCELLING_DIALOG);
				}
            });
        }

        public void onServiceDisconnected(ComponentName className) {
        	Log.d(TAG, "onServiceDisconnected");
        	mSyncServiceBound = false;
        	SyncService s = mSyncService.get();
        	if (s != null) {
        		s.unsetListener();
        	}
        	mSyncService = null;
        	finish();
        }
    };
}
