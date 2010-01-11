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
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

public class ContactProxy2 implements IContactProxy {

	public InputStream getPhoto(ContentResolver cr, String id) {
		if (cr == null || id == null) {
			return null;
		}
		
		Uri contact = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
		return Contacts.openContactPhotoInputStream(cr, contact);
	}
	
	public void updatePhoto(ContentResolver cr, byte[] photo, String id) { 
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
		Uri.Builder builder = ContactsContract.Data.CONTENT_URI.buildUpon();
		builder.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
		
		if(photoRow >= 0){ 
			if (photo == null) {
				cr.delete(builder.build(), ContactsContract.Data._ID 
					+ " = " + photoRow, null);
			} else {
				cr.update 
					(builder.build(), values, ContactsContract.Data._ID 
					+ " = " + photoRow, null);
			}
		} else { 
			cr.insert(builder.build(), values); 
		} 
	} 
	
	private long queryForRawContactId(ContentResolver cr, long contactId) {
        Cursor rawContactIdCursor = null;
        long rawContactId = -1;
        
        if (cr != null) {
	        try {
	            rawContactIdCursor = cr.query(RawContacts.CONTENT_URI,
	                    new String[] {RawContacts._ID},
	                    RawContacts.CONTACT_ID + "=" + contactId, null, null);
	            if (rawContactIdCursor != null && rawContactIdCursor.moveToFirst()) {
	                // Just return the first one.
	                rawContactId = rawContactIdCursor.getLong(0);
	            }
	        } finally {
	            if (rawContactIdCursor != null) {
	                rawContactIdCursor.close();
	            }
	        }
        }
        return rawContactId;
    }
}
