//
//    SyncMyPixDbHelper.java is part of SyncMyPix
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

import java.io.InputStream;

import com.nloko.android.Log;
import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Contacts;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts.People;

public class SyncMyPixDbHelper {

	private static final String TAG = "SyncMyPixDbHelper";
	
	private final ContentResolver mResolver;
	
	@SuppressWarnings("unused")
	private SyncMyPixDbHelper()
	{
		mResolver = null;
	}
	
	public SyncMyPixDbHelper(Context context)
	{
		super();
		mResolver = context.getContentResolver();
	}

	public void deleteAllPictures()
	{
		deleteAllPictures(null);
	}
	
	public void deleteAllPictures(final DbHelperNotifier notifier)
	{
		final Cursor cursor = mResolver.query(Contacts.CONTENT_URI, 
				new String[] { Contacts._ID, Contacts.PHOTO_HASH },
				null,
				null, 
				null);
		
		Thread thread = new Thread(new Runnable() {
			
			public void run() {
				
				synchronized(SyncService.syncLock) {
					
					while(cursor.moveToNext()) {
						String id  = cursor.getString(cursor.getColumnIndex(Contacts._ID));
						String dbHash = cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_HASH));
						Uri uri = Uri.withAppendedPath(People.CONTENT_URI, id);
						
						InputStream stream = People.openContactPhotoInputStream(mResolver, uri);
						if (stream != null) {
							String hash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(stream));
							if (dbHash.equals(hash)) {
								ContactServices.updateContactPhoto(mResolver, null, id);
							}
						}
					}
					
					mResolver.delete(Contacts.CONTENT_URI, null, null);
					mResolver.delete(Results.CONTENT_URI, null, null);
					mResolver.delete(Sync.CONTENT_URI, null, null);

				}
				
				if (notifier != null) {
					notifier.onUpdateComplete();
				}
				
				cursor.close();
			}
			
		});
		
		thread.start();
	}

	public void updateHashes(String id, byte[] origImage, byte[] modifiedImage)
	{
		String networkHash = null;
		String hash = null;
		
		if (origImage != null) {
			networkHash = Utils.getMd5Hash(origImage);
		}
		
		if (modifiedImage != null) {
			hash = Utils.getMd5Hash(modifiedImage);
		}

		updateHashes(id, networkHash, hash);
	}
	
	public void updateHashes(String id, String networkHash, String updatedHash)
	{
		Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
		Cursor cursor = mResolver.query(uri,
						new String[] { Contacts._ID }, 
						null, 
						null, 
						null);	
		
		ContentValues values = new ContentValues();
		
		if (networkHash != null) {
			values.put(Contacts.NETWORK_PHOTO_HASH, networkHash);
		}
		
		if (updatedHash != null) {
			values.put(Contacts.PHOTO_HASH, updatedHash);
		}
		
		if (cursor.moveToFirst()) {
			mResolver.update(uri, values, null, null);
		}
		else {
			values.put(Contacts._ID, id);
			mResolver.insert(Contacts.CONTENT_URI, values);
		}
		
		if (cursor != null) {
			cursor.close();
		}
	}
	
    public DBHashes getHashes(String id)
    {
    	if (id == null) {
    		throw new IllegalArgumentException("id");
    	}
    	
    	Uri syncUri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
		
    	Cursor syncC = mResolver.query(syncUri, 
				new String[] { SyncMyPix.Contacts._ID,
				SyncMyPix.Contacts.PHOTO_HASH,
				SyncMyPix.Contacts.NETWORK_PHOTO_HASH }, 
				null, 
				null, 
				null);
		
    	DBHashes hashes = new DBHashes();
    	
		if (syncC.moveToFirst()) {
			hashes.updatedHash = syncC.getString(syncC.getColumnIndex(SyncMyPix.Contacts.PHOTO_HASH));
			hashes.networkHash = syncC.getString(syncC.getColumnIndex(SyncMyPix.Contacts.NETWORK_PHOTO_HASH));
		}
		
		syncC.close();
		
		return hashes;
    }
    
    public boolean isSyncablePicture(String id, String dbHash, String contactHash, boolean skipIfExists)
    {
    	if (id == null) {
    		throw new IllegalArgumentException("id");
    	}
    	
    	boolean ok = true;

    	Uri syncUri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
    	    	
    	if (skipIfExists) {
    		
    		// not tracking any hashes for contact and photo is set for contact
    		if (dbHash == null && contactHash != null) {
    			ok = false;
    		}
    		
    		// we are tracking a hash and there is a photo for this contact
    		else if (contactHash != null) {

    			Log.d(TAG, String.format("dbhash %s hash %s", dbHash, contactHash));

    			// hashes do not match, so we don't need to track this hash anymore
    			if (!contactHash.equals(dbHash)) {
   					mResolver.delete(syncUri, null, null);
    				ok = false;
    			}
    		}
    	}
    	
    	return ok;
    }

	public final class DBHashes
	{
		public String updatedHash = null;
		public String networkHash = null;
	}
}
