package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;

public class SimpleDhtProvider extends ContentProvider {
    private yxwDatabaseHelper yxwDbh;
    private SQLiteDatabase yxw_SQL;

    static final int SERVER_PORT = 10000;
    private String master_avd = "5554";
    private String my_id = "";
    private String pre = "";
    private String suc = "";
    private boolean flag = false;
    private boolean init_at = false;
    private String back ="";
    private Uri mUri;
    private HashMap<String,String> qbMap;
    Object lock = new Object();

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(selection.compareTo("\"*\"")==0){
            Log.d("Global Delete","Start");
            synchronized (lock) {
                yxw_SQL.delete(yxwDbh.TableName, null, null);
            }
            Message m = generateMessage("Del",my_id,suc,null,null,selection,null);
            Log.d("Global Delete send suc from ", my_id + " To " + suc);
            sendRequest(m,request(suc,0));
            Log.d("Global Delete","Finished");
        }
        else if(selection.compareTo("\"@\"")==0){
            Log.d("Local Delete all", "Start");
            synchronized (lock) {
                yxw_SQL.delete(yxwDbh.TableName, null, null);
            }
            Log.d("Local Delete all","Finished");
        }
        else{
            Log.d("Delete single","Start");
            int f;
            synchronized (lock) {
                f = yxw_SQL.delete(yxwDbh.TableName, "key=?", new String[]{selection});
            }
            if(f==0){
                Log.d("Global Delete single","sending to the suc");
                Message mts = generateMessage("Del",my_id,suc,null,null,selection,null);
                Log.d("Global Delete single send suc from ", my_id + " To " + suc);
                sendRequest(mts,request(suc,0));
            }else{
                Log.d("Global Delete single","finished");
            }
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String KEY_FIELD = (String)values.get(yxwDbh._Key_);
        String VALUE_FIELD = (String)values.get(yxwDbh._Value_);
        Log.e("Insert Getting Key",KEY_FIELD);
        try {

            Log.e("Check"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
            // only one node
            if((pre.compareTo(suc) ==0 && pre.compareTo(my_id) ==0) || (pre.compareTo("")==0 && suc.compareTo("") ==0) ){
                Log.d("only one node","------");
                return toInsert(values);
            }else {
                if (genHash(my_id).compareTo(genHash(pre)) < 0) {
                   // Log.d("outer circle / two node", "curr: " + my_id + " pre: " + pre);
                    // if the key is < then the current id || greater than the pre
                    if (genHash(KEY_FIELD).compareTo(genHash(my_id)) <= 0 || genHash(KEY_FIELD).compareTo(genHash(pre)) > 0) {
                       // Log.d("outer circle", "-----");
                        return toInsert(values);
                    } else {
                        // pass to its successor
                        //Log.d("in circle", "pass to suc");
                        Log.e("Insert inCircle sending Key",KEY_FIELD + " c:"+ my_id +" s:" + suc);
                        Message mts = generateMessage("Insert", my_id, suc, null, null, KEY_FIELD, VALUE_FIELD);
                        sendRequest(mts, request(suc, 0));
                    }
                } else if (genHash(my_id).compareTo(genHash(pre)) > 0) {
                    // regular cases
                    // if pre < key <= curr;
                    //Log.d("regular case", "curr: " + my_id + " pre: " + pre);
                    if (genHash(KEY_FIELD).compareTo(genHash(my_id)) <= 0 && genHash(KEY_FIELD).compareTo(genHash(pre)) > 0) {
                        //Log.d("regular case", "belong to you");
                        return toInsert(values);
                    } else  {
                        //Log.d("regular case", "belong to others so send to suc");
                        Log.e("Insert ouCircle sending Key",KEY_FIELD + " c:"+ my_id +" s:" + suc);
                        Message mts = generateMessage("Insert", my_id, suc, null, null, KEY_FIELD, VALUE_FIELD);
                        sendRequest(mts, request(suc, 0));
                    }
                }
            }
        }catch(NoSuchAlgorithmException e){

        }
        return null;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        Log.d("On Create", "Start----------------");
        yxwDbh = new yxwDatabaseHelper(getContext());
        yxw_SQL = yxwDbh.getWritableDatabase();
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        my_id = request(myPort,1);
        mUri = buildUri("content","edu.buffalo.cse.cse486586.simpledht.provider");

        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(SERVER_PORT);
        }catch(IOException e){
            Log.e("On Create", "Can't create a ServerSocket");
            return false;
        }
        // start the server task;
        new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        Log.d("OnCreate","Server Socket created");
        qbMap = new HashMap<String,String>();
        return (yxw_SQL == null) ? false : true;

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        // get the readable database
        //yxw_SQL = yxwDbh.getReadableDatabase();
        MatrixCursor m = new MatrixCursor((new String[]{yxwDbh._Key_,yxwDbh._Value_}));
        Cursor c;
        //check if the node is the only node -->>> 5554
        if(suc.compareTo(pre) == 0 && suc.compareTo(my_id) ==0 || suc.compareTo(pre) ==0 && pre.compareTo("")==0){
            Log.e("Only one node Query","Start");
            Log.e("Seletion", selection);
            if(selection.compareTo("\"*\"")==0){
                Log.d("Global query @","start");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName, null);
                }
                return c;
            }
            else if(selection.compareTo("\"@\"") == 0){
                Log.e("Local query @","start");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName, null);
                }
                return c;
            }
            else{
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName + " WHERE key = ?", new String[]{selection});
                }
                if(c.getCount()>0) {
                    String value = "";
                    Log.e("Query",selection);
                    int colId = 0;
                    colId = c.getColumnIndex(yxwDbh._Value_);
                    c.moveToFirst();
                    value = c.getString(colId);
                    m.newRow().add(selection).add(value);
                    return m;
                }
            }
        }else{
            Log.e("many nodes query", "start");
            if(selection.compareTo("\"*\"")==0){
                Log.e("Global query *","start");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName, null);
                }
                Log.e("Global Query", "my own");
                if(c != null){
                    int k = c.getColumnIndex(yxwDbh._Key_);
                    int v = c.getColumnIndex(yxwDbh._Value_);
                    if(k!=-1 && v!=-1){
                        if(c.moveToFirst()) {
                            while (c.isAfterLast()==false) {
                                String key = c.getString(k);
                                String value = c.getString(v);
                                qbMap.put(key, value);
                                Log.e(key, " " + value);
                                //m.newRow().add(key).add(value);
                                c.moveToNext();
                            }
                        }
                    }
                }
                Log.e("Global Query", "my own finished");
                Message mts = generateMessage("Que",my_id,suc,null,null,selection,null);
                mts.setQbHash(qbMap);
                sendRequest(mts,request(suc,0));

                while(flag == false){};

                for(String k : qbMap.keySet()){
                    Log.e(k," "+ qbMap.get(k));
                    m.newRow().add(k).add(qbMap.get(k));
                }
                qbMap.clear();
                flag = false;
                Log.e("Global query *","finished");
                return m;

            }
            else if(selection.compareTo("\"@\"") == 0){
                Log.e("Local query @","start");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName, null);
                }
                return c;
            }
            else{
                Log.e("Local query single","start");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName + " WHERE key = ?", new String[]{selection});
                }
                if(c.getCount()>0){
                    String value = "";
                    Log.e("Local Query","Selection");
                    int colId = 0;
                    colId = c.getColumnIndex(yxwDbh._Value_);
                    c.moveToFirst();
                    value = c.getString(colId);
                    m.newRow().add(selection).add(value);
                    return m;
                }else{
                    Log.e("Local Query","Selection send to the successor");
                    Message mst = generateMessage("Que",my_id,suc,null,null,selection,null);
                    sendRequest(mst,request(suc,0));

                    while(flag == false){};
                    Log.e("Query",selection);
                    m.addRow(new String[]{selection,back});
                    flag = false;
                    back ="";
                    return m;
                }
            }
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    // a simple server task when the provider created
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            // if you are 5554 then pre = suc = my_id if ther is only one node
            if (my_id.compareTo(master_avd) == 0) {
                pre = suc = my_id;
                Log.d("Master node created", "Pre = " + pre + " Suc = " + suc + " current = " + my_id);
            } else {
                // sending the join request to the 5554 with you
                Log.d("sending join request", "Start---------");
                Message j = generateMessage("Join", my_id, "5554", null, null, null, null);
                sendRequest(j,request(master_avd,0));
            }

            ServerSocket serverSocket = sockets[0];
            Socket connection = null;

            // a loop keep accepting the connections
            while (true) {
                if (serverSocket != null) {
                    try {
                        Message inMes = null;
                        connection = serverSocket.accept();
                        try {
                            ObjectInputStream in = new ObjectInputStream((connection.getInputStream()));
                            inMes = (Message) in.readObject();
                        } catch (ClassNotFoundException e) {
                            e.getMessage();
                        }

                        if (inMes.getRequest().compareTo("Join") == 0) {
                            toJoin(inMes);
                        }
                        else if(inMes.getRequest().compareTo("Updating")==0){
                            Log.e("Updating before"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
                            if(inMes.getSuc()!=null){
                                Log.e("updating the suc to",inMes.getSuc());
                                suc = inMes.getSuc();
                            }
                            if(inMes.getPre()!=null){
                                Log.e("updating the pre to",inMes.getPre());
                                pre = inMes.getPre();
                            }
                            Log.e("Updating after"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
                        }
                        else if (inMes.getRequest().compareTo("Del") == 0) {
                            Log.d("Delete","Start");
                            String selection = inMes.getKey();
                            //getContext().getContentResolver().delete(mUri,selection,null);
                            delete(mUri,selection,null);

                            Log.d("Delete","Finished");
                        }
                        else if (inMes.getRequest().compareTo("Insert") == 0){

                            String KEY_FIELD = inMes.getKey();
                            String VALUE_FIELD = inMes.getValue();
                            ContentValues v  = new ContentValues();
                            v.put(yxwDbh._Key_,KEY_FIELD);
                            v.put(yxwDbh._Value_,VALUE_FIELD);
//                            getContext().getContentResolver().insert(mUri,v);
                            insert(mUri,v);
                        }
                        else if (inMes.getRequest().compareTo("Que") == 0) {
                            String selection = inMes.getKey();
                            Cursor c;
                            //yxw_SQL = yxwDbh.getReadableDatabase();
                            //if(inMes.getOrigin_avd().compareTo(my_id)!=0) {
                                if (selection.compareTo("\"*\"") == 0) {
                                    if(inMes.getOrigin_avd().compareTo(my_id)!=0) {
                                        qbMap.clear();
                                        qbMap.putAll(inMes.getQbHash());
                                        Log.d("Server Global query *", "start");
                                        synchronized (lock) {
                                            c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName, null);
                                        }
                                        if(c != null){
                                            int k = c.getColumnIndex(yxwDbh._Key_);
                                            int v = c.getColumnIndex(yxwDbh._Value_);
                                            if(k!=-1 && v!=-1){
                                                if(c.moveToFirst()) {
                                                    while (c.isAfterLast() == false) {
                                                        String key = c.getString(k);
                                                        String value = c.getString(v);
                                                        qbMap.put(key, value);
                                                        Log.e(key, " " + value);
                                                        //m.newRow().add(key).add(value);
                                                        c.moveToNext();
                                                    }
                                                }
                                            }
                                        }
                                        Message mts = generateMessage("Que", inMes.getOrigin_avd(), suc, null, null, selection, null);
                                        mts.setQbHash(qbMap);
                                        sendRequest(mts, request(suc, 0));
                                        qbMap.clear();
                                    }else{
                                        qbMap.clear();
                                        qbMap.putAll(inMes.getQbHash());
                                        flag = true;
                                    }
                                }
                                else{
                                    Log.e("Local Server query single","start");
                                    synchronized (lock) {
                                        c = yxw_SQL.rawQuery("SELECT key, value FROM " + yxwDbh.TableName + " WHERE key = ?", new String[]{selection});
                                    }
                                    if(c.getCount()>0){
                                        String value = "";
                                        Log.e("Local server Query","Selection");
                                        int colId = 0;
                                        colId = c.getColumnIndex(yxwDbh._Value_);
                                        c.moveToFirst();
                                        value = c.getString(colId);
                                        Message mts = generateMessage("BackQ",my_id,inMes.getOrigin_avd(),null,null,selection,value);
                                        sendRequest(mts,request(inMes.getOrigin_avd(),0));
                                    }else{
                                        // pass to its successor
                                        Log.e("Local server Query","pass to successor");
                                        Message mts = generateMessage("Que",inMes.getOrigin_avd(),suc,null,null,selection,null);
                                        sendRequest(mts,request(suc,0));
                                    }
                                }
                        }
                        else if (inMes.getRequest().compareTo("BackQ") == 0) {
                                back = inMes.getValue();
                                flag = true;
                        }

                    } catch (IOException e) {
                        Log.e("Server", "Server IO");
                    }
                } else {
                    Log.e("serverSock", "is null");
                }
            }

        }
    }

    private void toJoin(Message m){
        Log.d("Join","Start");
        String newId = m.getOrigin_avd();
        Log.e("New Node joined",newId);
        try {
            if(genHash(my_id).compareTo(genHash(pre)) < 0) {
                // if the key is < then the current id || greater than the pre
                Log.e("New Node","2 nodes");
                //Log.e("Check"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
                if(genHash(newId).compareTo(genHash(my_id))<0 || genHash(newId).compareTo(genHash(pre))>0){
                    Log.e("Node is out side"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
                    Log.e("Node is out", newId);
                    String t = pre;
                    pre = newId;

                    // for new id
                    Message up = generateMessage("Updating",my_id,newId,t,my_id,null,null);
                    sendRequest(up,request(newId,0));
                    // for my pre precessor;
                    Message oPre = generateMessage("Updating",my_id,t,null,newId,null,null);
                    sendRequest(oPre,request(t,0));

                }else{
                    // pass to its successor
                    Log.e("Node is in send to suc","n: " +newId + " c: "+ my_id+" s: "+suc);
                    Message mts = generateMessage("Join",newId,suc,null,null,null,null);
                    sendRequest(mts,request(suc,0));
                }
            }
            else if (genHash(my_id).compareTo(genHash(pre))>0) {
                // regular cases
                // if pre < key <= curr;
                Log.e("New node","regular");
                if(genHash(newId).compareTo(genHash(my_id))<0 && genHash(newId).compareTo(genHash(pre))>0){
                    Log.e("Node is in side"," pre: "+pre + " curr: "+ my_id +" suc: "+ suc);
                    Log.e("Node is in", newId);
                    String t = pre;
                    pre = newId;
                    // for new id
                    // for new id
                    Message up = generateMessage("Updating",my_id,newId,t,my_id,null,null);
                    sendRequest(up,request(newId,0));
                    // for my pre precessor;
                    Message oPre = generateMessage("Updating",my_id,t,null,newId,null,null);
                    sendRequest(oPre,request(t,0));
                }
                else{
                    Log.e("Node not in not in send to suc","n: " +newId + " c: "+ my_id+" s: "+suc);
                    // pass to its successor
                    Message mts = generateMessage("Join",newId,suc,null,null,m.getKey(),m.getValue());
                    sendRequest(mts,request(suc,0));
                }
            }
            //else if( genHash(suc).compareTo(genHash(pre)) == 0 && genHash(my_id).compareTo(genHash(pre)) == 0){
            else{
                // meaning only one node --> 5554
                //if(genHash(newId).compareTo(genHash(my_id))<0){
                Log.e("Node join","Only one node");
                suc = pre = newId;
                Message up = generateMessage("Updating",my_id,newId,my_id,my_id,null,null);
                sendRequest(up,request(newId,0));
                //}
            }
        }catch(NoSuchAlgorithmException e){

        }
    }
    private Uri toInsert(ContentValues values){
        //yxw_SQL = yxwDbh.getWritableDatabase();
        long id;
        synchronized (lock) {
             id = yxw_SQL.insertWithOnConflict(yxwDbh.TableName, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
        if(id > 0){
            // append the id;
            Uri res = ContentUris.withAppendedId(Uri.parse("edu.buffalo.cse.cse486586.simpledht.provider"),
                    id);
            Log.v("insert", values.toString());
            getContext().getContentResolver().notifyChange(res, null);
            return res;
        }else{
            Log.e("Insert","insert failed");
            return null;
        }
    }

    /*
    * send the request message;
    * */
    private void sendRequest(Message msgToSend, String remotePort){
        Log.e("Sending: ", msgToSend.getDestination_acd());

        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(remotePort));
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            try {
                out.writeObject(msgToSend);
                out.flush();
            } catch (InvalidClassException e) {
                Log.e("write failed", e.getMessage());
            } catch (NotSerializableException e) {
                Log.e("write failed2", e.getMessage());
            }
            socket.close();
        } catch (UnknownHostException e) {
            Log.e("Sending message", "ClientTask UnknownHostException");

        } catch (IOException e) {
            Log.e("Sending message", "ClientTask socket IOException");
        }
    }


    // my dhHelper for creating the content provider
    protected static final class yxwDatabaseHelper extends SQLiteOpenHelper {
        public static final String dBName = "yxwDb";
        public static final int version = 1;
        public static final String TableName = "yxw_group_message";
        public static final String _Key_ = "key";
        public static final String _Value_ = "value";

        //A string that defines the SQL statement for creating a table
        //reference: http://www.vogella.com/tutorials/AndroidSQLite/article.html
        private static final String SQL_Crete = "CREATE TABLE "+
                TableName
                + " ("
                + _Key_
                + " user, "
                + _Value_
                + " password );";

        yxwDatabaseHelper(Context context){
            super(context,dBName,null,version);
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
    //generate sending message
    private Message generateMessage(String r,
                                    String oAvd,String dAvd,
                                    String p, String s,
                                    String key, String value){
        Message m = new Message();
        m.setRequest(r);
        m.setOrigin_avd(oAvd);
        m.setDestination_acd(dAvd);
        m.setPre(p);
        m.setSuc(s);
        m.setKey(key);
        m.setValue(value);
        return m;
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    // request the port ->>> flag =0;
    // request the avd  ->>> flag =1;
    private String request(String port_or_avd, int flag){
        if(flag == 0){
            // request the port number by providing the avd;
            if(port_or_avd.compareTo("5554") == 0){
                return "11108";
            }
            else if(port_or_avd.compareTo("5556") == 0){
                return "11112";
            }
            else if(port_or_avd.compareTo("5558") == 0){
                return "11116";
            }
            else if(port_or_avd.compareTo("5560") == 0){
                return "11120";
            }
            else if(port_or_avd.compareTo("5562") == 0){
                return "11124";
            }
        }
        else if(flag ==1){
            // request the avd number by providing the port number;
            if(port_or_avd.compareTo("11108") == 0){
                return "5554";
            }
            else if(port_or_avd.compareTo("11112") == 0){
                return "5556";
            }
            else if(port_or_avd.compareTo("11116") == 0){
                return "5558";
            }
            else if(port_or_avd.compareTo("11120") == 0){
                return "5560";
            }
            else if(port_or_avd.compareTo("11124") == 0){
                return "5562";
            }
        }
        return null;
    }
}

