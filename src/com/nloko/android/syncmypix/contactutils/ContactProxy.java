//
//    ContactProxy.java is part of SyncMyPix
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

import com.nloko.android.syncmypix.PhoneContact;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.Contacts.People;
import android.provider.Contacts.Photos;

public class ContactProxy implements IContactProxy {
	public InputStream getPhoto(ContentResolver cr, String id) {
		if (cr == null || id == null) {
			return null;
		}
		
		Uri contact = Uri.withAppendedPath(People.CONTENT_URI, id);
		return People.openContactPhotoInputStream(cr, contact);
	}
	
	public boolean isContactUpdatable(ContentResolver cr, String id) {
		return true;
	}
	
	public PhoneContact confirmContact(ContentResolver cr, String id, String lookup) {
		return new PhoneContact(id, null, lookup);
	}
	
	public void updatePhoto (ContentResolver cr, byte[] image, String id, boolean markDirty) {
		if (cr == null || id == null) {
			return;
		}
		
		ContentValues values = new ContentValues();
        // we have to include this here otherwise the provider will set it to 1
		if (!markDirty) {
			values.put("_sync_dirty", 0);
		}
		
        values.put(Photos.DATA, image);
        
//        values.put("_sync_version", "SyncMyPix");
        
        Uri photoUri = Uri.withAppendedPath(People.CONTENT_URI,
                "" + id + "/" + Photos.CONTENT_DIRECTORY);
        cr.update(photoUri, values, null, null);
	}
	
	public String getLookup(ContentResolver resolver, Uri contact) {
		return null;
	}
	
	public Uri getContentUri() {
		return People.CONTENT_URI;
	}
}
