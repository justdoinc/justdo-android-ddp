package justdo.today.ddp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import im.delight.android.ddp.Fields;


public class DBManager extends SQLiteOpenHelper {

    public static final String TAG = "DBManager";
    private final static String DB_NAME_DEFAULT = "ddp.sqlite";
    private static DBManager sInstance = null;
    // The Android's default system path of your application database.
    private static String sDBPath;
    private final Context mContext;
    private final String TABLE_DEFAULT = "ddpdefault";
    private SQLiteDatabase mDDPDatabase = null;

    /**
     * Constructor
     * Takes and keeps a reference of the passed context in order to access to
     * the application assets and resources.
     *
     * @param context
     */
    private DBManager(Context context) {
        super(context, DB_NAME_DEFAULT, null, 1);
        mContext = context;
        sDBPath = context.getFilesDir().getParentFile().getPath() + "/databases/";
    }

    /**
     * Singleton instance
     */
    public static DBManager getsInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        }
        return sInstance = new DBManager(context);
    }

    /**
     * Instead of removing existing tables one by one, we always start by copying an empty database
     * and re-create all DDP tables we need.
     */
    public final synchronized void initDataBase() throws IOException {
        // By calling this method an empty database will be created into
        // the default system path of your application so we are gonna be able to overwrite that
        // database with our database.
        this.getReadableDatabase();
        deleteDatabase();
        copyEmptyDataBase(sDBPath + DB_NAME_DEFAULT);
        openDataBase(sDBPath + DB_NAME_DEFAULT);

        // Recreate tables based on an empty database
        mDDPDatabase.beginTransaction();
        try {
            // VARCHAR in sqlite is essentially the same as TEXT
            // Ref: https://www.sqlite.org/datatype3.html  (section 3.2)
            mDDPDatabase.execSQL("CREATE TABLE IF NOT EXISTS "
                    + TABLE_DEFAULT
                    + " (`collectionName` TEXT, `documentId` TEXT, `seqId` INTEGER, `json` TEXT, PRIMARY KEY (`collectionName`, `documentId`));");

            mDDPDatabase.setTransactionSuccessful();

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            mDDPDatabase.endTransaction();
        }
    }

    /**
     * Copies your database from your local assets-folder to the just created
     * empty database in the system folder, from where it can be accessed and handled.
     * This is done by transferring byte stream.
     */
    private synchronized void copyEmptyDataBase(String dbFileName) throws IOException {

        // Open your local db as the input stream
        InputStream myInput = mContext.getAssets().open(DB_NAME_DEFAULT);

        // Open the empty db as the output stream
        OutputStream myOutput = new FileOutputStream(dbFileName);

        // transfer bytes from the inputfile to the outputfile
        byte[] buffer = new byte[1024];
        int length;
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }

        myOutput.flush();
        myOutput.close();
        myInput.close();
    }

    /***
     * Delete the database file
     */
    private synchronized void deleteDatabase() {
        if (mDDPDatabase != null) {
            mDDPDatabase.close();
        }
        try {
            boolean result = mContext.deleteDatabase(DB_NAME_DEFAULT);
            Log.d(TAG, "Delete Database result = " + result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private synchronized void openDataBase(String dbFileName) throws SQLException {
        mDDPDatabase = SQLiteDatabase.openDatabase(dbFileName, null, SQLiteDatabase.OPEN_READWRITE);
    }

    @Override
    public final synchronized void close() {
        if (mDDPDatabase != null) {
            mDDPDatabase.close();
            mDDPDatabase = null;
        }
        super.close();
    }

    @Override
    public final void onCreate(SQLiteDatabase db) {
        // intended to be blank currently
    }

    /**
     * If database file needs upgrade,
     * we delete the old one and copy the new one to complete this process,
     * no data loss should happen because it will trigger a complete download
     *
     * @param db         SQLiteDatabase
     * @param oldVersion old version number
     * @param newVersion new version number
     */
    @Override
    public final synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade from " + oldVersion + " to " + newVersion);

        try {
            initDataBase();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Methods to support DDP callbacks
     */
    public final synchronized void onDataAdded(String collectionName, String documentId, String newValuesJson) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }

        boolean alreadyInTransaction = mDDPDatabase.inTransaction();
        if (!alreadyInTransaction) {
            mDDPDatabase.beginTransaction();
        }

        // It might have some other ways to do bulk insertValues, but this is how Google Android Engineers code too. Example:
        // https://android.googlesource.com/platform/packages/apps/Camera/+/jb-release/src/com/android/camera/Storage.java
        ContentValues insertValues = new ContentValues();

        // In Default table, collectionName appears as a column
        insertValues.put("collectionName", collectionName);
        insertValues.put("documentId", documentId);
        insertValues.put("json", newValuesJson);

        try {
            // originally we used insert, but replace could avoid unique field error and save the checking step for every operation
            mDDPDatabase.replace(TABLE_DEFAULT, null, insertValues);
            if (!alreadyInTransaction) {
                mDDPDatabase.setTransactionSuccessful();
                mDDPDatabase.endTransaction();
            }
        } catch (Exception ex) {
            if (!alreadyInTransaction) {
                mDDPDatabase.endTransaction();
            }
            ex.printStackTrace();

            Toast.makeText(mContext, R.string.error_ddp, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Database ready. Broadcast the event
            Intent intent = new Intent(DDPIntents.DATA_ADDED);
            intent.putExtra(DDPExtras.COLLECTION_NAME, collectionName);
            intent.putExtra(DDPExtras.DOCUMENT_ID, documentId);
            intent.putExtra(DDPExtras.NEW_VALUES_JSON, newValuesJson);

            String projectId = (String) insertValues.get("project_id");
            if (!TextUtils.isEmpty(projectId)) {
                intent.putExtra(DDPExtras.PROJECT_ID, projectId);
            }

            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public final synchronized void beginTransaction() {
        mDDPDatabase.beginTransaction();
    }

    public final synchronized void endCommitTransaction() throws IllegalStateException {
        mDDPDatabase.setTransactionSuccessful();
        mDDPDatabase.endTransaction();
    }

    public final synchronized void endRollbackTransaction() throws IllegalStateException {
        mDDPDatabase.endTransaction();
    }

    public final synchronized void onDataChanged(String collectionName, String documentId, String updatedValuesJson, String removedValuesJson) {
        // Default collection table
        String documentJson = getDocumentJson(collectionName, documentId);
        if (documentJson != null) {
            updateDocument(collectionName, documentId, documentJson, updatedValuesJson, removedValuesJson);
            return;
        }

        // Either document or collection not found, should add back the values instead of update
        // Log.e(TAG, "Cannot find document `" + documentId + "` to update in collection `" + collectionName + "`, trying onDataAdded.");
        onDataAdded(collectionName, documentId, updatedValuesJson);
    }

    /**
     * @param collectionName
     * @param documentId
     * @param documentJson
     * @param updatedValuesJson
     * @param removedValuesJson
     */
    private void updateDocument(String collectionName, String documentId, String documentJson, String updatedValuesJson, String removedValuesJson) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }

        Fields documentFields = JsonUtil.fromJson(documentJson, Fields.class);
        Fields updatedValuesFields = JsonUtil.fromJson(updatedValuesJson, Fields.class);

        if (updatedValuesFields != null) {
            documentFields.putAll(updatedValuesFields);
        }

        String[] removedValues = JsonUtil.fromJson(removedValuesJson, String[].class);
        if (removedValues != null) {
            for (String removedKey : removedValues) {
                documentFields.remove(removedKey);
            }
        }

        documentJson = JsonUtil.toJson(documentFields);

        boolean alreadyInTransaction = mDDPDatabase.inTransaction();
        if (!alreadyInTransaction) {
            mDDPDatabase.beginTransaction();
        }

        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("json", documentJson);

            mDDPDatabase.update(TABLE_DEFAULT, contentValues, "`collectionName`= ?  AND `documentId` = ?", new String[]{collectionName, documentId});

            if (!alreadyInTransaction) {
                mDDPDatabase.setTransactionSuccessful();
                mDDPDatabase.endTransaction();
            }

            // Database ready. Broadcast the event
            Intent intent = new Intent(DDPIntents.DATA_CHANGED);
            intent.putExtra(DDPExtras.COLLECTION_NAME, collectionName);
            intent.putExtra(DDPExtras.DOCUMENT_ID, documentId);
            intent.putExtra(DDPExtras.UPDATED_VALUES_JSON, updatedValuesJson);
            intent.putExtra(DDPExtras.REMOVED_VALUES_JSON, removedValuesJson);

            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

        } catch (Exception ex) {
            if (!alreadyInTransaction) {
                mDDPDatabase.endTransaction();
            }
            ex.printStackTrace();
            Toast.makeText(mContext, R.string.error_ddp, Toast.LENGTH_LONG).show();
        }
    }


    public final synchronized void onDataRemoved(String collectionName, String documentId) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }

        boolean alreadyInTransaction = mDDPDatabase.inTransaction();
        if (!alreadyInTransaction) {
            mDDPDatabase.beginTransaction();
        }
        try {
            mDDPDatabase.delete(TABLE_DEFAULT, "`collectionName`=? and `documentId`=?", new String[]{collectionName, documentId});

            if (!alreadyInTransaction) {
                mDDPDatabase.setTransactionSuccessful();
                mDDPDatabase.endTransaction();
            }

            // Database ready. Broadcast the event
            Intent intent = new Intent(DDPIntents.DATA_REMOVED);
            intent.putExtra(DDPExtras.COLLECTION_NAME, collectionName);
            intent.putExtra(DDPExtras.DOCUMENT_ID, documentId);

            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

        } catch (Exception ex) {
            if (!alreadyInTransaction) {
                mDDPDatabase.endTransaction();
            }
            ex.printStackTrace();
            Toast.makeText(mContext, R.string.error_ddp, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get a document raw JSON string from a collection. Caller needs to know the collection is stored as an individual table or in the default table using hasCollection.
     *
     * @param collectionName
     * @param documentId
     * @return JSON string. Null if the given documentId does not exist;
     */
    @Nullable
    public final String getDocumentJson(String collectionName, String documentId) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }

        Cursor cursor;
        // Default table
        cursor = mDDPDatabase.rawQuery("SELECT `json` FROM `" + TABLE_DEFAULT + "` WHERE `collectionName` = ? AND `documentId` = ? LIMIT 1", new String[]{collectionName, documentId});

        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            String json = cursor.getString(0);
            cursor.close();
            return json;
        }

        if (cursor != null) {
            cursor.close();
        }
        return null;
    }

    /**
     * Generic query interface to get document JSON encapsulated by Fields object provided by the DDP Library.
     * The advantage of using this is the codes can be reused when switching back to InMemoryDatabase or compatible databases.
     *
     * @param collectionName
     * @param documentId
     * @return Fields object keeping the document data. Null if the given documentId does not exist;
     */
    public final Fields getDocumentFields(String collectionName, String documentId) {
        String json = getDocumentJson(collectionName, documentId);
        if (json == null) {
            return null;
        }
        return JsonUtil.fromJson(json, Fields.class);
    }

    /**
     * Return a raw SQL Query cursor based result set. Great for complex data handling
     *
     * @param rawQuerySQL
     * @param rawQuerySelectionArgs
     * @return Cursor (caller should close it after use) or null if error
     */
    public final Cursor getRawCursor(String rawQuerySQL, String[] rawQuerySelectionArgs) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }
        try {
            return mDDPDatabase.rawQuery(rawQuerySQL, rawQuerySelectionArgs);
        } catch (Exception ex) {
            // Malformed SQL Query
            ex.printStackTrace();
            return null;
        }
    }

    public final synchronized void execSQL(String sql) {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }
        mDDPDatabase.execSQL(sql);
    }

    public final SQLiteDatabase getDatabase() {
        if (mDDPDatabase == null) {
            openDataBase(sDBPath + DB_NAME_DEFAULT);
        }
        return mDDPDatabase;
    }
}