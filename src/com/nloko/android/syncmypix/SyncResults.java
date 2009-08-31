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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

// TODO add sync start and finish times
public class SyncResults extends Activity {

	Cursor cur;
	ListView listview;
	ImageView contactImage;
	Handler handleBitmap;
	DownloadImageThread downloadThread;
	
	boolean runThread = true;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.results);	
		
        ContentResolver resolver = getContentResolver();
        String[] projection = { 
        		Results._ID, 
        		Results.NAME, 
        		Results.DESCRIPTION, 
        		Results.PIC_URL,
        		Sync.DATE_STARTED, 
        		Sync.DATE_COMPLETED };
        
        cur = resolver.query(Results.CONTENT_URI, projection, null, null, Results.DEFAULT_SORT_ORDER);
        
        startManagingCursor(cur);
        
        listview = (ListView) findViewById(R.id.resultList);
                
        if (cur.moveToFirst()) {
	        long started = cur.getLong(cur.getColumnIndex(Sync.DATE_STARTED));
	        long completed = cur.getLong(cur.getColumnIndex(Sync.DATE_COMPLETED));
	        
	        TextView text1 = (TextView) (findViewById(R.id.started));
	        text1.setText(new Date(started).toString());
	        
	        TextView text2 = (TextView) (findViewById(R.id.completed));
	        text2.setText(new Date(completed).toString());
        }
		
		ListAdapter adapter = new SimpleCursorAdapter(
                this, 
                android.R.layout.two_line_list_item,  
                cur,                                    
                new String[] {Results.NAME, Results.DESCRIPTION },
                new int[] { android.R.id.text1, android.R.id.text2 } );    

        listview.setAdapter(adapter);      
        
        TextView header = (TextView) findViewById(R.id.resultsListHeader);
        header.setBackgroundColor(Color.DKGRAY);
                

        
        contactImage = (ImageView) findViewById(R.id.contactImage);
        contactImage.setImageResource(android.R.drawable.gallery_thumb);
        
        listview.setOnItemClickListener(new OnItemClickListener () {

			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				
				cur.moveToPosition(position);
				String url = cur.getString(cur.getColumnIndex(Results.PIC_URL));

				if (url != null && !url.equals("null") && !url.equals("")) {
					downloadThread.add(url);
					String name = cur.getString(cur.getColumnIndex(Results.NAME));
					TextView selectedName = (TextView) findViewById(R.id.selectedName);
					selectedName.setText(name);
				}
			}
        	
        });

        handleBitmap = new Handler () {

			@Override
			public void handleMessage(Message msg) {
				Bitmap bitmap = (Bitmap) msg.obj;
				if (bitmap != null) {
					contactImage.setImageBitmap(bitmap);
				}
			}
        };
        
        downloadThread = new DownloadImageThread(handleBitmap);
        downloadThread.start();
	}

	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		runThread = false;
	}


	private class DownloadImageThread extends Thread
	{
		private List<String> list;
		private Handler handler;
		
		@SuppressWarnings("unused")
		private DownloadImageThread() {}
		DownloadImageThread(Handler handler)
		{
			list = new ArrayList<String> ();
			this.handler = handler;
		}
		
		public void add(String url)
		{
			list.add(url);
		}
		
		public void run()
		{
			while (runThread) {
				while (!list.isEmpty()) {
					String url = list.get(0);
					if (url != null) {
						try {
							Bitmap bitmap = Utils.downloadPictureAsBitmap(url);
							if (bitmap != null) {
								Message msg = handler.obtainMessage();
								msg.obj = bitmap;
								handler.sendMessage(msg);
							}
						}
						catch (Exception e) {}
						finally {
							list.remove(0);
						}
					}
				}
			}
		}
		
	}
}
