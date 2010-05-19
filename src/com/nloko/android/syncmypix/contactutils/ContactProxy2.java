//
//    ContactProxy2.java is part of SyncMyPix
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

package com.nloko.android.syncmypix.contactutils;

import java.io.InputStream;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class ContactProxy2 implements IContactProxy {
	
	private final static String TAG = "ContactProxy2";

	public InputStream getPhoto(ContentResolver cr, String id) {
		if (cr == null || id == null) {
			return null;
		}
		
		Uri contact = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
		return Contacts.openContactPhotoInputStream(cr, contact);
	}
	
	public void updatePhoto(ContentResolver cr, byte[] photo, String id, boolean markDirty) { 
		if (cr == null || id == null) {
			return;
		}
		
		long rawId = queryForRawContactId(cr, Long.parseLong(id));
		if (rawId < 0) {
			return;
		}
		
		id = String.valueOf(rawId);
		ContentValues values = new ContentValues(); 
		int photoRow = -1; 
		
		// query for existing photo
		String where = ContactsContract.Data.RAW_CONTACT_ID + " == " + 
			id + " AND " + Data.MIMETYPE + "=='" + 
			ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'"; 
		
		Cursor cursor = cr.query(ContactsContract.Data.CONTENT_URI, null, where, null, null); 
		int idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID); 
		if(cursor.moveToFirst()){ 
			photoRow = cursor.getInt(idIdx); 
		} 
		cursor.close();
		if (photoRow < 0 && photo == null) {
			return;
		}
		
		values.put(ContactsContract.Data.RAW_CONTACT_ID, 
				id); 
		values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1); 
		values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photo); 
		values.put(ContactsContract.Data.MIMETYPE, 
				ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE); 
		// append CALLER_IS_SYNCADAPTER to prevent sync
		Uri uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photoRow);
		//Uri.Builder builder = ContactsContract.Data.CONTENT_URI.buildUpon();
		//builder.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
		Uri updateUri = uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
		if(photoRow >= 0){ 
			if (photo == null) {
				//cr.delete(builder.build(), ContactsContract.Data._ID 
				//	+ " = " + photoRow, null);
				cr.delete(updateUri, null, null);
			} else {
				//cr.update 
				//	(builder.build(), values, ContactsContract.Data._ID 
				//	+ " = " + photoRow, null);
				cr.update(updateUri, values, null, null);
			}
		} else { 
			//cr.insert(builder.build(), values);
			uri = ContactsContract.Data.CONTENT_URI;
			updateUri = uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
			cr.insert(updateUri, values);
		} 
	} 
	
	public boolean isContactUpdatable(ContentResolver cr, String id) {
		long rawId = queryForRawContactId(cr, Long.parseLong(id));
		return rawId > -1;
	}
	
	private long queryForRawContactId(ContentResolver cr, long contactId) {
        Cursor rawContactIdCursor = null;
        long rawContactId = -1;
        
        if (cr != null) {
	        try {
	            rawContactIdCursor = cr.query(RawContacts.CONTENT_URI,
	                    new String[] { RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE },
	                    RawContacts.CONTACT_ID + "=" + contactId, null, null);
	            if (rawContactIdCursor != null) {
//	            	Log.d(TAG, "JKLFJDLKSFJLSJFLSD");
	            	while(rawContactIdCursor.moveToNext() && rawContactId < 0) {
		            	String accountName = rawContactIdCursor.getString(rawContactIdCursor.getColumnIndex(RawContacts.ACCOUNT_NAME));
		            	String accountType = rawContactIdCursor.getString(rawContactIdCursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
		            	Log.d(TAG, accountName != null ? accountName : "empty");
		            	Log.d(TAG, accountType != null ? accountType : "empty");
		            	
		            	// a HACK to exclude read only accounts
		            	if (accountType == null || 
		            			accountType.toLowerCase().contains("google") ||
		            			accountType.toLowerCase().contains("exchange") ||
		            			accountType.toLowerCase().contains("htc.android.mail") ||
		            			accountType.toLowerCase().contains("htc.android.pcsc") ||
		            			accountType.length() == 0) {
		            		rawContactId = rawContactIdCursor.getLong(0);
		            	}
	            	}
	            }
	        } finally {
	            if (rawContactIdCursor != null) {
	                rawContactIdCursor.close();
	            }
	        }
        }
        return rawContactId;
    }
	
	public Uri getContentUri() {
		return ContactsContract.Contacts.CONTENT_URI;
	}
}
