//
//    SyncResultsActivity.java is part of SyncMyPix
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
import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.nloko.android.Log;
import com.nloko.android.PhotoCache;
import com.nloko.android.ThumbnailCache;
import com.nloko.android.Utils;
import com.nloko.android.ThumbnailCache.ImageListener;
import com.nloko.android.ThumbnailCache.ImageProvider;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;
import com.nloko.android.syncmypix.contactutils.ContactUtils;
import com.nloko.android.syncmypix.graphics.CropImage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
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
import android.provider.Contacts.People;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class SyncResultsActivity extends Activity {
	private ContactUtils mContactUtils;
	private SyncMyPixDbHelper mDbHelper;
	private ListView mListview;
	
	private Handler mMainHandler;
	private DownloadImageHandler mDownloadHandler;
	private ThumbnailHandler mThumbnailHandler;
	private InitializeResultsThread mInitResultsThread;
	
	private Bitmap mContactImage;
	private final ThumbnailCache mCache = new ThumbnailCache();
	private PhotoCache mSdCache;

	private ProgressBar mProgress;
	private ImageButton mDeleteButton;
	private ImageButton mHelpButton;
	private ImageButton mHomeButton;
	
	private Uri mUriOfSelected = null;
	
	private final int MENU_FILTER = 1;
	private final int MENU_FILTER_ALL = 2;
	private final int MENU_FILTER_NOTFOUND = 3;
	private final int MENU_FILTER_UPDATED = 4;
	private final int MENU_FILTER_SKIPPED = 5;
	private final int MENU_FILTER_ERROR = 6;

	// dialogs
	private final int ZOOM_PIC = 1;
	private final int UPDATE_CONTACT = 3;
	private final int HELP_DIALOG = 4;
	private final int DELETE_DIALOG = 5;
	private final int DELETING = 6;
	
	private static final String TAG = "SyncResults";
	
	private final int CONTEXTMENU_CROP = 3;
	private final int CONTEXTMENU_SELECT_CONTACT = 4;
	private final int CONTEXTMENU_VIEW_CONTACT = 5;
	private final int CONTEXTMENU_UNLINK = 6;
	
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
		setContentView(R.layout.results);	
		
		mContactUtils = new ContactUtils();
		
		mSdCache = new PhotoCache(getApplicationContext());
		Bitmap defaultImage = BitmapFactory.decodeResource(getResources(), R.drawable.default_face);
		//mCache.setDefaultImage(Bitmap.createScaledBitmap(defaultImage, 40, 40, false));
		mCache.setDefaultImage(defaultImage);
		
		mDbHelper = new SyncMyPixDbHelper(getApplicationContext());
		
        Cursor cursor = managedQuery(Results.CONTENT_URI, mProjection, null, null, Results.DEFAULT_SORT_ORDER);
        
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.VISIBLE);
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
				if (mThumbnailHandler != null) {
					//Log.d(TAG, "reloading thumbnail");
					Message msg = mThumbnailHandler.obtainMessage();
					msg.obj = url;
					mThumbnailHandler.sendMessage(msg);
					return true;
				}
		        return false;
			}
        });
        
        mCache.setImageListener(new ImageListener() {
			public void onImageReady(final String url) {
				if (mListview == null) return;
				final ImageView image = (ImageView) mListview.findViewWithTag(url);

//				Log.d(TAG, "onImageReady updating image");
				runOnUiThread(new Runnable() {
					public void run() {
						if (mListview == null) return;
						if (mCache != null && image != null) {
							image.setImageBitmap(mCache.get(url));
						} else {
							// 	HACK sometimes the view can't be found, probably already recycled
							((SimpleCursorAdapter)mListview.getAdapter()).notifyDataSetChanged();
						}
						mListview.invalidateViews();
					}
				});
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
					//setProgressBarIndeterminateVisibility(true);
					mProgress.setVisibility(View.VISIBLE);
					
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
							menu.add(0, CONTEXTMENU_UNLINK, Menu.NONE, R.string.syncresults_menu_unlink);
						}
						
		                menu.add(0, CONTEXTMENU_SELECT_CONTACT, Menu.NONE, R.string.syncresults_menu_addpicture);
					}
				}
			}
        });

		mHomeButton = (ImageButton) findViewById(R.id.home);
		mHomeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
				finish();
			}
		});
		
		mHelpButton = (ImageButton) findViewById(R.id.help);
		mHelpButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help_link)));
				startActivity(i);
			}
		});
		
		mDeleteButton = (ImageButton) findViewById(R.id.delete);
		mDeleteButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(DELETE_DIALOG);
			}
		});

        mMainHandler = new MainHandler(this);
	}

	private static class MainHandler extends Handler
	{
		private final WeakReference<SyncResultsActivity> mActivity;
		private final static int UNKNOWN_HOST_ERROR = 2;
		private final static int MANUAL_LINK_ERROR = 3;
		
		public MainHandler(SyncResultsActivity activity)
		{
			super();
			mActivity = new WeakReference<SyncResultsActivity>(activity);
		}
		
		@Override
		public void handleMessage(Message msg) {
			SyncResultsActivity activity = mActivity.get();
			if (activity != null) {
				Bitmap bitmap = (Bitmap) msg.obj;
				if (bitmap != null) {
					activity.mContactImage = bitmap;
					activity.showDialog(activity.ZOOM_PIC);
				}
				
				activity.mProgress.setVisibility(View.INVISIBLE);
				handleWhat(msg);
			}
		}
		
		private void handleWhat(Message msg) {
			SyncResultsActivity activity = mActivity.get();
			if (activity != null) {
				switch (msg.what) {
					case UNKNOWN_HOST_ERROR:
						Toast.makeText(activity.getApplicationContext(), R.string.syncresults_networkerror, Toast.LENGTH_LONG).show();
						break;
					case MANUAL_LINK_ERROR:
						Toast.makeText(activity.getApplicationContext(), R.string.syncresults_manuallinkerror, Toast.LENGTH_LONG).show();
						break;
				}
			}
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Log.d(TAG, "FINALIZED");
	}

	@Override
	protected void onPause() {
		super.onPause();
		// save battery life by killing downloader thread
		if (mCache != null) {
			mCache.togglePauseOnDownloader(true);
			mCache.empty();
		}
		
//		if (mThumbnailHandler != null) {
//			mThumbnailHandler.stopRunning();
//		}
		
		if (mDownloadHandler != null) {
			mDownloadHandler.stopRunning();
		}
		
		if (mInitResultsThread != null) {
			mInitResultsThread.stopRunning();
		}
	}
	 
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		
		if (mCache != null) {
			mCache.empty();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
        HandlerThread downloadThread = new HandlerThread("ImageDownload");
        downloadThread.start();
        mDownloadHandler = new DownloadImageHandler(this, downloadThread.getLooper());
        
        if (mInitResultsThread == null) {
        	mInitResultsThread = new InitializeResultsThread(this);
        	mInitResultsThread.start();
        }
        
        if (mThumbnailHandler == null) {
        	HandlerThread thumbnailThread = new HandlerThread("Thumbnail");
        	thumbnailThread.start();
        	mThumbnailHandler = new ThumbnailHandler(this, thumbnailThread.getLooper());
        }
        
        mCache.togglePauseOnDownloader(false);
        
        NotificationManager notifyManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notifyManager.cancel(R.string.syncservice_stopped);
	}

	@Override
	protected void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
		if (mThumbnailHandler != null) {
			mThumbnailHandler.stopRunning();
		}
		mCache.destroy();
		
		if (mSdCache != null) {
			mSdCache.releaseResources();
		}
	}
		
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		 MenuItem item;

		 SubMenu options = menu.addSubMenu(0, MENU_FILTER, 0, R.string.syncresults_filterButton);
		 options.add(0, MENU_FILTER_ALL, 0, "All");
		 options.add(0, MENU_FILTER_ERROR, 0, "Errors");
		 options.add(0, MENU_FILTER_NOTFOUND, 0, "Not found");
		 options.add(0, MENU_FILTER_SKIPPED, 0, "Skipped");
		 options.add(0, MENU_FILTER_UPDATED, 0, "Updated");
		 
		 options.setIcon(android.R.drawable.ic_menu_sort_alphabetically);
		 return true;
	 }
	 
	 @Override
	 public boolean onOptionsItemSelected(MenuItem item) {
		 
		 SimpleCursorAdapter adapter = (SimpleCursorAdapter)mListview.getAdapter();
		 
		 switch (item.getItemId()) {
		 case MENU_FILTER_ALL:
			 adapter.getFilter().filter(null);
			 return true;
		 case MENU_FILTER_ERROR:
			 adapter.getFilter().filter("'" + getString(R.string.resultsdescription_error) + "'," +
					 "'" + getString(R.string.resultsdescription_downloadfailed) + "'");
			 return true;
		 case MENU_FILTER_NOTFOUND:
			 adapter.getFilter().filter("'" + getString(R.string.resultsdescription_notfound) + "'");
			 return true;
		 case MENU_FILTER_UPDATED:
			 adapter.getFilter().filter("'" + getString(R.string.resultsdescription_updated) + "'," +
					 "'" + getString(R.string.resultsdescription_multipleprocessed) + "'");
			 return true;
		 case MENU_FILTER_SKIPPED:
			 adapter.getFilter().filter("'" + getString(R.string.resultsdescription_skippedexists) + "'," +
					 "'" + getString(R.string.resultsdescription_skippedunchanged) + "'," +
					 "'" + getString(R.string.resultsdescription_skippedmultiplefound) + "'");
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
						intent = new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(mContactUtils.getContentUri(), id));
						startActivity(intent);
					}
				}
				
				return true;
				
			case CONTEXTMENU_SELECT_CONTACT:
				position = ((AdapterContextMenuInfo)menuInfo).position;
				mUriOfSelected = getResultsUriFromListPosition(position);
				
				intent = new Intent(Intent.ACTION_PICK, mContactUtils.getContentUri());
				startActivityForResult(intent, PICK_CONTACT);  
				return true;
				
			case CONTEXTMENU_CROP:
				position = ((AdapterContextMenuInfo)menuInfo).position;
				mUriOfSelected = getResultsUriFromListPosition(position);
				
				cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				if (cursor.moveToPosition(position)) {
					id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					updateContactWithSelection(Uri.withAppendedPath(mContactUtils.getContentUri(), id));
					//crop(id);
				}
				
				return true;
				
			case CONTEXTMENU_UNLINK:
				position = ((AdapterContextMenuInfo)menuInfo).position;
				mUriOfSelected = getResultsUriFromListPosition(position);
				
				cursor = ((SimpleCursorAdapter)mListview.getAdapter()).getCursor();
				if (cursor.moveToPosition(position)) {
					id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					unlink(id, true);
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
				if (mUriOfSelected == null) {
					break;
				}
				
				ContentResolver resolver = getContentResolver();
				Cursor cursor = resolver.query(mUriOfSelected, 
						new String[] { Results.CONTACT_ID, Results.PIC_URL, Results.LOOKUP_KEY }, 
						null, 
						null, 
						null);
				
				ResultsListAdapter adapter = (ResultsListAdapter)mListview.getAdapter();
				
				if (cursor.moveToFirst()) {
					String id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
					String lookup = cursor.getString(cursor.getColumnIndex(Results.LOOKUP_KEY));
					
					Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");
					if (bitmap != null) {
						byte[] bytes = Utils.bitmapToPNG(bitmap);
					
						mContactUtils.updatePhoto(getContentResolver(), bytes, id);
						mDbHelper.updateHashes(id, lookup, null, bytes);
					
						mCache.add(url, bitmap);
						adapter.notifyDataSetChanged();
					}
				}
			
				cursor.close();
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
		// hopefully, no users will ever see this message
		// SyncMyPix has been getting killed by Android under
		// low memory conditions. 
		// Work has been done to try to prevent this ie. override onLowMemory,
		// free resources, ensure proper garbage collection.
		if (contact == null || mUriOfSelected == null) {
			Toast.makeText(getApplicationContext(),
					R.string.syncresults_addpicture_error, 
					Toast.LENGTH_LONG).show();
			return;
		}
		
		final ContentResolver resolver = getContentResolver();
		final Uri contactUri = contact;
		final List<String> segments = contactUri.getPathSegments();
		final String contactId = segments.get(segments.size() - 1);
		final String lookup = mContactUtils.getLookup(resolver, contactUri);
		
		Cursor cursor = resolver.query(mUriOfSelected, 
				new String[] { Results._ID, 
					Results.CONTACT_ID,
					Results.PIC_URL,
					Results.FRIEND_ID,
					Sync.SOURCE }, 
				null, 
				null, 
				null);
		
		if (cursor.moveToFirst()) {
			final SyncMyPixPreferences prefs = new SyncMyPixPreferences(getApplicationContext());

			
			if (!mContactUtils.isContactUpdatable(resolver, contactId) && !prefs.overrideReadOnlyCheck()) {
				Toast.makeText(getApplicationContext(),
						R.string.syncresults_contactunlinkableerror, 
						Toast.LENGTH_LONG).show();
				return;
			}
			
			showDialog(UPDATE_CONTACT);
			
			final long id  = cursor.getLong(cursor.getColumnIndex(Results._ID));
			final String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
			final String friendId = cursor.getString(cursor.getColumnIndex(Results.FRIEND_ID));
			final String source = cursor.getString(cursor.getColumnIndex(Sync.SOURCE));
			final String oldContactId = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
			
			final PhotoCache sdCache = mSdCache;
			
			Thread thread = new Thread(new Runnable() {
				public void run() {
					try {
						String filename = Uri.parse(url).getLastPathSegment();
						InputStream friend = sdCache.get(filename);
						if (friend == null) {
							friend = Utils.downloadPictureAsStream(url);
						}
						if (friend != null) {
							byte[] bytes = Utils.getByteArrayFromInputStream(friend);
							friend.close();
							sdCache.add(filename, bytes);
							
							Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
							mCache.add(url, bitmap);
							String origHash = Utils.getMd5Hash(bytes);
							bytes = Utils.bitmapToPNG(bitmap);
							String dbHash = Utils.getMd5Hash(bytes);
							
							// free memory
							bitmap.recycle();
							
							Log.d(TAG, contactUri.toString());
							unlink(contactId);
							if (oldContactId != null) {
								unlink(oldContactId, true);
							}
							
							mContactUtils.updatePhoto(resolver, bytes, contactId);
							mDbHelper.updateHashes(contactId, lookup, origHash, dbHash);
							
							if (friendId != null && !friendId.equals("")) {
								mDbHelper.updateLink(contactId, lookup, friendId, source);
							}
							
							ContentValues values = new ContentValues();
							values.put(Results.DESCRIPTION, getString(R.string.resultsdescription_updated));
							values.put(Results.CONTACT_ID, Long.parseLong(contactId));
							resolver.update(Uri.withAppendedPath(Results.CONTENT_URI, Long.toString(id)), 
									values, 
									null, 
									null);
							
							runOnUiThread(new Runnable() {
								public void run() {
									crop(contactId);
								}
							});

						} else {
							mMainHandler.sendEmptyMessage(MainHandler.MANUAL_LINK_ERROR);
						}
					} catch (UnknownHostException ex) {
						ex.printStackTrace();
						mMainHandler.sendEmptyMessage(MainHandler.UNKNOWN_HOST_ERROR);
					} catch (Exception e) {
						e.printStackTrace();
						mMainHandler.sendEmptyMessage(MainHandler.MANUAL_LINK_ERROR);
					}
					finally {
						runOnUiThread(new Runnable() {
							public void run() {
								try {
									dismissDialog(UPDATE_CONTACT);
								} catch (IllegalArgumentException e) { 
									// ignore 
								}
							}
						});
					}
				}
			});
			
			thread.start();
		}
		
		cursor.close();
	}
	
	private void unlink(String id)
	{
		unlink(id, false);
	}
	
	private void unlink(String id, boolean purge)
	{
		if (id == null) {
			return;
		}
		
		final ContentResolver resolver = getContentResolver();
		ContentValues values = new ContentValues();
		values.put(Results.DESCRIPTION, getString(R.string.resultsdescription_notfound));
		values.putNull(Results.CONTACT_ID);
		resolver.update(Results.CONTENT_URI, 
				values, 
				Results.CONTACT_ID + "=" + id, 
				null);
		
		if (purge) {
			mDbHelper.deletePicture(id);
			resolver.delete(Uri.withAppendedPath(SyncMyPix.Contacts.CONTENT_URI, id), null, null);
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
			case ZOOM_PIC:
				if (mContactImage != null) {
					return showZoomDialog();
				}
				break;
			case UPDATE_CONTACT:
				ProgressDialog sync = new ProgressDialog(this);
				sync.setCancelable(false);
				sync.setMessage(getString(R.string.syncresults_syncDialog));
				sync.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				return sync;
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
				            				   Toast.makeText(getApplicationContext(),
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
		private Cursor cursor;
		
		private final WeakReference<SyncResultsActivity> mActivity;
		
		InitializeResultsThread (SyncResultsActivity activity)
		{
			mActivity = new WeakReference<SyncResultsActivity>(activity);
		}
		
		private void ensureQuery()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			cursor = activity.getContentResolver().query(Results.CONTENT_URI, 
					new String[] { Sync.UPDATED, 
						Sync.SKIPPED,
						Sync.NOT_FOUND }, 
					null, 
					null, 
					null);
		}
		
		private void closeQuery()
		{
			if (cursor != null) {
				cursor.close();
			}
		}
		
		public void stopRunning ()
		{
			synchronized(this) {
				running = false;
				closeQuery();
			}
		}
		
		private void hideDialog() 
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			activity.runOnUiThread(new Runnable() {
				public void run() {
					activity.mProgress.setVisibility(View.INVISIBLE);
				}
			});
		}
		
		public void run()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}

			final int updated, skipped, notFound;
			
			synchronized(this) {
				if (running) {
					ensureQuery();
					if (cursor.moveToFirst()) {
						updated = cursor.getInt(cursor.getColumnIndex(Sync.UPDATED));
						skipped = cursor.getInt(cursor.getColumnIndex(Sync.SKIPPED));
						notFound = cursor.getInt(cursor.getColumnIndex(Sync.NOT_FOUND));
					} else {
						updated = 0;
						skipped = 0;
						notFound = 0;
					}
				} else {
					hideDialog();
					return;
				}
			}

			final TextView text1 = (TextView) activity.findViewById(R.id.updated);
			final TextView text2 = (TextView) activity.findViewById(R.id.skipped);
			final TextView text3 = (TextView) activity.findViewById(R.id.notfound);

			final TextView label1 = (TextView) activity.findViewById(R.id.updatedLabel);
			final TextView label2 = (TextView) activity.findViewById(R.id.skippedLabel);
			final TextView label3 = (TextView) activity.findViewById(R.id.notfoundLabel);

			activity.runOnUiThread(new Runnable() {
				public void run() {
					CharSequence s = activity.getString(R.string.syncresults_updated);
					label1.setText(String.format(s.toString(), activity.getString(R.string.app_name)));

					text1.setText(Integer.toString(updated));
					text2.setText(Integer.toString(skipped));
					text3.setText(Integer.toString(notFound));

					label1.setVisibility(View.VISIBLE);
					label2.setVisibility(View.VISIBLE);
					label3.setVisibility(View.VISIBLE);

					//activity.removeDialog(activity.LOADING_DIALOG);
					activity.mProgress.setVisibility(View.INVISIBLE);
				}
			});
		}
	}
	
	private static class ThumbnailHandler extends Handler
	{
		private final int LOAD_ALL = 1;
		private final WeakReference<SyncResultsActivity> mActivity;
		private boolean running = true;
		private boolean mLoading = false;
		
		private static class WorkerThread extends Thread {
			// create a structure to queue
			private final class Work {
				public String contactId;
				public String url;
				public Work(String contactId, String url) {
					this.contactId = contactId;
					this.url = url;
				}
			}
			
			private final BlockingQueue<Work> mQueue = new LinkedBlockingQueue<Work>();
			private final WeakReference<SyncResultsActivity> mActivity;
			private boolean running = true;
			
			public WorkerThread(SyncResultsActivity activity) {
				mActivity = new WeakReference<SyncResultsActivity>(activity);
			}
		
			public void stopRunning() {
				running = false;
				interrupt();
			}

			public void queueWork(String contactId, String url) {
				try {
					mQueue.put(new Work(contactId, url));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
			
			private String queryContact(String url)
			{
				final SyncResultsActivity activity = mActivity.get();
				if (activity == null) {
					return null;
				}
				final ContentResolver resolver = activity.getContentResolver();
				if (resolver == null) {
					return null;
				}
				
				final String where = Results.PIC_URL + "='" + url + "'";
				
				String[] projection = { 
		        		Results._ID, 
		        		Results.CONTACT_ID,
		        		Results.PIC_URL };
				
				Cursor cursor = null;
				String id = null;
				try {
					cursor = resolver.query(Results.CONTENT_URI, 
			        	projection, 
			        	where, 
			        	null, 
			        	Results.DEFAULT_SORT_ORDER);
				
					if (cursor.moveToNext()) {
						id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					}
				} catch (Exception ex) {
					Log.e(TAG, android.util.Log.getStackTraceString(ex));
				} finally {
					if (cursor != null) {
						cursor.close();
					}
				}
				
				return id;
			}

			private void update(String contactId, String url)
			{
				if (contactId == null || url == null) {
					return;
				}
				
				final SyncResultsActivity activity = mActivity.get();
				if (activity == null) {
					return;
				}

				if (activity.mCache.contains(url)) {
					return;
				}
				
				final ContentResolver resolver = activity.getContentResolver();
				if (resolver == null) {
					return;
				}
			
				final ContactUtils utils = activity.mContactUtils;
				if (utils == null) {
					return;
				}
				
				try {
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = 2;
					Bitmap bitmap = BitmapFactory.decodeStream(utils.getPhoto(resolver, contactId), null, options);
					if (bitmap != null) {
						//Log.d(TAG, "ThumbnailHandler updated cache " + url);
						activity.mCache.add(url, bitmap);
//						// HACK to force notifyDatasetUpdated() to be honoured
//						ContentValues values = new ContentValues();
//						values.put(Results.PIC_URL, url);
//						//synchronized(this) {
//							resolver.update(Results.CONTENT_URI, 
//									values, 
//									Results.CONTACT_ID + "=" + contactId, 
//									null);
//						//}
					}
				} catch (Exception ex) {
					Log.e(TAG, android.util.Log.getStackTraceString(ex));
				} finally {
//					activity.runOnUiThread(new Runnable() {
//
//						public void run() {
//							// TODO Auto-generated method stub
//							((SimpleCursorAdapter)activity.mListview.getAdapter()).notifyDataSetChanged();
//						}
//						
//					});
					
				}
				
			}
			
			public void run() {
				while(running) {
					try {
						Work w = mQueue.take();
						if (w != null && w.contactId == null) {
							w.contactId = queryContact(w.url);
						}
						update(w.contactId, w.url);
					} catch (InterruptedException e) {
						Log.d(TAG, "INTERRUPTED!");
					}
				}
			}
		}
		
		// use a pool to support extremely popular people
		private static final int MAX_THREADS = 3; 
		private int mThreadIndex = 0;
		private final List<WorkerThread> mThreadPool = new ArrayList<WorkerThread>();
		
		ThumbnailHandler(SyncResultsActivity activity, Looper looper)
		{
			super(looper);
			mActivity = new WeakReference<SyncResultsActivity>(activity);
			initThreadPool(activity);
			//init();
		}
		
		private void initThreadPool(SyncResultsActivity activity)
		{
			for (int i=0; i < MAX_THREADS; i++) {
				WorkerThread thread = new WorkerThread(activity);
				thread.start();
				mThreadPool.add(thread);
			}
		}
		
		private void init()
		{
			Message msg = obtainMessage();
			msg.what = LOAD_ALL;
			sendMessage(msg);
		}
		
		public boolean isLoading() 
		{
			return mLoading;
		}
		
		private void loadAll()
		{
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			final ContentResolver resolver = activity.getContentResolver();
			if (resolver == null) {
				return;
			}
		
			mLoading = true;
			
			String[] projection = { 
	        		Results._ID, 
	        		Results.CONTACT_ID,
	        		Results.PIC_URL };
			
			Cursor cursor = null;
			try {
				cursor = resolver.query(Results.CONTENT_URI, 
			        	projection, 
			        	null, 
			        	null, 
			        	Results.DEFAULT_SORT_ORDER);
				
				while(running && cursor.moveToNext()) {
					String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
					url = url != null ? url.trim() : null;
					String id = cursor.getString(cursor.getColumnIndex(Results.CONTACT_ID));
					queueWork(id, url);
				}
			} catch (Exception ex) {
				Log.e(TAG, android.util.Log.getStackTraceString(ex));
			} finally {
				if (cursor != null) {
					cursor.close();
				}
				//((SimpleCursorAdapter)activity.mListview.getAdapter()).notifyDataSetChanged();
				mLoading = false;
			}
		}
		
		public void stopRunning()
		{
			synchronized(this) {
				getLooper().quit();
				running = false;
				for (WorkerThread thread : mThreadPool) {
					if (thread != null) {
						thread.stopRunning();
					}
				}
			}
		}
		
		private void queueWork(String url) {
			queueWork(null, url);
		}
		
		private void queueWork(String contactId, String url) {
			if (mThreadIndex == mThreadPool.size()) {
				mThreadIndex = 0;
			}
			WorkerThread thread = mThreadPool.get(mThreadIndex++);
			if (thread != null) {
				thread.queueWork(contactId, url);
			}
		}
		
		@Override
		public void handleMessage(Message msg)
		{
			if (!running || mLoading) {
				return;
			} else if (msg.what == LOAD_ALL) {
				loadAll();
				return;
			}
			
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			String url = (String) msg.obj;
			if (url == null) {
				return;
			}
			
			queueWork(url);				
		}
	}
	
	private static class DownloadImageHandler extends Handler
	{
		private final WeakReference<SyncResultsActivity> mActivity;
		private boolean running = true;
		
		DownloadImageHandler(SyncResultsActivity activity, Looper looper)
		{
			super(looper);
			mActivity = new WeakReference<SyncResultsActivity>(activity);
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
			if (activity == null) {
				return;
			}
			final Handler handler = activity.mMainHandler;
			if (handler == null) {
				return;
			}
			
			final SyncMyPixPreferences prefs = new SyncMyPixPreferences(activity.getApplicationContext());
			
			String url = (String) msg.obj;
			if (url != null) {
				try {
					InputStream friend = null;
					String filename = Uri.parse(url).getLastPathSegment();
					if (activity.mSdCache != null) {
						friend = activity.mSdCache.get(filename);
					}
					
					// cache miss
					if (friend == null) {
						friend = Utils.downloadPictureAsStream(url);
					}
					
					synchronized(this) {
						if (running && friend != null) {
							byte[] bytes = Utils.getByteArrayFromInputStream(friend);
							friend.close();
							
							Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
							if (prefs.getCache()) {
								activity.mSdCache.add(filename, bytes);
							}
							Message mainMsg = handler.obtainMessage();
							mainMsg.obj = bitmap;
							mainMsg.what = msg.what;
							handler.sendMessage(mainMsg);
							if (!activity.mCache.contains(url)) {
								activity.mCache.add(url, bitmap, true);
							}
						}
					}
				} catch (UnknownHostException ex) {
					ex.printStackTrace();
					handler.sendEmptyMessage(MainHandler.UNKNOWN_HOST_ERROR);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}	
	}
	
	public static class ResultsListAdapter extends SimpleCursorAdapter
	{
		public static final class Viewholder {
			public String url;
			public ImageView image;
			public TextView name;
			public TextView status;
		}
		
		private final WeakReference<SyncResultsActivity> mActivity;
		private Bitmap mNeutralFace;
		private Bitmap mSadFace;
		
		public ResultsListAdapter(SyncResultsActivity context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);
			mActivity = new WeakReference<SyncResultsActivity>(context);
			
			// pre-scale images
			mNeutralFace = BitmapFactory.decodeResource(context.getResources(), R.drawable.neutral_face);
			//mNeutralFace = Bitmap.createScaledBitmap(mNeutralFace, 40, 40, false);
			mSadFace = BitmapFactory.decodeResource(context.getResources(), R.drawable.sad_face);
			//mSadFace = Bitmap.createScaledBitmap(mSadFace, 40, 40, false);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			// use a Viewholder to cache the ImageView and avoid unnecessary findViewById calls
			View view =  super.newView(context, cursor, parent);
			Viewholder holder = new Viewholder(); 
			holder.image = (ImageView) view.findViewById(R.id.contactImage); 
			holder.name = (TextView) view.findViewById(R.id.text1);
			holder.status = (TextView) view.findViewById(R.id.text2);
			view.setTag(holder);
			return view;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
//			Viewholder holder = (Viewholder) view.getTag();
//			if (holder == null) {
//				return;
//			} else {
//				view.setTag("");
//			}
			
			// do all the binding myself since 1.5 throws a ClassCastException due to Viewholder
			//super.bindView(view, context, cursor);

			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return;
			}
			
			Viewholder holder = (Viewholder) view.getTag();
			if (holder == null) {
				return;
			}
			
			long id = cursor.getLong(cursor.getColumnIndex(Results.CONTACT_ID));
			String name = cursor.getString(cursor.getColumnIndex(Results.NAME));
			String url = cursor.getString(cursor.getColumnIndex(Results.PIC_URL));
			url = url != null ? url.trim() : null;
			String description = cursor.getString(cursor.getColumnIndex(Results.DESCRIPTION));
			
			holder.name.setText(name);
			holder.status.setText(description);
			holder.url = url;
			
			// this finds the right view to load the image into
			// due to Android object recycling
			ImageView image = holder.image;
			image.setTag(url);
			
			if (id > 0 && !activity.mCache.contains(url)) {
				//if (!activity.mThumbnailHandler.isLoading()) {
					//Log.d(TAG, "bindView attempting to load into cache " + url);
					Message msg = activity.mThumbnailHandler.obtainMessage();
					msg.obj = url;
					activity.mThumbnailHandler.sendMessage(msg);
				//}
				image.setImageBitmap(activity.mCache.getDefaultImage());
			} else if (activity.mCache.contains(url)) {
				//Log.d(TAG, id + " bindView attempting to get from cache " + url);
				image.setImageBitmap(activity.mCache.get(url));
			} else if (description.equals(context.getString(R.string.resultsdescription_notfound))) {
				image.setImageBitmap(mNeutralFace);
			} else {
				image.setImageBitmap(mSadFace);
			}
		}

		@Override
		public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
			final SyncResultsActivity activity = mActivity.get();
			if (activity == null) {
				return null;
			}
			final ContentResolver resolver = activity.getContentResolver();
			if (resolver == null) {
				return null;
			}
			
			String where = null;
			if (constraint != null) {
				where = Results.DESCRIPTION + " IN (" + constraint + ")";
			}
			
			return resolver.query(Results.CONTENT_URI, 
					activity.mProjection, 
					where, 
					null, 
					null);
		}
	}
}
