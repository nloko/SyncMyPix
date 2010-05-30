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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

public class PhotoCache {
	// this location is important, as it allows automatic removal when the app is
	// uninstalled
	public static final String BASE_DIR = "Android/data/%s/cache/";
	public static final String NO_MEDIA = ".nomedia";
	public long mMaxBytes = 5000000;
	
	private static final String TAG = "PhotoCache";
	private static final int DELETE_ALL = 1;
	private static final int DELETE = 2;
	private static final int ADD = 3;
	private static final int SHUTDOWN = 4;
	
	public static final int DELETE_OLDEST = 0;
	public static final int DELETE_NEWEST = 1;
		
	private BroadcastReceiver mExternalStorageReceiver;
	private WeakReference<Context> mContext;
	private boolean mExternalStorageAvailable;
	private boolean mExternalStorageWriteable;
	private File mPath;
	
	private final TreeMap<Long, List<String>> mPhotos = new TreeMap<Long, List<String>>();
	private long mSize = 0;
	private boolean mSized = false;
	private int mDeleteOrder = DELETE_OLDEST;
	
	private final AsyncHandler mHandler;
	private PhotoCacheListener mListener;
	
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
		
		HandlerThread thread = new HandlerThread("PhotoCacheThread", 
				Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		mHandler = new AsyncHandler(thread);
	}
	
	public void setDeleteOrder(int order) {
		if (order == DELETE_OLDEST || order == DELETE_NEWEST) {
			mDeleteOrder = order;
		}
	}
	
	public void setListener(PhotoCacheListener listener) {
		mListener = listener;
	}
	
	public void delete(String name) {
		Message msg = mHandler.obtainMessage();
		msg.what = DELETE;
		msg.obj = name;
		mHandler.sendMessage(msg);
	}

	public void deleteAll() {
		mHandler.sendEmptyMessage(DELETE_ALL);
	}
	
	public void releaseResources() {
		mHandler.sendEmptyMessage(SHUTDOWN);
	}
	
	public void destroy() {
		deleteAll();
		releaseResources();
	}
	
	public InputStream get(String file) {
		if (file == null) {
			return null;
		}
		
		File path = new File(mPath, file);
		try {
			return new FileInputStream(path.getAbsolutePath());
		} catch (FileNotFoundException e) {}
		
		return null;
	}
	
	public void add(String file, byte[] b) {
		Message msg = mHandler.obtainMessage();
		msg.what = ADD;
		msg.obj = new Photo(file, b);
		mHandler.sendMessage(msg);
	}
	
	private boolean isNoMedia(File f) {
		if (f == null) return false;
		return f.getName().equals(NO_MEDIA) && f.length() == 0;
	}
	
	private void startWatchingExternalStorage() {
		Context context = mContext.get();
		if (context == null) {
			return;
		}
		
		Log.v(TAG, "Registering BroadcastReceiver");
	    mExternalStorageReceiver = new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	            Log.d(TAG, "Storage: " + intent.getData());
	            updateExternalStorageState();
	        }
	    };
	    IntentFilter filter = new IntentFilter();
	    filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
	    filter.addAction(Intent.ACTION_MEDIA_REMOVED);
	    filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
	    filter.addDataScheme("file");
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
	
	private final class AsyncHandler extends Handler { 
		public AsyncHandler(HandlerThread t) {
			super(t.getLooper());
		}

		private synchronized void ensurePath() {
			if (mExternalStorageWriteable) {
				mPath.mkdirs();
			}
		}
		
		private synchronized void resize() {
			Log.d(TAG, String.format("resize() map size %d", mPhotos.size()));
			// delete the oldest in the cache
			while (!mPhotos.isEmpty() && mSize > mMaxBytes) {
				long key;
				if (mDeleteOrder == DELETE_OLDEST) {
					key = mPhotos.firstKey();
				} else {
					key = mPhotos.lastKey();
				}
				for(String name : mPhotos.get(key)) {
					delete(name);
					Log.v(TAG, String.format("resize() %d", mSize));
				}
				
				mPhotos.remove(key);
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

		private synchronized void delete(String name) {
			if (!mExternalStorageWriteable || name == null) {
				return;
			}
			
			File f = new File(mPath, name);
			if (!isNoMedia(f) && f.exists()) {
				mSize -= f.length();
				f.delete();
				if (mListener != null) {
					mListener.onDeleted(name);
				}
				Log.d(TAG, String.format("delete() deleted %s", name));
			}
		}
	
		private synchronized void deleteAll() {
			File[] files = mPath.listFiles();
			if (files == null) {
				return;
			}
			
			for(File f : files) {
				if (mExternalStorageWriteable && !isNoMedia(f)) {
					if (mSized) {
						mSize -= f.length();
					}
					f.delete();
				}
			}
			
			if (mListener != null) {
				mListener.onAllDeleted();
			}
		}

		private synchronized void add(String file, byte[] bytes) {
			if (bytes == null) {
				throw new IllegalArgumentException("bytes");
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
					os.write(bytes);
					os.close();
					updateSize(photo);
					
					if (mListener != null) {
						mListener.onAdded(file);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			
			switch(msg.what) {
			case ADD:
				Photo p = (Photo)msg.obj;
				if (p != null) {
					add(p.file, p.bytes);
				}
				break;
			case DELETE_ALL:
				deleteAll();
				break;
				
			case DELETE:
				delete((String)msg.obj);
				break;

			case SHUTDOWN:
				stopWatchingExternalStorage();
				getLooper().quit();
				break;
			}
		}
	};
	
	private static final class Photo {
		public String file;
		public byte[] bytes;
		
		public Photo(String f, byte[] b) {
			file = f;
			bytes = b;
		}
	}
}
