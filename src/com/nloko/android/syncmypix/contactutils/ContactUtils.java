//
//    ContactUtils.java is part of SyncMyPix
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

import com.nloko.android.Log;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Contacts.People;
import android.provider.Contacts.Photos;
import android.provider.ContactsContract.Contacts;

public class ContactUtils {
	private static final String TAG = "ContactServices";
	
	// proxy is created each call to allow garbage collection
	// slightly inefficient, but whatever
	public static InputStream getPhoto(ContentResolver cr, String id)
	{
		return ContactProxyFactory.create().getPhoto(cr, id);
	}
	
	public static void updatePhoto (ContentResolver cr, byte[] image, String id)
	{
		ContactProxyFactory.create().updatePhoto(cr, image, id);
	}
}
