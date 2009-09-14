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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.provider.Contacts.People;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class SyncResults extends Activity {

	Cursor cur;
	ListView listview;
	
	Looper downloadLooper;
	Handler handleBitmap;
	DownloadImageHandler downloadHandler;

	Bitmap contactImage;
	
	private final int LOADING_DIALOG = 0;
	private final int ZOOM_PIC = 1;
	
	private final int UNKNOWN_HOST_ERROR = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.results);	

		showDialog(LOADING_DIALOG);
		
        final ContentResolver resolver = getContentResolver();
        String[] projection = { 
        		Results._ID, 
        		Results.NAME, 
        		Results.DESCRIPTION, 
        		Results.PIC_URL,
        		Results.CONTACT_ID,
        		Sync.DATE_STARTED, 
        		Sync.DATE_COMPLETED };
        
        cur = resolver.query(Results.CONTENT_URI, projection, null, null, Results.DEFAULT_SORT_ORDER);
        startManagingCursor(cur);
        
        listview = (ListView) findViewById(R.id.resultList);
        
		ListAdapter adapter = new ResultsListAdapter(
                this, 
                R.layout.resultslistitem,  
                cur,                                    
                new String[] {Results.NAME, Results.DESCRIPTION },
                new int[] { R.id.text1, R.id.text2 } );    

        listview.setAdapter(adapter);      
        
        listview.setOnItemClickListener(new OnItemClickListener () {

			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				
				cur.moveToPosition(position);
				String url = cur.getString(cur.getColumnIndex(Results.PIC_URL));

				if (url != null) {
			
					setProgressBarIndeterminateVisibility(true);
					
					Message msg = downloadHandler.obtainMessage();
					msg.obj = url;
					downloadHandler.sendMessage(msg);
				}
			}
        	
        });

        handleBitmap = new Handler () {

			@Override
			public void handleMessage(Message msg) {
				Bitmap bitmap = (Bitmap) msg.obj;
				if (bitmap != null) {
					((SimpleCursorAdapter)listview.getAdapter()).notifyDataSetChanged();
					
					contactImage = bitmap;
					showDialog(ZOOM_PIC);
					
					setProgressBarIndeterminateVisibility(false);
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

	private Dialog showZoomDialog()
	{
		
		Dialog zoomedDialog = new Dialog(SyncResults.this);
		zoomedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		zoomedDialog.setContentView(R.layout.zoomedpic);
		zoomedDialog.setCancelable(true);
		
		final ImageView image = (ImageView)zoomedDialog.findViewById(R.id.image);

		//final Drawable d = contactImage.getDrawable();
		
		final int padding = 15;
		
		final int width  = contactImage.getWidth();
		final int height = contactImage.getHeight();

		int newWidth  = width;
		int newHeight = height;
		
		final int windowHeight = getWindowManager().getDefaultDisplay().getHeight();
		final int windowWidth  = getWindowManager().getDefaultDisplay().getWidth();

		boolean scale = false;
		float ratio;
		
		if (newHeight >= windowHeight) {
			ratio  =  (float)newWidth / (float)newHeight;
			newHeight = windowHeight - padding;
			newWidth  = Math.round(ratio * (float)newHeight);
			
			scale = true;
		}
		
		if (newWidth >= windowWidth) {
			ratio  = (float)newHeight / (float)newWidth;
			newWidth   = windowWidth - padding;
			newHeight  = Math.round(ratio * (float)newWidth);
			
			scale = true;
		}
		
		image.setImageBitmap(contactImage);
		
		if (scale) {
			Matrix m = new Matrix();
			m.postScale((float)newWidth / (float)width, (float)newHeight / (float)height);
			image.setImageMatrix(m);
			image.invalidate();
		}

		zoomedDialog.getWindow().setLayout(newWidth, newHeight);
		
		zoomedDialog.setOnCancelListener(new OnCancelListener() {

			public void onCancel(DialogInterface dialog) {
				removeDialog(ZOOM_PIC);
			}
			
		});
		
		return zoomedDialog;
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
			case ZOOM_PIC:
				return showZoomDialog();
		}
		
		return super.onCreateDialog(id);
	}


	@Override
	protected void onDestroy() {
		
		downloadLooper.quit();
		
		// TODO Auto-generated method stub
		super.onDestroy();

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
						
						ThumbnailCache.add(url, bitmap);
					}
				}
				catch (UnknownHostException ex) {
					mainHandler.sendEmptyMessage(UNKNOWN_HOST_ERROR);
				}
				catch (Exception e) {}
			}
		}	

	}
	
	private class ResultsListAdapter extends SimpleCursorAdapter
	{

		public ResultsListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			super.bindView(view, context, cursor);
			
			ImageView image = (ImageView) view.findViewById(R.id.contactImage);
			
			String id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
			String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
			String description = cursor.getString(cursor.getColumnIndex(Results.DESCRIPTION));
			
			if (id != null) {
				image.setImageBitmap(People.loadContactPhoto(getBaseContext(), 
						Uri.withAppendedPath(People.CONTENT_URI, id), 
						R.drawable.smiley_face, null));
			}
			else if (ThumbnailCache.contains(url)) {
				image.setImageBitmap(ThumbnailCache.get(url));
			}
			else if (description.equals("Contact not found")) {
				image.setImageResource(R.drawable.neutral_face);
			}
			else if (description.contains("Mutiple contacts processed")) {
				image.setImageResource(R.drawable.neutral_face);
			}
			else {
				image.setImageResource(R.drawable.sad_face);
			}
		}
	}
}
