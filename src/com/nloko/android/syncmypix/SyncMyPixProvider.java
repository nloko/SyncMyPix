//
//    SyncMyPixProvider.java is part of SyncMyPix
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

import java.util.HashMap;

import com.nloko.android.Log;
import com.nloko.android.syncmypix.SyncMyPix.Contacts;
import com.nloko.android.syncmypix.SyncMyPix.Results;
import com.nloko.android.syncmypix.SyncMyPix.Sync;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

// TODO this probably should not have even been a ContentProvider. Change?
public class SyncMyPixProvider extends ContentProvider {

	private static final String TAG = "SyncMyPixProvider";
	
    private static final String DATABASE_NAME = "syncpix.db";
    private static final int DATABASE_VERSION = 7;
    
    private static final String CONTACTS_TABLE_NAME = "contacts";
    private static final String RESULTS_TABLE_NAME = "results";
    private static final String SYNC_TABLE_NAME = "sync";

    private static HashMap<String, String> contactsProjection;
    private static HashMap<String, String> resultsProjection;
    private static HashMap<String, String> syncProjection;

    private static final int CONTACTS = 1;
    private static final int CONTACTS_ID = 2;
    private static final int RESULTS = 3;
    private static final int RESULTS_ID = 4;
    private static final int SYNC = 5;
    private static final int SYNC_ID = 6;

    private static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "contacts", CONTACTS);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "contacts/#", CONTACTS_ID);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "results", RESULTS);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "results/#", RESULTS_ID);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "sync", SYNC);
        uriMatcher.addURI(SyncMyPix.AUTHORITY, "sync/#", SYNC_ID);

        // Map columns to resolve ambiguity
        contactsProjection = new HashMap<String, String>();
        contactsProjection.put(Contacts._ID, Contacts._ID);
        contactsProjection.put(Contacts.LOOKUP_KEY, Contacts.LOOKUP_KEY);
        contactsProjection.put(Contacts.PIC_URL, Contacts.PIC_URL);
        contactsProjection.put(Contacts.PHOTO_HASH, Contacts.PHOTO_HASH);
        contactsProjection.put(Contacts.NETWORK_PHOTO_HASH, Contacts.NETWORK_PHOTO_HASH);
        contactsProjection.put(Contacts.FRIEND_ID, Contacts.FRIEND_ID);
        contactsProjection.put(Contacts.SOURCE, Contacts.SOURCE);

        syncProjection = new HashMap<String, String>();
        syncProjection.put(Sync._ID, SYNC_TABLE_NAME + "." + Sync._ID);
        syncProjection.put(Sync.SOURCE, Sync.SOURCE);
        syncProjection.put(Sync.DATE_STARTED, Sync.DATE_STARTED);
        syncProjection.put(Sync.DATE_COMPLETED, Sync.DATE_COMPLETED);
        syncProjection.put(Sync.UPDATED, Sync.UPDATED);
        syncProjection.put(Sync.SKIPPED, Sync.SKIPPED);
        syncProjection.put(Sync.NOT_FOUND, Sync.NOT_FOUND);
        
        resultsProjection = new HashMap<String, String>();
        resultsProjection.put(Sync._ID, SYNC_TABLE_NAME + "." + Sync._ID);
        resultsProjection.put(Sync.SOURCE, Sync.SOURCE);
        resultsProjection.put(Sync.DATE_STARTED, Sync.DATE_STARTED);
        resultsProjection.put(Sync.DATE_COMPLETED, Sync.DATE_COMPLETED);
        resultsProjection.put(Sync.UPDATED, Sync.UPDATED);
        resultsProjection.put(Sync.SKIPPED, Sync.SKIPPED);
        resultsProjection.put(Sync.NOT_FOUND, Sync.NOT_FOUND);
        
        resultsProjection.put(Results._ID, RESULTS_TABLE_NAME + "." + Results._ID);
        resultsProjection.put(Results.SYNC_ID, Results.SYNC_ID);
        resultsProjection.put(Results.NAME, Results.NAME);
        resultsProjection.put(Results.PIC_URL, Results.PIC_URL);
        resultsProjection.put(Results.DESCRIPTION, Results.DESCRIPTION);
        resultsProjection.put(Results.CONTACT_ID, Results.CONTACT_ID);
        resultsProjection.put(Results.LOOKUP_KEY, Results.LOOKUP_KEY);
        resultsProjection.put(Results.FRIEND_ID, Results.FRIEND_ID);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + CONTACTS_TABLE_NAME + " ("
                    + Contacts._ID + " INTEGER PRIMARY KEY,"
                    + Contacts.LOOKUP_KEY + " TEXT DEFAULT NULL,"
                    + Contacts.PIC_URL + " TEXT DEFAULT NULL,"
                    + Contacts.PHOTO_HASH + " TEXT,"
                    + Contacts.NETWORK_PHOTO_HASH + " TEXT,"
                    + Contacts.FRIEND_ID + " TEXT DEFAULT NULL,"
                    + Contacts.SOURCE + " TEXT"
                    + ");");
            
            db.execSQL("CREATE TABLE " + RESULTS_TABLE_NAME + " ("
                    + Results._ID + " INTEGER PRIMARY KEY,"
                    + Results.SYNC_ID + " INTEGER,"
                    + Results.NAME + " TEXT DEFAULT NULL,"
                    + Results.DESCRIPTION + " TEXT DEFAULT NULL,"
                    + Results.PIC_URL + " TEXT  DEFAULT NULL,"
                    + Results.CONTACT_ID + " INTEGER,"
                    + Results.LOOKUP_KEY + " TEXT DEFAULT NULL,"
                    + Results.FRIEND_ID + " TEXT DEFAULT NULL"
                    + ");");
            
            db.execSQL("CREATE TABLE " + SYNC_TABLE_NAME + " ("
                    + Sync._ID + " INTEGER PRIMARY KEY,"
                    + Sync.SOURCE + " TEXT DEFAULT NULL,"
                    + Sync.DATE_STARTED + " INTEGER,"
                    + Sync.DATE_COMPLETED + " INTEGER,"
                    + Sync.UPDATED + " INTEGER,"
                    + Sync.SKIPPED + " INTEGER,"
                    + Sync.NOT_FOUND + " INTEGER"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            if (oldVersion >= 2) {
	            db.execSQL("CREATE TABLE results_new ("
	                    + Results._ID + " INTEGER PRIMARY KEY,"
	                    + Results.SYNC_ID + " INTEGER,"
	                    + Results.NAME + " TEXT DEFAULT NULL,"
	                    + Results.DESCRIPTION + " TEXT DEFAULT NULL,"
	                    + Results.PIC_URL + " TEXT  DEFAULT NULL,"
	                    + Results.CONTACT_ID + " INTEGER,"
	                    + Results.LOOKUP_KEY + " TEXT DEFAULT NULL,"
	                    + Results.FRIEND_ID + " TEXT DEFAULT NULL"
	                    + ");");
	            
                db.execSQL("CREATE TABLE sync_new  ("
                        + Sync._ID + " INTEGER PRIMARY KEY,"
                        + Sync.SOURCE + " TEXT DEFAULT NULL,"
                        + Sync.DATE_STARTED + " INTEGER,"
                        + Sync.DATE_COMPLETED + " INTEGER,"
                        + Sync.UPDATED + " INTEGER,"
                        + Sync.SKIPPED + " INTEGER,"
                        + Sync.NOT_FOUND + " INTEGER"
                        + ");");
	                
	            db.execSQL("DROP TABLE IF EXISTS sync;");
	            db.execSQL("ALTER TABLE sync_new RENAME TO " + SYNC_TABLE_NAME +";");

	            db.execSQL("DROP TABLE IF EXISTS results;");
	            db.execSQL("ALTER TABLE results_new RENAME TO " + RESULTS_TABLE_NAME +";");
            }
            
            db.execSQL("CREATE TABLE contacts_new ("
                    + Contacts._ID + " INTEGER PRIMARY KEY,"
                    + Contacts.LOOKUP_KEY + " TEXT DEFAULT NULL,"
                    + Contacts.PIC_URL + " TEXT DEFAULT NULL,"
                    + Contacts.PHOTO_HASH + " TEXT,"
                    + Contacts.NETWORK_PHOTO_HASH + " TEXT,"
                    + Contacts.FRIEND_ID + " TEXT DEFAULT NULL,"
                    + Contacts.SOURCE + " TEXT"
                    + ");");
            
            if (oldVersion <= 4) {
	            db.execSQL("INSERT INTO contacts_new (" 
	            		+ Contacts._ID + ","
	            		+ Contacts.PHOTO_HASH + ")"
	            		+ "SELECT "
	            		+ Contacts._ID + ","
	            		+ Contacts.PHOTO_HASH
	            		+ " FROM " + CONTACTS_TABLE_NAME + ";");
            } else {
            	db.execSQL("INSERT INTO contacts_new (" 
	            		+ Contacts._ID + ","
	            		+ Contacts.NETWORK_PHOTO_HASH + ","
	            		+ Contacts.FRIEND_ID + ","
	            		+ Contacts.SOURCE + ","
	            		+ Contacts.PHOTO_HASH + ")"
	            		+ "SELECT "
	            		+ Contacts._ID + ","
	            		+ Contacts.NETWORK_PHOTO_HASH + ","
	            		+ Contacts.FRIEND_ID + ","
	            		+ Contacts.SOURCE + ","
	            		+ Contacts.PHOTO_HASH
	            		+ " FROM " + CONTACTS_TABLE_NAME + ";");
            }
            
            db.execSQL("DROP TABLE IF EXISTS contacts;");
            db.execSQL("ALTER TABLE contacts_new RENAME TO " + CONTACTS_TABLE_NAME +";");
        }
    }

    private DatabaseHelper openHelper;
    
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
        
		int count;
		String Id;
		
        switch (uriMatcher.match(uri)) {
        case CONTACTS:
            count = db.delete(CONTACTS_TABLE_NAME, selection, selectionArgs);
            break;

        case CONTACTS_ID:
            Id = uri.getPathSegments().get(1);
            count = db.delete(CONTACTS_TABLE_NAME, Contacts._ID + "=" + Id
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        case RESULTS:
        case SYNC:
        	// just wipe out everything
            count = db.delete(SYNC_TABLE_NAME, null, null);
            count = db.delete(RESULTS_TABLE_NAME, null, null);
            break;

        case SYNC_ID:
        	Id = uri.getPathSegments().get(1);
            count = db.delete(SYNC_TABLE_NAME, Sync._ID + "=" + Id, null);
            count = db.delete(RESULTS_TABLE_NAME, Results.SYNC_ID + "=" + Id, null);
            break;
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)) {
        case CONTACTS:
            return Contacts.CONTENT_TYPE;

        case CONTACTS_ID:
            return Contacts.CONTENT_ITEM_TYPE;
            
        case RESULTS:
        	return Results.CONTENT_TYPE;
        	
        case SYNC:
        	return Sync.CONTENT_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
	    // Validate the requested uri
        if (uriMatcher.match(uri) != CONTACTS &&
        		uriMatcher.match(uri) != RESULTS &&
        		uriMatcher.match(uri) != SYNC) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        String nullCol = null;
        String table = null;
        Uri baseUri = null;
        
        Long now = Long.valueOf(System.currentTimeMillis());

        if (uriMatcher.match(uri) == CONTACTS) {
        	table = CONTACTS_TABLE_NAME;
        	baseUri = Contacts.CONTENT_URI;
        	nullCol = Contacts.PHOTO_HASH;
        }
        
        if (uriMatcher.match(uri) == RESULTS) {
        	table = RESULTS_TABLE_NAME;
        	baseUri = Results.CONTENT_URI;
        	nullCol = Results.DESCRIPTION;
        }
        
        if (uriMatcher.match(uri) == SYNC) {
        	table = SYNC_TABLE_NAME;
        	baseUri = Sync.CONTENT_URI;
        	nullCol = Sync.SOURCE;
        	
        	if (values.containsKey(Sync.DATE_STARTED) == false) {
        		values.put(Sync.DATE_STARTED, now);
        	}
        }

        SQLiteDatabase db = openHelper.getWritableDatabase();
        long rowId = db.insert(table, nullCol, values);
        if (rowId > 0) {
            Uri rowUri = ContentUris.withAppendedId(baseUri, rowId);
            getContext().getContentResolver().notifyChange(rowUri, null);
            return rowUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public boolean onCreate() {
        openHelper = new DatabaseHelper(getContext());
        return true;
    }

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String orderBy;
		
        switch (uriMatcher.match(uri)) {
        case CONTACTS:
            qb.setTables(CONTACTS_TABLE_NAME);
            qb.setProjectionMap(contactsProjection);
            orderBy = Contacts.DEFAULT_SORT_ORDER;
            break;

        case CONTACTS_ID:
            qb.setTables(CONTACTS_TABLE_NAME);
            qb.setProjectionMap(contactsProjection);
            qb.appendWhere(Contacts._ID + "=" + uri.getPathSegments().get(1));
            orderBy = Contacts.DEFAULT_SORT_ORDER;
            break;
        case RESULTS:
        	qb.setProjectionMap(resultsProjection);
            qb.setTables(RESULTS_TABLE_NAME
            		+ " LEFT OUTER JOIN "
            		+ SYNC_TABLE_NAME
            		+ " ON ("
            		+ RESULTS_TABLE_NAME + "." + Results.SYNC_ID + "=" + SYNC_TABLE_NAME + "." + Sync._ID
            		+ ")");
            
            orderBy = Results.DEFAULT_SORT_ORDER;
            break;

        case RESULTS_ID:
        	qb.setProjectionMap(resultsProjection);
            qb.setTables(RESULTS_TABLE_NAME
            		+ " LEFT OUTER JOIN "
            		+ SYNC_TABLE_NAME
            		+ " ON ("
            		+ RESULTS_TABLE_NAME + "." + Results.SYNC_ID + "=" + SYNC_TABLE_NAME + "." + Sync._ID
            		+ ")");
            
            qb.appendWhere(RESULTS_TABLE_NAME + "." + Results._ID + "=" + uri.getPathSegments().get(1));
            orderBy = Results.DEFAULT_SORT_ORDER;
        	break;
        	
        case SYNC:
            qb.setTables(SYNC_TABLE_NAME);
            qb.setProjectionMap(syncProjection);
            orderBy = Sync.DEFAULT_SORT_ORDER;
            break;
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (!TextUtils.isEmpty(sortOrder)) {
        	orderBy = sortOrder;
        }

        SQLiteDatabase db = openHelper.getWritableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
	
		SQLiteDatabase db = openHelper.getWritableDatabase();
		String Id;
        int count;
        
        switch (uriMatcher.match(uri)) {
        case CONTACTS:
            count = db.update(CONTACTS_TABLE_NAME, values, selection, selectionArgs);
            break;

        case CONTACTS_ID:
            Id = uri.getPathSegments().get(1);
            count = db.update(CONTACTS_TABLE_NAME, values, Contacts._ID + "=" + Id
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        case RESULTS:
            count = db.update(RESULTS_TABLE_NAME, values, selection, selectionArgs);
            break;
            
        case RESULTS_ID:
            Id = uri.getPathSegments().get(1);
            count = db.update(RESULTS_TABLE_NAME, values, Results._ID + "=" + Id
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        case SYNC:
            count = db.update(SYNC_TABLE_NAME, values, selection, selectionArgs);
            break;
            
        case SYNC_ID:
            Id = uri.getPathSegments().get(1);
            count = db.update(SYNC_TABLE_NAME, values, Sync._ID + "=" + Id
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}
}
