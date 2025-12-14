package com.eveningoutpost.dexdrip.models;

import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.activeandroid.util.SQLiteUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores historical IoB values from companion apps for graphing
 */
@Table(name = "IobReading", id = BaseColumns._ID)
public class IobReading extends Model {

    private final static String TAG = "IobReading";
    private static boolean patched = false;

    @Column(name = "timestamp", unique = true, onUniqueConflicts = Column.ConflictAction.REPLACE)
    public long timestamp;

    @Column(name = "iob")
    public double iob;

    public static void create(long timestamp, double iob) {
        if (iob < 0 || timestamp <= 0) {
            UserError.Log.e(TAG, "Invalid IoB reading: timestamp=" + timestamp + ", iob=" + iob);
            return;
        }
        final IobReading reading = new IobReading();
        reading.timestamp = timestamp;
        reading.iob = iob;
        reading.saveit();
    }

    public static List<IobReading> latestForGraph(long startTime) {
        try {
            return new Select()
                    .from(IobReading.class)
                    .where("timestamp >= " + Math.max(startTime, 0))
                    .orderBy("timestamp asc")
                    .execute();
        } catch (android.database.sqlite.SQLiteException e) {
            fixUpTable();
            return new ArrayList<>();
        }
    }

    private static void fixUpTable() {
        if (patched) return;
        String[] patchup = {
                "CREATE TABLE IobReading (_id INTEGER PRIMARY KEY AUTOINCREMENT);",
                "ALTER TABLE IobReading ADD COLUMN timestamp INTEGER;",
                "ALTER TABLE IobReading ADD COLUMN iob REAL;",
                "CREATE UNIQUE INDEX index_IobReading_timestamp on IobReading(timestamp);"};

        for (String patch : patchup) {
            try {
                SQLiteUtils.execSql(patch);
            } catch (Exception e) {
                // Expected to fail if table already exists
            }
        }
        patched = true;
    }

    private Long saveit() {
        fixUpTable();
        return save();
    }
}
