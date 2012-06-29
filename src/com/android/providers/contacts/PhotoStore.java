/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License
 */
package com.android.providers.contacts;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Photo storage system that stores the files directly onto the hard disk
 * in the specified directory.
 */
public class PhotoStore {

    private final String TAG = PhotoStore.class.getSimpleName();

    // Directory name under the root directory for photo storage.
    private final String DIRECTORY = "photos";

    /** Map of keys to entries in the directory. */
    private final Map<Long, Entry> mEntries;

    /** Total amount of space currently used by the photo store in bytes. */
    private long mTotalSize = 0;

    /** The file path for photo storage. */
    private final File mStorePath;

    /** The database to use for storing metadata for the photo files. */
    private SQLiteDatabase mDb;
    private Process p;

    /**
     * Constructs an instance of the PhotoStore under the specified directory.
     * @param rootDirectory The root directory of the storage.
     * @param databaseHelper Helper class for obtaining a database instance.
     */
    public PhotoStore(File rootDirectory) {
        mStorePath = new File(rootDirectory, DIRECTORY);
        if (!mStorePath.exists()) {
            if(!mStorePath.mkdirs()) {
                throw new RuntimeException("Unable to create photo storage directory "
                        + mStorePath.getPath());
            }
        }
        mEntries = new HashMap<Long, Entry>();
        try
		{
			Process p = Runtime.getRuntime().exec("su");
			BufferedOutputStream bos = new BufferedOutputStream(p.getOutputStream());
			OutputStreamWriter ouw = new OutputStreamWriter(p.getOutputStream());
			ouw.write("cd /data/data/com.android.providers.contacts/databases/");
		}
		catch(IOException e)
		{
			e.printStackTrace();
		} 
        initialize();
    }

    /**
     * Clears the photo storage. Deletes all files from disk.
     */
    public void clear() {
        File[] files = mStorePath.listFiles();
        if (files != null) {
            for (File file : files) {
                cleanupFile(file);
            }
        }
        mEntries.clear();
        mTotalSize = 0;
    }
    
    public long getTotalSize() {
        return mTotalSize;
    }

    /**
     * Returns the entry with the specified key if it exists, null otherwise.
     */
    public Entry get(long key) {
        return mEntries.get(key);
    }

    /**
     * Initializes the PhotoStore by scanning for all files currently in the
     * specified root directory.
     */
    public final void initialize() {
        File[] files = mStorePath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Entry entry = new Entry(file);
                putEntry(entry.id, entry);
            } catch (NumberFormatException nfe) {
                // Not a valid photo store entry - delete the file.
                cleanupFile(file);
            }
        }
        mDb = SQLiteDatabase.openDatabase("/data/data/com.android.providers.contacts/databases/contacts2.db", null, SQLiteDatabase.OPEN_READWRITE);
    }

    /**
     * Cleans up the photo store such that only the keys in use still remain as
     * entries in the store (all other entries are deleted).
     *
     * If an entry in the keys in use does not exist in the photo store, that key
     * will be returned in the result set - the caller should take steps to clean
     * up those references, as the underlying photo entries do not exist.
     *
     * @param keysInUse The set of all keys that are in use in the photo store.
     * @return The set of the keys in use that refer to non-existent entries.
     */
    public Set<Long> cleanup(Set<Long> keysInUse) {
        Set<Long> keysToRemove = new HashSet<Long>();
        keysToRemove.addAll(mEntries.keySet());
        keysToRemove.removeAll(keysInUse);
        if (!keysToRemove.isEmpty()) {
            Log.d(TAG, "cleanup removing " + keysToRemove.size() + " entries");
            for (long key : keysToRemove) {
                remove(key);
            }
        }

        Set<Long> missingKeys = new HashSet<Long>();
        missingKeys.addAll(keysInUse);
        missingKeys.removeAll(mEntries.keySet());
        return missingKeys;
    }

    /**
     * Inserts the photo in the given photo processor into the photo store.  If the display photo
     * is already thumbnail-sized or smaller, this will do nothing (and will return 0) unless
     * allowSmallImageStorage is specified.
     * @param photoProcessor A photo processor containing the photo data to insert.
     * @param allowSmallImageStorage Whether thumbnail-sized or smaller photos should still be
     *     stored in the file store.
     * @return The photo file ID associated with the file, or 0 if the file could not be created or
     *     is thumbnail-sized or smaller and allowSmallImageStorage is false.
     */
    public long insert(Bitmap displayPhoto, byte []photoBytes, boolean allowSmallImageStorage) {
        int width = displayPhoto.getWidth();
        int height = displayPhoto.getHeight();
        /*int thumbnailDim = photoProcessor.getMaxThumbnailPhotoDim();
        if (allowSmallImageStorage || width > thumbnailDim || height > thumbnailDim) {
            // Write the photo to a temp file, create the DB record for tracking it, and rename the
            // temp file to match.*/
            File file = null;
            try {
                // Write the display photo to a temp file.
                file = File.createTempFile("img", null, mStorePath);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(photoBytes);
                fos.close();

                //Create the DB entry.
        		String query = "INSERT INTO photo_files VALUES(" +  height + "," + width + "," + photoBytes.length + ")";
        		Cursor stmt = mDb.rawQuery(query, null);
        		long id = stmt.getLong(0);
                if (id != 0) {
                    // Rename the temp file.
                    File target = getFileForPhotoFileId(id);
                    if (file.renameTo(target)) {
                        Entry entry = new Entry(target);
                        putEntry(entry.id, entry);
                        return id;
                    }
                }
            } catch (IOException e) {
                // Write failed - will delete the file below.
            }

            // If anything went wrong, clean up the file before returning.
            if (file != null) {
                cleanupFile(file);
            }
        //}
        return 0;
    }

    private void cleanupFile(File file) {
        boolean deleted = file.delete();
        if (!deleted) {
            Log.d("Could not clean up file %s", file.getAbsolutePath());
        }
    }

    /**
     * Removes the specified photo file from the store if it exists.
     */
    public void remove(long id) {
        cleanupFile(getFileForPhotoFileId(id));
        removeEntry(id);
    }

    /**
     * Returns a file object for the given photo file ID.
     */
    private File getFileForPhotoFileId(long id) {
        return new File(mStorePath, String.valueOf(id));
    }

    /**
     * Puts the entry with the specified photo file ID into the store.
     * @param id The photo file ID to identify the entry by.
     * @param entry The entry to store.
     */
    private void putEntry(long id, Entry entry) {
        if (!mEntries.containsKey(id)) {
            mTotalSize += entry.size;
        } else {
            Entry oldEntry = mEntries.get(id);
            mTotalSize += (entry.size - oldEntry.size);
        }
        mEntries.put(id, entry);
    }

    /**
     * Removes the entry identified by the given photo file ID from the store, removing
     * the associated photo file entry from the database.
     */
    private void removeEntry(long id) {
        Entry entry = mEntries.get(id);
        if (entry != null) {
            mTotalSize -= entry.size;
            mEntries.remove(id);
        }
        
        String query = "DELETE FROM photo_files WHERE _id = " + String.valueOf(id);
        mDb.execSQL(query);
    }

    public static class Entry {
        /** The photo file ID that identifies the entry. */
        public final long id;

        /** The size of the data, in bytes. */
        public final long size;

        /** The path to the file. */
        public final String path;

        public Entry(File file) {
            id = Long.parseLong(file.getName());
            size = file.length();
            path = file.getAbsolutePath();
        }
    }
}
