//
//  Utils.java
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.nloko.android.Log;

public final class Utils {

	private Utils() {}
	
      public static String getMd5Hash(byte[] input) 
      {
    	  if (input == null) {
    		  throw new IllegalArgumentException("input");
    	  }
    	  
          try {
              MessageDigest md = MessageDigest.getInstance("MD5");
              byte[] messageDigest = md.digest(input);
              BigInteger number = new BigInteger(1,messageDigest);
              String md5 = number.toString(16);

              while (md5.length() < 32) {
                  md5 = "0" + md5;
              }
              
              return md5;
          } 
          catch(NoSuchAlgorithmException e) {
        	  Log.e("MD5", e.getMessage());
              return null;
          }
    }
      
	public static String buildNameSelection (String field, String firstName, String lastName)
	{
		if (field == null) {
			throw new IllegalArgumentException ("field");
		}
		
		if (firstName == null) {
			throw new IllegalArgumentException ("firstName");
		}
		
		if (lastName == null) {
			throw new IllegalArgumentException ("lastName");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%s = '%s %s' OR ", field, firstName, lastName));
		sb.append(String.format("%s = '%s, %s' OR ", field, lastName, firstName));
		sb.append(String.format("%s = '%s,%s'", field, lastName, firstName));
		
		return sb.toString();
	}
	
	public static byte[] getByteArrayFromInputStream(InputStream is)
	{
		if (is == null) {
			throw new IllegalArgumentException("is");
		}
		
		int size = 8192;
		int read = 0;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(size);
		byte[] buffer = new byte[size];
		
		try {
			while ((read = is.read(buffer, 0, buffer.length)) > 0) {
				bytes.write(buffer, 0, read);
			}
		}
		catch (IOException ex) {
			return null;
		}
		
		return bytes.toByteArray();
	}

	public static Bitmap downloadPictureAsBitmap (String url)
	{
		if (url == null) {
    		throw new IllegalArgumentException ("url");
    	}
    	
    	Bitmap image = null;
    	try {
	    	URL fetchUrl = new URL(url);
	    	HttpURLConnection conn = (HttpURLConnection) fetchUrl.openConnection();
	    	InputStream stream = conn.getInputStream();
	    	
	    	image = BitmapFactory.decodeStream(stream);
    	}
	    catch (IOException ex) {
	    	Log.e(null, android.util.Log.getStackTraceString(ex));
	    }
	    
	    return image;
	}
	
	public static byte[] downloadPicture (String url)
    {
		byte[] image = null;
		Bitmap bitmap = downloadPictureAsBitmap(url);
		
		if (bitmap != null) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 50, bytes);
			image =  bytes.toByteArray();
		}

    	return image;
    }
    
	public static void setBoolean (SharedPreferences settings, String key, boolean value)
    {
    	if (settings == null) {
    		throw new IllegalArgumentException ("settings");
    	}
    	
    	if (key == null) {
    		throw new IllegalArgumentException ("key");
    	}
        
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(key, value);

        // Don't forget to commit your edits!!!
        editor.commit();
    }
	
	public static void setString (SharedPreferences settings, String key, String value)
    {
    	if (settings == null) {
    		throw new IllegalArgumentException ("settings");
    	}
    	
    	if (key == null) {
    		throw new IllegalArgumentException ("key");
    	}
    	
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(key, value);

        // Don't forget to commit your edits!!!
        editor.commit();
    }
	
	public static void setInt (SharedPreferences settings, String key, int value)
    {
    	if (settings == null) {
    		throw new IllegalArgumentException ("settings");
    	}
    	
    	if (key == null) {
    		throw new IllegalArgumentException ("key");
    	}
    	
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(key, value);

        // Don't forget to commit your edits!!!
        editor.commit();
    }
	
	public static void setLong (SharedPreferences settings, String key, long value)
    {
    	if (settings == null) {
    		throw new IllegalArgumentException ("settings");
    	}
    	
    	if (key == null) {
    		throw new IllegalArgumentException ("key");
    	}
    	
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(key, value);

        // Don't forget to commit your edits!!!
        editor.commit();
    }

}
