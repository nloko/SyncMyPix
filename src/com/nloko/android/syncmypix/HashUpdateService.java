//
//    HashUpdateService.java is part of SyncMyPix
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

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Contacts.People;

// TODO this is a dirty hack because after a sync, the phone syncs the pics with Google
// which changes the pic, and thus, changes the pic hash
public class HashUpdateService extends Service {

	private final static String TAG = "HashUpdateService";
	
	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
		
		hashThread = new HashThread();
		hashThread.start();
	}

	// just access directly. No IPC crap to deal with.
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
    	public HashUpdateService getService() {
            return HashUpdateService.this;
        }
    }
    
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return binder;
	}
	
	private boolean cancel = false;
	public void cancelUpdate()
	{
		cancel = true;
	}
	
	private boolean executing = false;
	public boolean isExecuting()
	{
		return executing;
	}
	
	private HashThread hashThread;
	
	private class HashThread extends Thread
	{
		HashThread () {}
		
		public void run()
		{
			Log.d(TAG, "Updating hashes after sync");
			executing = true;
			
			ContentResolver resolver = getContentResolver();
			Cursor cur = resolver.query(Contacts.CONTENT_URI, 
					new String[] { Contacts._ID, Contacts.PHOTO_HASH }, 
					null, 
					null, 
					null);
			
			while (cur.moveToNext() && !cancel) {
				String id = cur.getString(cur.getColumnIndex(Contacts._ID));
				Uri uri = Uri.withAppendedPath(People.CONTENT_URI, id);
				
				InputStream is = People.openContactPhotoInputStream(resolver, uri);
				String hash = Utils.getMd5Hash(Utils.getByteArrayFromInputStream(is));
				
				Uri contacts = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
				ContentValues values = new ContentValues();
				values.put(Contacts._ID, id);
				values.put(Contacts.PHOTO_HASH, hash);
				resolver.update(contacts, values, null, null);
			}
			
			Log.d(TAG, "Finished updating hashes after sync");
			executing = false;
			cancel = false;
			
			stopSelf();
		}
		
	}

}
