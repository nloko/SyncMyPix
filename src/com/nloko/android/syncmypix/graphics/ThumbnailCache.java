//
//    ThumbnailCache.java is part of SyncMyPix
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

package com.nloko.android.syncmypix.graphics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import com.nloko.android.Utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;

public class ThumbnailCache {

	private final Map <String, Bitmap> images = new HashMap <String, Bitmap> (); 
	private final Object lock = new Object();
	
	private ThumbnailCache() {}
	
	private static ThumbnailCache instance = null;
	public static ThumbnailCache create()
	{
		if (instance == null) {
			instance = new ThumbnailCache();
		}
		
		return instance;
	}
	
	public void destroy()
	{
		images.clear();
		instance = null;
	}
	
	public boolean contains(String key)
	{
		synchronized(lock) {
			if (images.containsKey(key)) {
				return true;
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
		if (bitmap == null) {
			throw new IllegalArgumentException("bitmap");
		}
		
		if (key == null) {
			throw new IllegalArgumentException("key");
		}
		
		if (resize) {
			bitmap = Utils.resize(bitmap, 40, 40);
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (bitmap.compress(CompressFormat.JPEG, 100, out)) {
			if (out != null) {
				bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
			}
		}
		
		synchronized(lock) {
			images.put(key, bitmap);
		}
	}
	
	public boolean remove(String key)
	{
		synchronized(lock) {
			if (images.containsKey(key)) {
				images.remove(key);
				return true;
			}
		}
		
		return false;
	}
	
	public Bitmap get(String key) 
	{
		synchronized(lock) {
			if (images.containsKey(key)) {
				return images.get(key);
			}
		}
		
		return null;
	}
}
