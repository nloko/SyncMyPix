//
//    SyncWakeLock.java is part of SyncMyPix
//
//    Authors:
//        Neil Loknath <neil.loknath@gmail.com>
//
//    Copyright (c) 2010 Neil Loknath
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

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class SyncWakeLock {
	private static WakeLock mWakeLock;
	
	public static void acquireWakeLock(Context context) {
		if (context == null) {
			return;
		}
		
		if (mWakeLock == null) {
    		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncMyPix WakeLock");
            mWakeLock.acquire();
    	}
	}
	
	public static void releaseWakeLock() {
    	if (mWakeLock != null) {
    		mWakeLock.release();
    		mWakeLock = null;
    	}
	}
}
