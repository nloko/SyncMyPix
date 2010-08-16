//
//  ThumbnailCache.java
//
//  Authors:
// 		Neil Loknath <neil.loknath@gmail.com>
//
//  Copyright 2009 Neil Loknath
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;

public class ThumbnailCache {

	private final String TAG = "ThumbnailCache";
	// use SoftReference so Android can free memory, if needed
	// ThumbnailCache can notify if a download is required or use the built-in 
	// ImageDownloader
	// See setImageListener and setImageProvider
	
	private final Map <String, SoftReference<Bitmap>> mImages = new HashMap <String, SoftReference<Bitmap>> ();
	
	private final Object lock = new Object();
	
	private Bitmap mDefaultImage = null;
	private ImageListener mListener = null;
	private ImageProvider mProvider = null;
	private final ImageDownloader mDownloader = new ImageDownloader(this);
	
	public void setDefaultImage(Bitmap defaultImage)
	{
		mDefaultImage = defaultImage;
	}
	
	public Bitmap getDefaultImage()
	{
		return mDefaultImage;
	}
	
	public void empty ()
	{
		synchronized(lock) {
			mImages.clear();
		}
	}
	
	public void destroy()
	{
		mDownloader.setPause(true);
		mListener = null;
		mProvider = null;
		mImages.clear();
	}
	
	public boolean contains(String key)
	{
		synchronized(lock) {
			if (key != null) {
				if (mImages.containsKey(key)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public void add(String key, byte[] image)
	{
		add(key, BitmapFactory.decodeByteArray(image, 0, image.length));
	}
	
	public void add(String key, Bitmap bitmap)
	{
		add(key, bitmap, true);
	}
	
	public void add(String key, Bitmap bitmap, boolean resize)
	{
		add(key, bitmap, resize, true);
	}
	
	public void add(String key, Bitmap bitmap, boolean resize, boolean notify)
	{
		if (bitmap == null) {
			throw new IllegalArgumentException("bitmap");
		}
		
		if (key == null) {
			throw new IllegalArgumentException("key");
		}
		
		if (resize) {
			bitmap = Utils.centerCrop(bitmap, 44, 44);
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (bitmap.compress(CompressFormat.JPEG, 85, out)) {
			if (out != null) {
				bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
			}
		}
		
		synchronized(lock) {
			mImages.put(key, new SoftReference<Bitmap>(bitmap));
			ImageListener listener = mListener;
			if (notify && listener != null) {
				listener.onImageReady(key);
			}
		}
	}
	
	public boolean remove(String key)
	{
		synchronized(lock) {
			if (key != null) {
				if (mImages.containsKey(key)) {
					mImages.remove(key);
					return true;
				}
			}
		}
		
		return false;
	}
	
	public Bitmap get(String key) 
	{
		if (key == null) {
			return null;
		}
		
		Bitmap image = null;
		
		synchronized(lock) {
			if (mImages.containsKey(key)) {
				if (mImages.get(key) != null) {
					image = mImages.get(key).get();
				}
			}
		}
		
		if (image == null) {
			if (mDefaultImage != null) {
				//mImages.put(key, new SoftReference<Bitmap>(mDefaultImage));
				image = mDefaultImage;
			}
			ImageProvider provider = mProvider;
			if (provider == null) {
				mDownloader.download(key);
			} else {
				remove(key);
				provider.onImageRequired(key);
			}
		}
			
		return image;
	}
	
	// this can be used in onPause and onResume to conserve
	// battery life by terminating the looping downloader
	// thread
	public void togglePauseOnDownloader(boolean value)
	{
		if (mDownloader != null) {
			mDownloader.setPause(value);
		}
	}
	
	public void setImageListener(ImageListener listener)
	{
		mListener = listener;
	}
	
	public void setImageProvider(ImageProvider provider)
	{
		mProvider = provider;
	}
	
	public interface ImageListener {
		void onImageReady(String url);
	}
	
	public interface ImageProvider {
		boolean onImageRequired(String url);
	}
	
	private static class ImageDownloader {
		private final static String TAG = "ImageDownloader";
		private final BlockingQueue<String> urlQueue = new LinkedBlockingQueue<String>();
		private WeakReference<ThumbnailCache> mCache;
		private Thread downloadThread;
		private boolean paused = false;
		
		public ImageDownloader(ThumbnailCache cache)
		{
			mCache = new WeakReference<ThumbnailCache>(cache);
			setupThread();
		}

		private void setupThread()
		{
			final ThumbnailCache cache = mCache.get();
			downloadThread = new Thread(new Runnable() {
				public void run() {
					String url;
					while(!paused) {
						try {
							url = urlQueue.take();
							InputStream friend = Utils.downloadPictureAsStream(url);
							if (friend != null) {
								Bitmap image = BitmapFactory.decodeStream(friend);
								if (cache != null) {
									cache.add(url, image, true, true);	
								}
							}
							
						} catch (InterruptedException e) {
							Log.d(TAG, "INTERRUPTED!");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});
			
			downloadThread.start();
		}
		
		public void setPause(boolean value)
		{
			if (paused == value) {
				return;
			}
			
			Log.d(TAG, "setPause called with " + value);
			paused = value;
			if (paused && downloadThread != null) {
				downloadThread.interrupt();
			} else if (!paused) {
				setupThread();
			}
		}
		
		public void download(String url)
		{
			if (url == null) {
				return;
			}
			
			try {
				urlQueue.put(url);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
