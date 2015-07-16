package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {
    private yxwDatabaseHelper yxwDbh;
    private SQLiteDatabase yxw_SQL;
    public static final String dBName = "yxwDb";
    public static final int version = 1;
    public static final String TableName = "yxw_group_message";
    public static final String _Key_ = "key";
    public static final String _Value_ = "value";
    public static final String TAG = "yxwGM";

    private static final String SQL_Crete = "CREATE TABLE "+
                                            TableName
                                            +" ("
                                            +_Key_
                                            +" user, "
                                            +_Value_
                                            + " message );";

    protected static final class yxwDatabaseHelper extends SQLiteOpenHelper{
        public yxwDatabaseHelper(Context context) {
            super(context, dBName, null, version);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_Crete);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */
        long id = yxw_SQL.insertWithOnConflict(TableName,null,values,SQLiteDatabase.CONFLICT_REPLACE);

        if(id > 0) {
            Uri myUri = buildUri("content", "content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
            Uri res = ContentUris.withAppendedId(myUri,id);
            Log.v("insert", values.toString());
            getContext().getContentResolver().notifyChange(res,null);
            return res;
        }else{
            Log.e(TAG,"insert failed");
        }
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        yxwDbh = new yxwDatabaseHelper(getContext());
        yxw_SQL = yxwDbh.getWritableDatabase();
        return (yxw_SQL == null)? false: true;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */
        SQLiteQueryBuilder sqlB = new SQLiteQueryBuilder();

        // Set the table we're querying.
        sqlB.setTables(TableName);

        //set the WHERE clause in our query.
        sqlB.appendWhere(_Key_ + " = '" + selection + "'");

        // create a cursor
        Cursor c = sqlB.query(
                yxw_SQL,
                projection,
                null,
                selectionArgs,
                null,
                null,
                sortOrder);

        //let the actual manager do the query
        c.setNotificationUri(getContext().getContentResolver(), uri);
        Log.v("query", selection);
        return c;
    }
}
