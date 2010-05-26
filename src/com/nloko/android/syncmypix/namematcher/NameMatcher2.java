//
//    NameMatcher.java is part of SyncMyPix
//
//    Authors:
//		  Mike Hearn  <mike@plan99.net>
//        Neil Loknath <neil.loknath@gmail.com>
//
//	  Copyright (c) 2009 Mike Hearn
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

package com.nloko.android.syncmypix.namematcher;

import java.io.InputStream;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.PhoneContact;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

public class NameMatcher2 extends NameMatcher {

	protected final String TAG = "NameMatcher2";
	
	public NameMatcher2(Context context, InputStream diminutives,
			boolean withPhone) throws Exception {
		super(context, diminutives, withPhone);
	}

	@Override
	protected PhoneContact createFromCursor(Cursor cursor) {
    	if (cursor == null || cursor.isClosed()) {
    		return null;
    	}
    	
    	String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
		String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
		String lookup = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
		Log.d(TAG, "NameMatcher is processing contact " + name + " " + lookup);
		return new PhoneContact(id, name, lookup);
	}


	@Override
	protected Cursor doQuery(boolean withPhone) {
		Context context = mContext.get();
    	if (context == null) {
    		return null;
    	}
    	
    	String where = null;
        if (withPhone) {
        	where = ContactsContract.Contacts.HAS_PHONE_NUMBER +"=1";
        }
        
        Log.d(TAG, "Querying database for contacts..");
        
		return context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, 
				//new String[] { Contacts._ID, Contacts.DISPLAY_NAME, Contacts.HAS_PHONE_NUMBER}, 
				null,
				where, 
				null, 
				null);
	}
}
