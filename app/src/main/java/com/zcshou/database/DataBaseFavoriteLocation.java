package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

import java.util.ArrayList;
import java.util.List;

public class DataBaseFavoriteLocation extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "FavoriteLocation";
    public static final String TABLE_GROUP = "FavoriteGroup";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_GROUP = "DB_COLUMN_GROUP";
    public static final String DB_COLUMN_GROUP_NAME = "DB_COLUMN_GROUP_NAME";
    public static final String DB_COLUMN_NAME = "DB_COLUMN_NAME";
    public static final String DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84";
    public static final String DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84";
    public static final String DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM";
    public static final String DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";

    public static final String DEFAULT_GROUP = "默认";

    private static final int DB_VERSION = 2;
    private static final String DB_NAME = "FavoriteLocation.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_GROUP TEXT NOT NULL, " +
            "DB_COLUMN_NAME TEXT, DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, " +
            "DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL, " +
            "DB_COLUMN_TIMESTAMP BIGINT NOT NULL)";
    private static final String CREATE_GROUP_TABLE = "create table if not exists " + TABLE_GROUP +
            " (DB_COLUMN_GROUP_NAME TEXT PRIMARY KEY)";

    public DataBaseFavoriteLocation(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
        sqLiteDatabase.execSQL(CREATE_GROUP_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        // 新增分组表，保留已有点位
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL(CREATE_GROUP_TABLE);
            sqLiteDatabase.execSQL("insert or ignore into " + TABLE_GROUP + " (" + DB_COLUMN_GROUP_NAME
                    + ") select distinct " + DB_COLUMN_GROUP + " from " + TABLE_NAME);
        }
    }

    public static class FavoritePoint {
        public int id;
        public String group;
        public String name;
        public String longitudeWgs84;
        public String latitudeWgs84;
        public String longitudeCustom;
        public String latitudeCustom;
    }

    public static List<String> getGroups(SQLiteDatabase sqLiteDatabase) {
        List<String> groups = new ArrayList<>();
        groups.add(DEFAULT_GROUP);

        Cursor cursor = null;
        try {
            cursor = sqLiteDatabase.rawQuery("select " + DB_COLUMN_GROUP_NAME + " from " + TABLE_GROUP
                    + " order by " + DB_COLUMN_GROUP_NAME, null);
            while (cursor.moveToNext()) {
                addGroup(groups, cursor.getString(0));
            }
            cursor.close();

            cursor = sqLiteDatabase.rawQuery("select distinct " + DB_COLUMN_GROUP + " from " + TABLE_NAME, null);
            while (cursor.moveToNext()) {
                addGroup(groups, cursor.getString(0));
            }
        } catch (Exception e) {
            XLog.e("DATABASE: get favorite groups error");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return groups;
    }

    private static void addGroup(List<String> groups, String group) {
        if (group != null && !group.isEmpty() && !groups.contains(group)) {
            groups.add(group);
        }
    }

    public static void createGroup(SQLiteDatabase sqLiteDatabase, String group) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_GROUP_NAME, group);
            sqLiteDatabase.insertWithOnConflict(TABLE_GROUP, null, contentValues,
                    SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            XLog.e("DATABASE: create favorite group error");
        }
    }

    public static List<FavoritePoint> getFavorites(SQLiteDatabase sqLiteDatabase, String group) {
        List<FavoritePoint> points = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = sqLiteDatabase.query(TABLE_NAME, null, DB_COLUMN_GROUP + " = ?",
                    new String[]{group}, null, null, DB_COLUMN_ID + " ASC", null);
            while (cursor.moveToNext()) {
                FavoritePoint point = new FavoritePoint();
                point.id = cursor.getInt(cursor.getColumnIndexOrThrow(DB_COLUMN_ID));
                point.group = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_GROUP));
                point.name = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_NAME));
                point.longitudeWgs84 = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_LONGITUDE_WGS84));
                point.latitudeWgs84 = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_LATITUDE_WGS84));
                point.longitudeCustom = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_LONGITUDE_CUSTOM));
                point.latitudeCustom = cursor.getString(cursor.getColumnIndexOrThrow(DB_COLUMN_LATITUDE_CUSTOM));
                points.add(point);
            }
        } catch (Exception e) {
            XLog.e("DATABASE: get favorites error");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return points;
    }

    public static int getFavoriteCount(SQLiteDatabase sqLiteDatabase, String group) {
        Cursor cursor = null;
        try {
            cursor = sqLiteDatabase.rawQuery("select count(*) from " + TABLE_NAME + " where "
                    + DB_COLUMN_GROUP + " = ?", new String[]{group});
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            XLog.e("DATABASE: get favorite count error");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    // 同一分组内坐标相同的点位视为同一个，直接更新名称
    public static void saveFavorite(SQLiteDatabase sqLiteDatabase, String group, String name,
                                    double longitudeWgs84, double latitudeWgs84,
                                    double longitudeCustom, double latitudeCustom) {
        try {
            String[] args = new String[]{group, Double.toString(longitudeCustom), Double.toString(latitudeCustom)};
            Cursor cursor = sqLiteDatabase.query(TABLE_NAME, new String[]{DB_COLUMN_ID},
                    DB_COLUMN_GROUP + " = ? AND " + DB_COLUMN_LONGITUDE_CUSTOM + " = ? AND "
                            + DB_COLUMN_LATITUDE_CUSTOM + " = ?", args, null, null, null);

            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_GROUP, group);
            contentValues.put(DB_COLUMN_NAME, name);
            contentValues.put(DB_COLUMN_LONGITUDE_WGS84, Double.toString(longitudeWgs84));
            contentValues.put(DB_COLUMN_LATITUDE_WGS84, Double.toString(latitudeWgs84));
            contentValues.put(DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(longitudeCustom));
            contentValues.put(DB_COLUMN_LATITUDE_CUSTOM, Double.toString(latitudeCustom));
            contentValues.put(DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            if (cursor.moveToFirst()) {
                int id = cursor.getInt(0);
                sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?",
                        new String[]{Integer.toString(id)});
            } else {
                sqLiteDatabase.insert(TABLE_NAME, null, contentValues);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("DATABASE: save favorite error");
        }
    }

    public static void updateFavoriteName(SQLiteDatabase sqLiteDatabase, int id, String name) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_NAME, name);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?",
                    new String[]{Integer.toString(id)});
        } catch (Exception e) {
            XLog.e("DATABASE: update favorite name error");
        }
    }

    public static void updateFavoriteGroup(SQLiteDatabase sqLiteDatabase, int id, String group) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_GROUP, group);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?",
                    new String[]{Integer.toString(id)});
        } catch (Exception e) {
            XLog.e("DATABASE: update favorite group error");
        }
    }

    public static void deleteFavorite(SQLiteDatabase sqLiteDatabase, int id) {
        try {
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_ID + " = ?", new String[]{Integer.toString(id)});
        } catch (Exception e) {
            XLog.e("DATABASE: delete favorite error");
        }
    }

    public static void renameGroup(SQLiteDatabase sqLiteDatabase, String oldGroup, String newGroup) {
        try {
            ContentValues groupValues = new ContentValues();
            groupValues.put(DB_COLUMN_GROUP_NAME, newGroup);
            sqLiteDatabase.update(TABLE_GROUP, groupValues, DB_COLUMN_GROUP_NAME + " = ?",
                    new String[]{oldGroup});

            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_GROUP, newGroup);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_GROUP + " = ?",
                    new String[]{oldGroup});
        } catch (Exception e) {
            XLog.e("DATABASE: rename favorite group error");
        }
    }

    public static void deleteGroup(SQLiteDatabase sqLiteDatabase, String group) {
        try {
            sqLiteDatabase.delete(TABLE_GROUP, DB_COLUMN_GROUP_NAME + " = ?", new String[]{group});
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_GROUP + " = ?", new String[]{group});
        } catch (Exception e) {
            XLog.e("DATABASE: delete favorite group error");
        }
    }
}
