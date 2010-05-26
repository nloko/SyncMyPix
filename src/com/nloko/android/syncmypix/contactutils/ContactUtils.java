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

import com.nloko.android.syncmypix.PhoneContact;

import android.content.ContentResolver;
import android.net.Uri;

public final class ContactUtils {
	private static final String TAG = "ContactServices";

	private final IContactProxy mInstance = ContactProxyFactory.create();
	
	public InputStream getPhoto(ContentResolver cr, String id)
	{
		return mInstance.getPhoto(cr, id);
	}
	
	public boolean isContactUpdatable(ContentResolver cr, String id) {
		return mInstance.isContactUpdatable(cr, id);
	}
	
	public void updatePhoto (ContentResolver cr, byte[] image, String id)
	{
		updatePhoto(cr, image, id, false);
	}
	
	public void updatePhoto (ContentResolver cr, byte[] image, String id, boolean markDirty)
	{
		mInstance.updatePhoto(cr, image, id, markDirty);
	}
	
	public PhoneContact confirmContact(ContentResolver cr, String id, String lookup) {
		return mInstance.confirmContact(cr, id, lookup);
	}
	
	public String getLookup(ContentResolver resolver, Uri contact) {
		return mInstance.getLookup(resolver, contact);
	}
	
	public Uri getContentUri() {
		return mInstance.getContentUri();
	}
}
