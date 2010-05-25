//
//  PhotoCache.java
//
//  Authors:
// 		Neil Loknath <neil.loknath@gmail.com>
//
//  Copyright 2010 Neil Loknath
//
//  Licensed under the Apache License, Version 2.0 (the "License"); 
//  you may not use this file except in compliance with the License. 
//  You may obtain a copy of the License at 
//
//  http://www.apache.org/licenses/LICENSE-2.0 
//
//  Unless required by applicable law or agreed to in writing, software 
//  distributed under the License is distributed on an "AS IS" BASIS, 
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
//  See the License for the specific language governing permissions and 
//  limitations under the License. 
//

package com.nloko.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.Environment;

public class PhotoCache {
	// this location is important, as it allows automatic removal when the app is
	// uninstalled
	public static final String BASE_DIR = "Android/data/%s/cache/";
	public long mMaxBytes = 5000000;
	
	private static final String TAG = "PhotoCache";
	
	private BroadcastReceiver mExternalStorageReceiver;
	private WeakReference<Context> mContext;
	private boolean mExternalStorageAvailable;
	private boolean mExternalStorageWriteable;
	private File mPath;
	
	private final TreeMap<Long, List<String>> mPhotos = new TreeMap<Long, List<String>>();
	private long mSize = 0;
	private boolean mSized = false;
	
	public PhotoCache(Context context, long maxBytes) {
		this(context);
		mMaxBytes = maxBytes;
	}
	
	public PhotoCache(Context context) {
		if (context == null) {
			throw new IllegalArgumentException("context");
		}
		
		mContext = new WeakReference<Context>(context);
		File path = Environment.getExternalStorageDirectory();
		mPath = new File(path, String.format(BASE_DIR, context.getPackageName()));
		startWatchingExternalStorage();
	}

	private void ensurePath() {
		if (mExternalStorageWriteable) {
			mPath.mkdirs();
		}
	}
	
	private synchronized void resize() {
		Log.d(TAG, String.format("resize() map size %d", mPhotos.size()));
		// delete the oldest in the cache
		while (!mPhotos.isEmpty() && mSize > mMaxBytes) {
			for(String name : mPhotos.get(mPhotos.firstKey())) {
				File f = new File(mPath, name);
				if (mExternalStorageWriteable && f.exists()) {
					mSize -= f.length();
					f.delete();
					Log.d(TAG, String.format("resize() deleted %s", name));
				}
				
				Log.v(TAG, String.format("resize() %d", mSize));
			}
			
			mPhotos.remove(mPhotos.firstKey());
		}
	}
	
	private synchronized void calculateSize() {
		if (!mExternalStorageAvailable) {
			return;
		}
		
		mSized = true;
		File[] files = mPath.listFiles();
		if (files == null) {
			return;
		}
		
		for(File f : files) {
			updateSize(f);
		}
	}

	private synchronized void updateSize(File f) {
		long modified = f.lastModified();
		if (!mPhotos.containsKey(modified)) {
			mPhotos.put(modified, new ArrayList<String>());
		}
		
		mPhotos.get(modified).add(f.getName());
		mSize += f.length();
		Log.d(TAG, String.format("updateSize() %d", mSize));
		resize();
	}
	
	public void releaseResources() {
		stopWatchingExternalStorage();
	}
	
	public void destory() {
		deleteAll();
		stopWatchingExternalStorage();
	}
	
	public synchronized void deleteAll() {
		File[] files = mPath.listFiles();
		if (files == null) {
			return;
		}
		
		for(File f : files) {
			if (mExternalStorageWriteable) {
				if (mSized) {
					mSize -= f.length();
				}
				f.delete();
			}
		}
	}
	
	public synchronized void delete(String name) {
		if (!mExternalStorageWriteable || name == null) {
			return;
		}
		
		File f = new File(mPath, name);
		if (f.exists()) {
			mSize -= f.length();
			f.delete();
		}
	}
	
	public Bitmap get(String file) {
		if (file == null) {
			return null;
		}
		
		File path = new File(mPath, file);
		return BitmapFactory.decodeFile(path.getAbsolutePath());
	}
	
	public synchronized void add(String file, Bitmap bitmap) {
		if (bitmap == null) {
			throw new IllegalArgumentException("bitmap");
		}
		if (file == null) {
			throw new IllegalArgumentException("file");
		}
		
		if (!mSized) {
			calculateSize();
		}
		
		File photo = new File(mPath, file);
		if (photo.exists()) {
			return;
		}
		
		if (mExternalStorageWriteable) {
			try {
				ensurePath();
				OutputStream os = new FileOutputStream(photo);
				bitmap.compress(CompressFormat.JPEG, 100, os);
				os.close();
				updateSize(photo);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void startWatchingExternalStorage() {
		Context context = mContext.get();
		if (context == null) {
			return;
		}
		
	    mExternalStorageReceiver = new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	            Log.d("test", "Storage: " + intent.getData());
	            updateExternalStorageState();
	        }
	    };
	    IntentFilter filter = new IntentFilter();
	    filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
	    filter.addAction(Intent.ACTION_MEDIA_REMOVED);
	    context.registerReceiver(mExternalStorageReceiver, filter);
	    updateExternalStorageState();
	}

	private void stopWatchingExternalStorage() {
		Context context = mContext.get();
		if (context == null) {
			return;
		}
		
	    context.unregisterReceiver(mExternalStorageReceiver);
	}
	
	private synchronized void updateExternalStorageState() {
		mExternalStorageAvailable = false;
		mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    // We can read and write the media
		    mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		    // We can only read the media
		    mExternalStorageAvailable = true;
		    mExternalStorageWriteable = false;
		} else {
		    // Something else is wrong. It may be one of many other states, but all we need
		    //  to know is we can neither read nor write
		    mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
	}
}
