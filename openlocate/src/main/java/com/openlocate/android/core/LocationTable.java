/*
 * Copyright (c) 2017 OpenLocate
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.openlocate.android.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class LocationTable {

    private static final String TABLE_NAME = "location";

    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_LOCATION = "location";
    private static final String QUERY_LIMIT = "1500";

    private static final int COLUMN_LOCATION_INDEX = 2;

    public static final String COLUMN_CREATED_AT = "created_at";

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_CREATED_AT + " INTEGER NOT NULL, "
            + COLUMN_LOCATION + " TEXT NOT NULL"
            + ");";

    private static final String CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `"
            + COLUMN_CREATED_AT + "_index`" + "ON `" + TABLE_NAME
            + "` (`" + COLUMN_CREATED_AT + "` ASC);";

    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS ";
    private static final String BULK_INSERT_LOCATION = "INSERT INTO "
            + TABLE_NAME
            + " (" + COLUMN_LOCATION + ", " + COLUMN_CREATED_AT + ")"
            + " VALUES (?, ?);";

    static void onOpen(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        db.execSQL(CREATE_INDEX_SQL);
    }

    static void createIfRequired(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        db.execSQL(CREATE_INDEX_SQL);
    }

    static void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_TABLE_SQL + TABLE_NAME);
        createIfRequired(db);
    }

    static void add(SQLiteDatabase database, OpenLocateLocation location) {
        if (database == null || location == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_CREATED_AT, location.getCreated().getTime());
        values.put(COLUMN_LOCATION, location.getJson().toString());
        database.insert(TABLE_NAME, null, values);
    }

    static void addAll(SQLiteDatabase database, List<OpenLocateLocation> locations) {
        if (database == null || locations == null || locations.isEmpty()) {
            return;
        }

        SQLiteStatement statement = database.compileStatement(BULK_INSERT_LOCATION);

        database.beginTransaction();
        for (OpenLocateLocation location : locations) {
            statement.clearBindings();
            statement.bindString(COLUMN_LOCATION_INDEX, location.getJson().toString());
            statement.bindLong(2, location.getCreated().getTime());
            statement.execute();
        }

        database.setTransactionSuccessful();
        database.endTransaction();
    }

    static long size(SQLiteDatabase database) {
        if (database == null) {
            return 0;
        }

        return DatabaseUtils.queryNumEntries(database, TABLE_NAME);
    }

    static List<OpenLocateLocation> getSince(SQLiteDatabase database, long millisecondsSince1970) {
        if (database == null) {
            return null;
        }

        Cursor cursor = database.query(TABLE_NAME, null, LocationTable.COLUMN_CREATED_AT + " > " + millisecondsSince1970,
                null, null, null, LocationTable.COLUMN_CREATED_AT, QUERY_LIMIT);

        if (cursor == null || cursor.isClosed()) {
            return null;
        }

        return getLocations(cursor);
    }

    static void deleteBefore(SQLiteDatabase database, long millisecondsSince1970) {
        database.delete(TABLE_NAME, LocationTable.COLUMN_CREATED_AT + " <= " + millisecondsSince1970, null);
    }

    private static List<OpenLocateLocation> getLocations(Cursor cursor) {
        List<OpenLocateLocation> locations = null;

        if (cursor.moveToFirst()) {
            locations = new ArrayList<>();
            do {
                if (cursor.isClosed()) {
                    break;
                }

                Date date = new Date(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                String json = cursor.getString(cursor.getColumnIndex(COLUMN_LOCATION));
                OpenLocateLocation location = new OpenLocateLocation(date, json);
                locations.add(location);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return locations;
    }
}
