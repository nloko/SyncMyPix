//
//    SyncResults.java is part of SyncMyPix
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

import java.net.UnknownHostException;
import java.util.Date;

import com.nloko.android.Utils;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


// TODO add sync start and finish times
public class SyncResults extends Activity {

	Cursor cur;
	ListView listview;
	ImageView contactImage;
	
	Looper downloadLooper;
	Handler handleBitmap;
	DownloadImageHandler downloadHandler;
	
	private final int LOADING_DIALOG = 0;
	
	private final int UNKNOWN_HOST_ERROR = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.results);	

		showDialog(LOADING_DIALOG);
		
        final ContentResolver resolver = getContentResolver();
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
        
		ListAdapter adapter = new SimpleCursorAdapter(
                this, 
                android.R.layout.two_line_list_item,  
                cur,                                    
                new String[] {Results.NAME, Results.DESCRIPTION },
                new int[] { android.R.id.text1, android.R.id.text2 } );    

        listview.setAdapter(adapter);      
        
        contactImage = (ImageView) findViewById(R.id.contactImage);
        contactImage.setImageResource(android.R.drawable.gallery_thumb);
        
        listview.setOnItemClickListener(new OnItemClickListener () {

			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				
				cur.moveToPosition(position);
				String url = cur.getString(cur.getColumnIndex(Results.PIC_URL));

				if (url != null && !url.equals("null") && !url.equals("")) {
					contactImage.setImageResource(android.R.drawable.gallery_thumb);
					
					Message msg = downloadHandler.obtainMessage();
					msg.obj = url;
					downloadHandler.sendMessage(msg);
					
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
				else {
					handleWhat(msg.what);
				}
			}
			
			private void handleWhat(int what) {
				switch (what) {
					case UNKNOWN_HOST_ERROR:
						Toast.makeText(SyncResults.this, "Unable to resolve host. Do you have network connectivity?", Toast.LENGTH_LONG).show();
						break;
				}
			}
        };
        
        HandlerThread downloadThread = new HandlerThread("ImageDownload");
        downloadThread.start();
        
        downloadLooper = downloadThread.getLooper();
        downloadHandler = new DownloadImageHandler(downloadLooper, handleBitmap);
        
        new InitializeResultsThread(Looper.myQueue(), cur).start();
	}

	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case LOADING_DIALOG:
				ProgressDialog progress = new ProgressDialog(this);
				progress.setCancelable(true);
				progress.setMessage("Loading...");
				progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				return progress;
		}
		
		return super.onCreateDialog(id);
	}


	@Override
	protected void onDestroy() {
		
		downloadLooper.quit();
		
		// TODO Auto-generated method stub
		super.onDestroy();

	}


	protected void setImageAnimation()
	{
		if (contactImage != null) {
			Animation a=new RotateAnimation(0, 360);

			a.setRepeatCount(Animation.INFINITE);
			a.setDuration(10000);
			// maybe other configuration as needed

			contactImage.startAnimation(a);

		}
	}
	
	private class InitializeResultsThread extends Thread
	{
		private MessageQueue queue;
		private Cursor cur;
		InitializeResultsThread (MessageQueue queue, Cursor cur)
		{
			this.queue = queue;
			this.cur = cur;
		}
		
		public void run()
		{
			if (cur.moveToFirst()) {
				long started = cur.getLong(cur.getColumnIndex(Sync.DATE_STARTED));
				long completed = cur.getLong(cur.getColumnIndex(Sync.DATE_COMPLETED));
				
				final String dateStarted = new Date(started).toString();
				final String dateCompleted = new Date(completed).toString();
				
				final TextView text1 = (TextView) (findViewById(R.id.started));
				final TextView text2 = (TextView) (findViewById(R.id.completed));
				
				final TextView label1 = (TextView) findViewById(R.id.startedLabel);
				final TextView label2 = (TextView) findViewById(R.id.completedLabel);

				queue.addIdleHandler(new MessageQueue.IdleHandler () {

					public boolean queueIdle() {
						
						text1.setText(dateStarted);
						text2.setText(dateCompleted);
						
						label1.setVisibility(View.VISIBLE);
						label2.setVisibility(View.VISIBLE);
				
				        removeDialog(LOADING_DIALOG);
						return false;
					}
		        	
		        });
			}
			else {
				removeDialog(LOADING_DIALOG);
			}
		}
	}
	
	
	private class DownloadImageHandler extends Handler
	{
		private Handler mainHandler;
		
		DownloadImageHandler(Looper looper, Handler handler)
		{
			super(looper);
			mainHandler = handler;
		}
		
		@Override
		public void handleMessage(Message msg)
		{
			String url = (String) msg.obj;
			if (url != null) {
				try {
					Bitmap bitmap = Utils.downloadPictureAsBitmap(url);
					if (bitmap != null) {
						Message mainMsg = mainHandler.obtainMessage();
						mainMsg.obj = bitmap;
						mainHandler.sendMessage(mainMsg);
					}
				}
				catch (UnknownHostException ex) {
					mainHandler.sendEmptyMessage(UNKNOWN_HOST_ERROR);
				}
				catch (Exception e) {}
			}
		}	

	}
}
