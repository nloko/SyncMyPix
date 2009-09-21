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

import android.net.Uri;
import android.provider.BaseColumns;

public final class SyncMyPix {
	
    public static final String AUTHORITY = "com.nloko.provider.SyncMyPix";
    
	private SyncMyPix() {}
	
	public static enum ResultsDescription {
		NOTFOUND (0, "Contact not found"),
		UPDATED (1, "Picture updated"),
		SKIPPED_EXISTS (2, "Skipped: non-SyncMyPix picture exists"),
		SKIPPED_MULTIPLEFOUND (3, "Skipped: multiple contacts found"),
		MULTIPLEPROCESSED (4, "Multiple contacts processed; conflicts may have occurred"),
		DOWNLOAD_FAILED (5, "Picture download failed"),
		ERROR (6, "Error occurred during processing");
		
		private final int index;
		private final String msg;
		
		ResultsDescription(int index, String msg) {
			this.index = index;
			this.msg = msg;
		}
		
		public String getDescription() {
			return msg;
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