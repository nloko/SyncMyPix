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
import java.lang.ref.WeakReference;

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
	
	private final WeakReference<ContentResolver> mResolver;
	
	public SyncMyPixDbHelper(Context context)
	{
		mResolver = new WeakReference<ContentResolver>(context.getContentResolver());
	}

	public void deleteAllPictures()
	{
		deleteAllPictures(null);
	}
	
	public void deleteAllPictures(final DbHelperNotifier notifier)
	{
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		final Cursor cursor = resolver.query(Contacts.CONTENT_URI, 
				new String[] { Contacts._ID, Contacts.PHOTO_HASH },
				null,
				null, 
				null);
		
		Thread thread = new Thread(new Runnable() {
			public void run() {
				synchronized(SyncService.mSyncLock) {
					while(cursor.moveToNext()) {
						String id  = cursor.getString(cursor.getColumnIndex(Contacts._ID));
						String dbHash = cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_HASH));
						deletePicture(id, dbHash);
					}
					
					resolver.delete(Contacts.CONTENT_URI, null, null);
					resolver.delete(Results.CONTENT_URI, null, null);
					resolver.delete(Sync.CONTENT_URI, null, null);

				}
				
				if (notifier != null) {
					notifier.onUpdateComplete();
				}
				
				cursor.close();
			}
		});
		
		thread.start();
	}

	public void deletePicture(String id)
	{
		if (id == null) {
			throw new IllegalArgumentException("id");
		}
		
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		final Cursor cursor = resolver.query(Uri.withAppendedPath(Contacts.CONTENT_URI, id), 
				new String[] { Contacts._ID, Contacts.PHOTO_HASH },
				null,
				null, 
				null);
		
		if (cursor.moveToNext()) {
			String hash = cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_HASH));
			deletePicture(id, hash);
		}
	}
	
	public void deletePicture(String id, String dbHash)
	{
		if (id == null) {
			throw new IllegalArgumentException("id");
		} else if (dbHash == null) {
			throw new IllegalArgumentException("dbHash");
		}
		
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		Uri uri = Uri.withAppendedPath(People.CONTENT_URI, id);
		InputStream stream = People.openContactPhotoInputStream(resolver, uri);
		if (stream != null) {
			String hash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(stream));
			if (dbHash.equals(hash)) {
				ContactServices.updateContactPhoto(resolver, null, id);
			}
		}
	}
	
	public void deleteResults(String source)
	{
		if (source == null) {
    		throw new IllegalArgumentException("source");
    	}
		
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		Cursor cursor = resolver.query(Sync.CONTENT_URI,
						new String[] { Sync._ID, Sync.SOURCE }, 
						Sync.SOURCE + "='" + source + "'", 
						null, 
						null);
		
		while (cursor.moveToNext()) {
			String id = cursor.getString(cursor.getColumnIndex(Sync._ID));
			Uri uri = Uri.withAppendedPath(Sync.CONTENT_URI, id);
			resolver.delete(uri, null, null);
		}
		
		cursor.close();
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
		if (id == null) {
    		throw new IllegalArgumentException("id");
    	}
		
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
		Cursor cursor = resolver.query(uri,
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
			resolver.update(uri, values, null, null);
		} else {
			values.put(Contacts._ID, id);
			resolver.insert(Contacts.CONTENT_URI, values);
		}
		
		if (cursor != null) {
			cursor.close();
		}
	}
	
	public void updateLink(String id, SocialNetworkUser user, String source)
	{
		updateLink(id, user.uid, source);
	}
	
	public void updateLink(String id, String friendId, String source)
	{
		if (id == null) {
    		throw new IllegalArgumentException("id");
    	} 
		
		final ContentResolver resolver = mResolver.get();
		if (resolver == null) {
			return;
		}
		
		Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
		Cursor cursor = resolver.query(uri,
						new String[] { Contacts._ID }, 
						null, 
						null, 
						null);	
		
		ContentValues values = new ContentValues();
		values.put(Contacts.FRIEND_ID, friendId);
		values.put(Contacts.SOURCE, source);
		if (cursor.moveToFirst()) {
			resolver.update(uri, values, null, null);
		} else {
			values.put(Contacts._ID, id);
			resolver.insert(Contacts.CONTENT_URI, values);
		}
		
		if (cursor != null) {
			cursor.close();
		}
	}
	
	public boolean hasLink(String id, String source)
	{
		if (id == null) {
    		throw new IllegalArgumentException("id");
    	} else if (source == null) {
    		throw new IllegalArgumentException("source");
    	}
    	
    	final ContentResolver resolver = mResolver.get();
    	if (resolver == null) {
    		return false;
    	}
    	
    	Cursor cursor = resolver.query(Contacts.CONTENT_URI, 
				new String[] { Contacts._ID },
				Contacts._ID + "=" + id 
					+ " AND " + Contacts.SOURCE + "='" + source + "'"
					+ " AND " + Contacts.FRIEND_ID + " IS NOT NULL",
				null, 
				null);
    	
    	boolean answer = cursor.moveToNext();
    	cursor.close();
    	return answer;
	}
	
	public String getLinkedContact(String id, String source)
	{
		if (id == null) {
    		throw new IllegalArgumentException("id");
    	} else if (source == null) {
    		throw new IllegalArgumentException("source");
    	}
    	
    	final ContentResolver resolver = mResolver.get();
    	if (resolver == null) {
    		return null;
    	}
    	
    	Cursor cursor = resolver.query(Contacts.CONTENT_URI, 
				new String[] { Contacts._ID },
				Contacts.FRIEND_ID + "='" + id + "' AND " + Contacts.SOURCE + "='" + source + "'",
				null, 
				null);
    	
    	String contactId = null;
    	if (cursor.moveToNext()) {
    		contactId = cursor.getString(cursor.getColumnIndex(Contacts._ID));
    	}

    	cursor.close();
    	return contactId;
	}
	
    public DBHashes getHashes(String id)
    {
    	if (id == null) {
    		throw new IllegalArgumentException("id");
    	}
    	
    	final ContentResolver resolver = mResolver.get();
    	if (resolver == null) {
    		return null;
    	}
    	
    	Uri syncUri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
		
    	Cursor syncC = resolver.query(syncUri, 
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
    	
    	final ContentResolver resolver = mResolver.get();
    	if (resolver == null) {
    		return false;
    	}
    	
    	boolean ok = true;
    	
    	Uri syncUri = Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id);
    	    	
    	if (skipIfExists) {
    		// not tracking any hashes for contact and photo is set for contact
    		if (dbHash == null && contactHash != null) {
    			ok = false;
    		} else if (contactHash != null) {
    			// we are tracking a hash and there is a photo for this contact
    			Log.d(TAG, String.format("dbhash %s hash %s", dbHash, contactHash));
    			// hashes do not match, so we don't need to track this hash anymore
    			if (!contactHash.equals(dbHash)) {
   					resolver.delete(syncUri, null, null);
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
