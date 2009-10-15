//
//    SyncMyPix.java is part of SyncMyPix
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

import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

public final class SyncMyPix {
	
    public static final String AUTHORITY = "com.nloko.provider.SyncMyPix";
    
	private SyncMyPix() {}
	
	public static enum ResultsDescription {
		NOTFOUND (0, R.string.resultsdescription_notfound),
		UPDATED (1, R.string.resultsdescription_updated),
		SKIPPED_EXISTS (2, R.string.resultsdescription_skippedexists),
		SKIPPED_MULTIPLEFOUND (3, R.string.resultsdescription_skippedmultiplefound),
		SKIPPED_UNCHANGED (4, R.string.resultsdescription_skippedunchanged),
		MULTIPLEPROCESSED (5, R.string.resultsdescription_multipleprocessed),
		DOWNLOAD_FAILED (6, R.string.resultsdescription_downloadfailed),
		ERROR (7, R.string.resultsdescription_error);
		
		private final int index;
		private final int msg;
		
		ResultsDescription(int index, int msg) {
			this.index = index;
			this.msg = msg;
		}
		
		public String getDescription(Context context) {
			
			return context != null ? context.getString(msg) : null;
		}
		
		public int getIndex() {
			return index;
		}
	}
	
	public static final class Contacts implements BaseColumns {
		
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/contacts");
		
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.nloko.contact";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.nloko.contact";
		
		public static final String DEFAULT_SORT_ORDER = "_id ASC";
		
		public static final String PHOTO_HASH = "photo_hash";
		
		public static final String NETWORK_PHOTO_HASH = "network_photo_hash";
	}
	
	public static final class Results implements BaseColumns {

        private Results() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/results");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.nloko.result";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.nloko.result";

        public static final String DEFAULT_SORT_ORDER = "name ASC";

        public static final String NAME = "name";

        public static final String PIC_URL = "pic_url";

        public static final String DESCRIPTION = "description";
        
        public static final String CONTACT_ID = "contact_id";
        
        public static final String SYNC_ID = "sync_id";
    }
	
	public static final class Sync implements BaseColumns {
	
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/sync");
		
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.nloko.sync";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.nloko.sync";
		
		public static final String DATE_STARTED = "date_started";
		
		public static final String DATE_COMPLETED = "date_completed";
		
		public static final String SOURCE = "source";
	}

}