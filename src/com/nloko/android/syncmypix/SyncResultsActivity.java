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

import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.Date;

import com.nloko.android.Log;
import com.nloko.android.ThumbnailCache;
import com.nloko.android.Utils;
import com.nloko.android.ThumbnailCache.ImageProvider;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.ResultsDescription;
import com.nloko.android.syncmypix.SyncMyPix.Sync;
import com.nloko.android.syncmypix.graphics.CropImage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.provider.Contacts.People;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class SyncResultsActivity extends Activity {

	private SyncMyPixDbHelper mDbHelper;
	private ListView mListview;
	
	private Handler mMainHandler;
	private DownloadImageHandler mDownloadHandler;
	private LoadThumbnailsThread mThumbnailThread;
	private InitializeResultsThread mInitResultsThread;
	
	private Bitmap mContactImage;
	private ThumbnailCache mCache;

	private Uri mUriOfSelected = null;
	
	private final int MENU_HELP = 0;
	private final int MENU_FILTER = 1;
	private final int MENU_FILTER_ALL = 2;
	private final int MENU_FILTER_NOTFOUND = 3;
	private final int MENU_FILTER_UPDATED = 4;
	private final int MENU_FILTER_SKIPPED = 5;
	private final int MENU_FILTER_ERROR = 6;
	private final int MENU_DELETE = 7;

	// dialogs
	private final int LOADING_DIALOG = 0;
	private final int ZOOM_PIC = 1;
	private final int UPDATE_CONTACT = 3;
	private final int HELP_DIALOG = 4;
	private final int DELETE_DIALOG = 5;
	private final int DELETING = 6;
	
	private static final String TAG = "SyncResults";
	
	private final int UNKNOWN_HOST_ERROR = 2;
	
	private final int CONTEXTMENU_CROP = 3;
	private final int CONTEXTMENU_SELECT_CONTACT = 4;
	private final int CONTEXTMENU_VIEW_CONTACT = 5;
	
	private final int PICK_CONTACT    = 6;
	private final int REQUEST_CROP_PHOTO    = 7;
	
	private final String[] mProjection = { 
    				Results._ID, 
    				Results.NAME, 
    				Results.DESCRIPTION, 
    				Results.PIC_URL,
    				Results.CONTACT_ID,
    				Sync.DATE_STARTED, 
    				Sync.DATE_COMPLETED };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		showDialog(LOADING_DIALOG);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.results);	
		
		mCache = ThumbnailCache.create();
		mCache.setDefaultImage(BitmapFactory.decodeResource(getResources(), R.drawable.default_face));
		
		mDbHelper = new SyncMyPixDbHelper(getApplicationContext());
		
        Cursor cursor = managedQuery(Results.CONTENT_URI, mProjection, null, null, Results.DEFAULT_SORT_ORDER);
        
        mListview = (ListView) findViewById(R.id.resultList);
        
		final SimpleCursorAdapter adapter = new ResultsListAdapter(
                this, 
                R.layout.resultslistitem,  
                cursor,                                    
                new String[] {Results.NAME, Results.DESCRIPTION },
                new int[] { R.id.text1, R.id.text2 } );    

		
        mListview.setAdapter(adapter);
        
        mCache.setImageProvider(new ImageProvider() {
			public boolean onImageRequired(String url) {
				if (mThumbnailThread != null) {
					Log.d(TAG, "restarting thumbnail thread");
					mThumbnailThread.restart();
					return true;
				}
		        return false;
			}
        });
        
        mListview.setOnItemLongClickListener(new OnItemLongClickListener() {
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {

				return false;
			}
        });
        
        mListview.setOnItemClickListener(new OnItemClickListener () {
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				
				Cursor cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				cursor.moveToPosition(position);
				
				String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
				if (url != null) {
					setProgressBarIndeterminateVisibility(true);
					
					Message msg = mDownloadHandler.obtainMessage();
					msg.what = ZOOM_PIC;
					msg.obj = url;
					mDownloadHandler.sendMessage(msg);
				}
			}
        });

        mListview.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				
				int position = ((AdapterContextMenuInfo)menuInfo).position;
				Cursor cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				
				if (cursor.moveToPosition(position)) {
					String id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
					String name = cursor.getString(cursor.getColumnIndex(Results.NAME));
					
					if (url != null) {
						menu.setHeaderTitle(name);
						if (id != null) {
							menu.add(0, CONTEXTMENU_VIEW_CONTACT, Menu.NONE, R.string.syncresults_menu_viewsynced);
							menu.add(0, CONTEXTMENU_CROP, Menu.NONE, R.string.syncresults_menu_crop);
						}
						
		                menu.add(0, CONTEXTMENU_SELECT_CONTACT, Menu.NONE, R.string.syncresults_menu_addpicture);
					}
				}
			}
        });
   
        mMainHandler = new Handler () {
			@Override
			public void handleMessage(Message msg) {
				Bitmap bitmap = (Bitmap) msg.obj;
				if (bitmap != null) {
					((SimpleCursorAdapter)mListview.getAdapter()).notifyDataSetChanged();
					mContactImage = bitmap;
					showDialog(ZOOM_PIC);
				}
				
				setProgressBarIndeterminateVisibility(false);
				handleWhat(msg);
			}
			
			private void handleWhat(Message msg) {
				switch (msg.what) {
					case UNKNOWN_HOST_ERROR:
						Toast.makeText(SyncResultsActivity.this, R.string.syncresults_networkerror, Toast.LENGTH_LONG).show();
						break;
				}
			}
        };
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}

	@Override
	protected void onPause() {
		super.onPause();
		mDownloadHandler.stopRunning();
		
		if (mInitResultsThread != null) {
			mInitResultsThread.stopRunning();
			mInitResultsThread.closeQuery();
		}
		// save battery life by killing downloader thread
		mCache.togglePauseOnDownloader(true);
	}
	 
	@Override
	protected void onResume() {
		super.onResume();
		mCache.togglePauseOnDownloader(false);
	}

	@Override
	protected void onStart() {
		super.onStart();
        HandlerThread downloadThread = new HandlerThread("ImageDownload");
        downloadThread.start();
        mDownloadHandler = new DownloadImageHandler(this, downloadThread.getLooper(), mMainHandler);
        
        if (mInitResultsThread == null) {
        	mInitResultsThread = new InitializeResultsThread(this, Looper.myQueue());
        	mInitResultsThread.start();
        }
	}

	@Override
	protected void onDestroy() {
		if (mThumbnailThread != null) {
			mThumbnailThread.stopRunning();
			mThumbnailThread.closeQuery();
		}
		
		mCache.destroy();
		super.onDestroy();
	}
		
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		 MenuItem item;

		 item = menu.add(0, MENU_HELP, 0, "Help");
		 item.setIcon(android.R.drawable.ic_menu_help);

		 SubMenu options = menu.addSubMenu(0, MENU_FILTER, 0, R.string.syncresults_filterButton);
		 options.add(0, MENU_FILTER_ALL, 0, "All");
		 options.add(0, MENU_FILTER_ERROR, 0, "Errors");
		 options.add(0, MENU_FILTER_NOTFOUND, 0, "Not found");
		 options.add(0, MENU_FILTER_SKIPPED, 0, "Skipped");
		 options.add(0, MENU_FILTER_UPDATED, 0, "Updated");
		 
		 options.setIcon(android.R.drawable.ic_menu_sort_alphabetically);

		 item = menu.add(0, MENU_DELETE, 0, R.string.syncresults_deleteButton);
		 item.setIcon(android.R.drawable.ic_menu_delete);
		 
		 return true;
	 }
	 
	 @Override
	 public boolean onOptionsItemSelected(MenuItem item) {
		 
		 SimpleCursorAdapter adapter = (SimpleCursorAdapter)mListview.getAdapter();
		 
		 switch (item.getItemId()) {
		 case MENU_HELP:
			 showDialog(HELP_DIALOG);
			 return true;
		 case MENU_FILTER_ALL:
			 adapter.getFilter().filter(null);
			 return true;
		 case MENU_FILTER_ERROR:
			 adapter.getFilter().filter("'" + ResultsDescription.ERROR.getDescription(this) + "'," +
					 "'" + ResultsDescription.DOWNLOAD_FAILED.getDescription(this) + "'");
			 return true;
		 case MENU_FILTER_NOTFOUND:
			 adapter.getFilter().filter("'" + ResultsDescription.NOTFOUND.getDescription(this) + "'");
			 return true;
		 case MENU_FILTER_UPDATED:
			 adapter.getFilter().filter("'" + ResultsDescription.UPDATED.getDescription(this) + "'," +
					 "'" + ResultsDescription.MULTIPLEPROCESSED.getDescription(this) + "'");
			 return true;
		 case MENU_FILTER_SKIPPED:
			 adapter.getFilter().filter("'" + ResultsDescription.SKIPPED_EXISTS.getDescription(this) + "'," +
					 "'" + ResultsDescription.SKIPPED_UNCHANGED.getDescription(this) + "'," +
					 "'" + ResultsDescription.SKIPPED_MULTIPLEFOUND.getDescription(this) + "'");
			 return true;
			 
		 case MENU_DELETE:
			 showDialog(DELETE_DIALOG);
			 return true;
		
		 }

		 return false;
	 }

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		
		int position;
		Cursor cursor;
		Intent intent;
		String id;
		
		switch (item.getItemId()) {
			case CONTEXTMENU_VIEW_CONTACT:
				position = ((AdapterContextMenuInfo)menuInfo).position;
				cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				
				if (cursor.moveToPosition(position)) {
					id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					if (id != null) {
						intent = new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(People.CONTENT_URI, id));
						startActivity(intent);
					}
				}
				
				return true;
				
			case CONTEXTMENU_SELECT_CONTACT:
				
				position = ((AdapterContextMenuInfo)menuInfo).position;
				mUriOfSelected = getResultsUriFromListPosition(position);
				
				intent = new Intent(Intent.ACTION_PICK, People.CONTENT_URI);  
				startActivityForResult(intent, PICK_CONTACT);  
				return true;
				
			case CONTEXTMENU_CROP:
				
				position = ((AdapterContextMenuInfo)menuInfo).position;
				mUriOfSelected = getResultsUriFromListPosition(position);
				
				cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				
				if (cursor.moveToPosition(position)) {
					id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					crop(id);
				}
				
				return true;
		}
		
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != Activity.RESULT_OK) {
			return;
		}
		
		Uri contactData;
		
		switch (requestCode) {  
			case PICK_CONTACT:  
				contactData = data.getData();  
	            updateContactWithSelection(contactData);
			
				break;
			
			case REQUEST_CROP_PHOTO:  
				
				ContentResolver resolver = getContentResolver();
				Cursor cursor = resolver.query(mUriOfSelected, 
						new String[] { Results.CONTACT_ID, Results.PIC_URL }, 
						null, 
						null, 
						null);
				
				ResultsListAdapter adapter = (ResultsListAdapter)mListview.getAdapter();
				
				if (cursor.moveToFirst()) {
					String id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
					
					Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");
					byte[] bytes = Utils.bitmapToJpeg(bitmap, 100);
					
					ContactServices.updateContactPhoto(getContentResolver(), bytes, id);
					mDbHelper.updateHashes(id, null, bytes);
					
					mCache.add(url, bitmap);
					adapter.notifyDataSetChanged();
				}
				
				break;
		}
	}

	private Uri getResultsUriFromListPosition(int pos)
	{
		Cursor cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
		
		if (cursor != null && cursor.moveToPosition(pos)) {
			String id = cursor.getString(cursor.getColumnIndex(Results._ID));
			return Uri.withAppendedPath(Results.CONTENT_URI, id);
		}
		
		return null;
	}
	
	private void crop(String id)
	{
		// launch cropping activity
		Intent intent = new Intent("com.android.camera.action.CROP");

		intent.setClass(getApplicationContext(), CropImage.class);
		//intent.putExtra("data", bitmap);
		intent.setData(Uri.withAppendedPath(People.CONTENT_URI, id));
		intent.putExtra("crop", "true");
		intent.putExtra("aspectX", 1);
		intent.putExtra("aspectY", 1);
		intent.putExtra("outputX", 96);
		intent.putExtra("outputY", 96);
		intent.putExtra("return-data", true);
		startActivityForResult(intent, REQUEST_CROP_PHOTO);
	}
	
	private void updateContactWithSelection(Uri contact)
	{
		final ContentResolver resolver = getContentResolver();
		
		Cursor cursor = resolver.query(mUriOfSelected, 
				new String[] { Results._ID, Results.PIC_URL }, 
				null, 
				null, 
				null);
		
		if (cursor.moveToFirst()) {
			
			showDialog(UPDATE_CONTACT);
			
			final Uri contactUri = contact;

			final long id  = cursor.getLong(cursor.getColumnIndex(Results._ID));
			final String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
		
			Thread thread = new Thread(new Runnable() {

				public void run() {
					try {
						Bitmap bitmap = Utils.downloadPictureAsBitmap(url);
						if (bitmap != null) {

							final String contactId = contactUri.getPathSegments().get(1);
							
							byte[] bytes = Utils.bitmapToJpeg(bitmap, 100);
							ContactServices.updateContactPhoto(resolver, bytes, contactId);
							mDbHelper.updateHashes(contactId, bytes, bytes);
							
							mCache.add(url, bitmap);
							
							ContentValues values = new ContentValues();
							values.put(Results.DESCRIPTION, ResultsDescription.UPDATED.getDescription(getBaseContext()));
							values.put(Results.CONTACT_ID, Long.parseLong(contactId));
							
							resolver.update(Uri.withAppendedPath(Results.CONTENT_URI, Long.toString(id)), 
									values, 
									null, 
									null);
							
							runOnUiThread(new Runnable() {

								public void run() {
									//((SimpleCursorAdapter)mListview.getAdapter()).notifyDataSetChanged();
									crop(contactId);
								}

							});

						}
					}
					catch (UnknownHostException ex) {
						mMainHandler.sendEmptyMessage(UNKNOWN_HOST_ERROR);
					}
					catch (Exception e) {}
					finally {
						runOnUiThread(new Runnable() {

							public void run() {
								dismissDialog(UPDATE_CONTACT);
							}
						});
					}
				}
			});
			
			thread.start();
		}
	}
	
	private Dialog showZoomDialog()
	{
		Dialog zoomedDialog = new Dialog(SyncResultsActivity.this);
		zoomedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		zoomedDialog.setContentView(R.layout.zoomedpic);
		zoomedDialog.setCancelable(true);
		
		final ImageView image = (ImageView)zoomedDialog.findViewById(R.id.image);

		final int padding = 15;
		
		final int width  = mContactImage.getWidth();
		final int height = mContactImage.getHeight();

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
		
		image.setImageBitmap(mContactImage);
		
		if (scale) {
			Matrix m = new Matrix();
			m.postScale((float)newWidth / (float)width, (float)newHeight / (float)height);
			image.setImageMatrix(m);
			
			zoomedDialog.getWindow().setLayout(newWidth, newHeight);
		}
		
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
				progress.setMessage(getString(R.string.syncresults_loadingDialog));
				progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				return progress;
			case ZOOM_PIC:
				return showZoomDialog();
			case UPDATE_CONTACT:
				ProgressDialog sync = new ProgressDialog(this);
				sync.setCancelable(false);
				sync.setMessage(getString(R.string.syncresults_syncDialog));
				sync.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				return sync;
			case HELP_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.syncresults_helpDialog)
					   .setIcon(android.R.drawable.ic_dialog_info)
					   .setMessage(R.string.results_help_msg)
				       .setCancelable(false)
				       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               removeDialog(HELP_DIALOG);
				           }
				       });
				AlertDialog help = builder.create();
				return help;
				
			case DELETE_DIALOG:
				AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(this);
				deleteBuilder.setTitle(R.string.syncresults_deleteDialog)
					   .setIcon(android.R.drawable.ic_dialog_alert)
					   .setMessage(R.string.syncresults_deleteDialog_msg)
				       .setCancelable(false)
				       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               removeDialog(DELETE_DIALOG);
				               
				               showDialog(DELETING);
				               mDbHelper.deleteAllPictures(new DbHelperNotifier() {

				            	   public void onUpdateComplete() {
				            		   runOnUiThread(new Runnable() {
				            			   public void run() {
				            				   dismissDialog(DELETING);
				            				   Toast.makeText(SyncResultsActivity.this,
													R.string.syncresults_deleted, 
													Toast.LENGTH_LONG).show();
											
				            				   finish();
				            			   }
				            		   });
				            	   }
				               });
				           }
				       })
				       .setNegativeButton("No", new DialogInterface.OnClickListener() {
				    	   public void onClick(DialogInterface dialog, int id) {
				    		   removeDialog(DELETE_DIALOG);
				    	   }
				       });
				
				AlertDialog delete = deleteBuilder.create();
				return delete;
			
			case DELETING:
				ProgressDialog deleting = new ProgressDialog(this);
				deleting.setCancelable(false);
				deleting.setMessage(getString(R.string.syncresults_deletingDialog));
				deleting.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				return deleting;
			
		}
		
		return super.onCreateDialog(id);
	}

	private static class InitializeResultsThread extends Thread
	{
		private boolean running = true;
		private MessageQueue queue;
		private Cursor cursor;
		
		private final WeakReference<SyncResultsActivity> mActivity;
		
		InitializeResultsThread (SyncResultsActivity activity, MessageQueue queue)
		{
			mActivity = new WeakReference<SyncResultsActivity>(activity);
			this.queue = queue;
			ensureQuery();
		}
		
		private void ensureQuery()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			cursor = activity.getContentResolver().query(Results.CONTENT_URI, 
					new String[] { Sync.DATE_STARTED, Sync.DATE_COMPLETED }, 
					null, 
					null, 
					null);
		}
		
		public void closeQuery()
		{
			synchronized(this) {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
		
		public void stopRunning ()
		{
			running = false;
		}
		
		public void run()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			long started, completed;
			ensureQuery();
			
			if (running && cursor.moveToFirst()) {
				synchronized(this) {
					started = cursor.getLong(cursor.getColumnIndex(Sync.DATE_STARTED));
					completed = cursor.getLong(cursor.getColumnIndex(Sync.DATE_COMPLETED));
				}
				
				final String dateStarted = new Date(started).toString();
				final String dateCompleted = new Date(completed).toString();
				
				final TextView text1 = (TextView) activity.findViewById(R.id.started);
				final TextView text2 = (TextView) activity.findViewById(R.id.completed);
				
				final TextView label1 = (TextView) activity.findViewById(R.id.startedLabel);
				final TextView label2 = (TextView) activity.findViewById(R.id.completedLabel);

				queue.addIdleHandler(new MessageQueue.IdleHandler () {

					public boolean queueIdle() {
						
						text1.setText(dateStarted);
						text2.setText(dateCompleted);
						
						label1.setVisibility(View.VISIBLE);
						label2.setVisibility(View.VISIBLE);
				
						activity.mThumbnailThread = new LoadThumbnailsThread(activity);
				        activity.mThumbnailThread.start();
				        
				        activity.removeDialog(activity.LOADING_DIALOG);
						return false;
					}
		        });
			}
			else {
				activity.removeDialog(activity.LOADING_DIALOG);
			}
		}
	}
	
	private static class LoadThumbnailsThread extends Thread
	{
		private final WeakReference<SyncResultsActivity> mActivity;
		private final String where;

		private Cursor cursor;
		
		private boolean running = true;
 		private boolean notified = false;
		
		public LoadThumbnailsThread(SyncResultsActivity activity)
		{
			mActivity = new WeakReference<SyncResultsActivity>(activity);
			where = Results.DESCRIPTION + " IN ('" +
				ResultsDescription.UPDATED.getDescription(activity) + "','" +
				ResultsDescription.SKIPPED_UNCHANGED.getDescription(activity) + "','" +
				ResultsDescription.MULTIPLEPROCESSED.getDescription(activity) + "')";
	        ensureQuery();
		}
		
		private void ensureQuery()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			String[] projection = { 
	        		Results._ID, 
	        		Results.CONTACT_ID,
	        		Results.PIC_URL };
			
			if (cursor == null || cursor.isClosed()) {
				cursor = activity.getContentResolver().query(Results.CONTENT_URI, 
		        		projection, 
		        		where, 
		        		null, 
		        		Results.DEFAULT_SORT_ORDER);
			}
		}
		
		public void closeQuery()
		{
			synchronized(this) {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
		
		public void restart()
		{
			notified = true;
			run();
		}

		public void stopRunning()
		{
			running = false;
		}
		
		private void refreshCursor()
		{
			ensureQuery();
			cursor.requery();
			cursor.moveToPosition(-1);
		}
		
		public void run()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			boolean wasNotified = false;
			boolean updated = false;
			Bitmap bitmap = null;
			String url = null;
			String id = null;
			long contactId = 0;
			
			if (notified) {
				refreshCursor();
				notified = false;
				wasNotified = true;
			} else {
				ensureQuery();
			}
			
			while(running && !notified && cursor.moveToNext()) {
				synchronized(this) {
					id = cursor.getString(cursor.getColumnIndex(Results._ID));
					contactId = cursor.getLong(cursor.getColumnIndex(Results.CONTACT_ID));
					url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
				}
				
				if (!activity.mCache.contains(url) && contactId > 0) {
					bitmap = People.loadContactPhoto(activity.getApplicationContext(), 
							Uri.withAppendedPath(People.CONTENT_URI, Long.toString(contactId)), 
							0, null);
					
					if (bitmap != null) {
						activity.mCache.add(url, bitmap);
						//Log.d(TAG, "added back " + url + " to cache");
						
						if (wasNotified) {
							// HACK to force notifyDatasetUpdated() to be honoured
							ContentValues values = new ContentValues();
							values.put(Results.PIC_URL, url);
							activity.getContentResolver().update(Uri.withAppendedPath(Results.CONTENT_URI, id), 
									values, 
									null, 
									null);
						}
						
						updated = true;
					}
				}
			}
			
			if (updated) {
				activity.runOnUiThread(new Runnable() {
					public void run() {
						Log.d(TAG, "listview notified");
						((ResultsListAdapter)activity.mListview.getAdapter()).notifyDataSetChanged();
					}
				});
			}
		}
	}
	
	private static class DownloadImageHandler extends Handler
	{
		private final WeakReference<SyncResultsActivity> mActivity;
		private boolean running = true;
		private Handler mainHandler;
		
		DownloadImageHandler(SyncResultsActivity activity, Looper looper, Handler handler)
		{
			super(looper);
			mActivity = new WeakReference<SyncResultsActivity>(activity);
			mainHandler = handler;
		}
		
		public void stopRunning()
		{
			synchronized(this) {
				getLooper().quit();
				running = false;
			}
		}
		
		@Override
		public void handleMessage(Message msg)
		{
			final SyncResultsActivity activity = mActivity.get();
			String url = (String) msg.obj;
					
			if (url != null) {
				try {
					Bitmap bitmap = Utils.downloadPictureAsBitmap(url);
					synchronized(this) {
						if (running && bitmap != null) {
							Message mainMsg = activity.mMainHandler.obtainMessage();
							
							mainMsg.obj = bitmap;
							mainMsg.what = msg.what;
							activity.mMainHandler.sendMessage(mainMsg);
							
							if (!activity.mCache.contains(url)) {
								activity.mCache.add(url, bitmap);
							}
						}
					}
				}
				catch (UnknownHostException ex) {
					activity.mMainHandler.sendEmptyMessage(activity.UNKNOWN_HOST_ERROR);
				} 
				catch (Exception e) {}
			}
		}	

	}
	
	private static class ResultsListAdapter extends SimpleCursorAdapter
	{
		private final WeakReference<SyncResultsActivity> mActivity;
		public ResultsListAdapter(SyncResultsActivity context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);
			mActivity = new WeakReference<SyncResultsActivity>(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			super.bindView(view, context, cursor);
			
			final SyncResultsActivity activity = mActivity.get();
			ImageView image = (ImageView) view.findViewById(R.id.contactImage);
			
			//long id = cursor.getLong(cursor.getColumnIndex(Results.CONTACT_ID));
			String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
			String description = cursor.getString(cursor.getColumnIndex(Results.DESCRIPTION));
			
			if (activity.mCache.contains(url)) {
				//Log.d(TAG, "bindView resetting " + url);
				image.setImageBitmap(activity.mCache.get(url));
			}
			else if (description.equals(ResultsDescription.SKIPPED_UNCHANGED.getDescription(context)) ||
					description.equals(ResultsDescription.UPDATED.getDescription(context))) {
				image.setImageResource(R.drawable.default_face);
			}
			else if (description.equals(ResultsDescription.NOTFOUND.getDescription(context))) {
				image.setImageResource(R.drawable.neutral_face);
			}
			else {
				image.setImageResource(R.drawable.sad_face);
			}
		}

		@Override
		public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
			SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return null;
			}
			
			String where = null;
			
			if (constraint != null) {
				where = Results.DESCRIPTION + " IN (" + constraint + ")";
			}
			
			return activity.getContentResolver().query(Results.CONTENT_URI, 
					activity.mProjection, 
					where, 
					null, 
					null);
		}
	}
}
