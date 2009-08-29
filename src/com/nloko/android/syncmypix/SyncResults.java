//
//    ResultsList.java is part of SyncMyPix
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

import com.nloko.android.syncmypix.SyncMyPix.Results;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;

// TODO add sync start and finish times
public class SyncResults extends ListActivity {

	Cursor cur;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        ContentResolver resolver = getContentResolver();
        String[] projection = { Results._ID, Results.NAME, Results.DESCRIPTION };
        cur = resolver.query(Results.CONTENT_URI, projection, null, null, Results.DEFAULT_SORT_ORDER);
        startManagingCursor(cur);

        ListAdapter adapter = new SimpleCursorAdapter(
                this, 
                android.R.layout.two_line_list_item,  
                cur,                                    
                new String[] {Results.NAME, Results.DESCRIPTION },
                new int[] { android.R.id.text1, android.R.id.text2 } );    

        setListAdapter(adapter);

	}

}
