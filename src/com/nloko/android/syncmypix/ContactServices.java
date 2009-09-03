//
//    ContactServices.java is part of SyncMyPix
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Photos;

public class ContactServices {

	public static Cursor getAllContacts (ContentResolver cr)
	{
		return getContact(cr, null);
	}
	
	public static Cursor getAllContacts (ContentResolver cr, String[] projection)
	{
		return getContact(cr, projection, null);
	}

	public static Cursor getContact (ContentResolver cr, String selection)
	{
		// Form an array specifying which columns to return. 
		String[] projection = new String[] {
			    People._ID,
			    People.NAME,
			    People.NUMBER
			    };

		return getContact(cr, projection, selection);
	}
	
	public static Cursor getContact (ContentResolver cr, String[] projection, String selection)
	{
		if (cr == null) {
			throw new IllegalArgumentException ("cr");
		}
		
		if (projection == null) {
			throw new IllegalArgumentException ("projection");
		}
		
		// Get the base URI for the People table in the Contacts content provider.
		Uri contacts =  People.CONTENT_URI;
	
		// Make the query. 
		return cr.query(contacts,
		                projection, // Which columns to return 
		                selection,       // Which rows to return (all rows)
		                null,       // Selection arguments (none)
		                null);
	}
	
	public static boolean find (Cursor c, String field, String value)
	{
		return false;
	}

	public static Cursor getPhoto (ContentResolver cr, String selection)
	{
		// Form an array specifying which columns to return. 
		String[] projection = new String[] {
			    Photos._ID,
			    Photos.LOCAL_VERSION,
			    Photos.PERSON_ID
			    };

		return getPhoto(cr, projection, selection);
	}
	
	public static Cursor getPhoto (ContentResolver cr, String[] projection, String selection)
	{
		if (cr == null) {
			throw new IllegalArgumentException ("cr");
		}
		
		if (projection == null) {
			throw new IllegalArgumentException ("projection");
		}
		
		// Get the base URI for the People table in the Contacts content provider.
		Uri contacts =  Photos.CONTENT_URI;
	
		// Make the query. 
		return cr.query(contacts,
		                projection, // Which columns to return 
		                selection,       // Which rows to return (all rows)
		                null,       // Selection arguments (none)
		                null);
	}
	
	public static void updateContactPhoto (ContentResolver cr, byte[] image, String id)
	{
		ContentValues values = new ContentValues();
        // we have to include this here otherwise the provider will set it to 1
        values.put("_sync_dirty", 0);
        values.put(Photos.DATA, image);
        //values.put(Photos.LOCAL_VERSION, "SyncMyPix");
        //values.put("_sync_version", "SyncMyPix");
        Uri photoUri = Uri.withAppendedPath(People.CONTENT_URI,
                "" + id + "/" + Photos.CONTENT_DIRECTORY);
        cr.update(photoUri, values, null, null);
        	
	}
}
