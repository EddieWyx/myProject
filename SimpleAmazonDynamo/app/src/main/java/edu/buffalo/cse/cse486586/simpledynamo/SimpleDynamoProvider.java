package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;

public class SimpleDynamoProvider extends ContentProvider {
    private yxwDatabaseHelper yxwDbh;
    private SQLiteDatabase yxw_SQL;
    static final int SERVER_PORT = 10000;
    private Uri mUri;
    Object lock = new Object();

    private static final String RemotePort0 = "11108";
    private static final String RemotePort1 = "11112";
    private static final String RemotePort2 = "11116";
    private static final String RemotePort3 = "11120";
    private static final String RemotePort4 = "11124";

    private static final String AVD0 = "5554";
    private static final String AVD1 = "5556";
    private static final String AVD2 = "5558";
    private static final String AVD3 = "5560";
    private static final String AVD4 = "5562";

    private static String[] RemotePortRing;
    private static String[] AVDRing;
    private static String[] IdRing;
    private static String myPort;

    private boolean isRecover = false;

    @Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
        Log.e("Delete",selection);
        String partitionPort = getPartitionPort(selection);
        suc_preSuc sucPre = get_Suc_PreSuc(partitionPort);
        String[] successors = sucPre.getSuccessors();
        Message mst = new Message("delete",selection,null,null,null);
        sendRequest(mst,partitionPort);
        sendRequest(mst,successors[0]);
        sendRequest(mst,successors[1]);
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
        Log.e("InsertKeyValue", KEY_FIELD + " " + VALUE_FIELD);
        String partitionPort = getPartitionPort(KEY_FIELD);
        Log.e("InsertPartitionPort",partitionPort);
        suc_preSuc sucPre = get_Suc_PreSuc(partitionPort);
        String[] successors = sucPre.getSuccessors();
        Log.e("InsertSuccessors",successors[0] + successors[1]);
        //String[] preSuccessors = sucPre.getSuccessors();
        Message mst = new Message("insert",KEY_FIELD,VALUE_FIELD,null,null);
        sendRequest(mst,partitionPort);
        sendRequest(mst,successors[0]);
        sendRequest(mst,successors[1]);
        return null;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
        RemotePortRing = new String[]{RemotePort4,RemotePort1,RemotePort0,RemotePort2,RemotePort3};
        AVDRing = new String[]{AVD4,AVD1,AVD0,AVD2,AVD3};
        try {
            IdRing = new String[]{genHash(AVD4),genHash(AVD1),genHash(AVD0),genHash(AVD2),genHash(AVD3)};
        }catch(NoSuchAlgorithmException e){
            Log.e("OnCreate","genHash");
            e.printStackTrace();
        }
        myPort = getMyPort();
        yxwDbh = new yxwDatabaseHelper(getContext());
        yxw_SQL = yxwDbh.getWritableDatabase();
        mUri = buildUri("content","edu.buffalo.cse.cse486586.simpledynamo.provider");

        SharedPreferences sp = this.getContext().getSharedPreferences("Fail_discovery",0);
        if(sp.getBoolean("Fail",true)){
            sp.edit().putBoolean("Fail",false).commit();
        }else{
            isRecover = true;
        }
        if(isRecover){
            new Recover().start();
        }else{
            Log.e("Normal", "Start");
        }

        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(SERVER_PORT);
        }catch(IOException e){
            Log.e("On Create", "Can't create a ServerSocket");
            return false;
        }

        new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,serverSocket);
        return yxw_SQL == null ? false : true;
    }

    private class Recover extends Thread{
        @Override
        public void run() {
            Log.e("Recovery", "Start");
            suc_preSuc surPre = get_Suc_PreSuc(myPort);
            String [] successors = surPre.getSuccessors();
            String [] preSuccessors = surPre.getPreSuccessors();
            String selection = "\"@\"";
            Log.e("Ports","own: " +myPort + " pre0: " + preSuccessors[0] +" pre1 " + preSuccessors[1]
                    + " suc0 " + successors[0] + " suc1 " +successors[1]);

            Message mst = new Message("query",selection,null,null,null);
            HashMap<String,Message.VV> result =new HashMap<String,Message.VV>();
            HashMap<String, Message.VV> pre0 = toQuery(mst,preSuccessors[0]);
            eliminate(pre0,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> pre1 = toQuery(mst,preSuccessors[1]);
            eliminate(pre1,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> suc0 = toQuery(mst,successors[0]);
            eliminate(suc0,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> suc1 = toQuery(mst,successors[1]);
            eliminate(suc1,result,selection,preSuccessors,myPort);

            for(String k: result.keySet()){
                Log.e(k, " " + result.get(k));
                ContentValues v  = new ContentValues();
                v.put(yxwDbh._Key_,k);
                v.put(yxwDbh._Value_,result.get(k).getVal());
                v.put(yxwDbh._Version_,result.get(k).getVer());
                toInsert(v);
            }
            Log.e("Recovery", "Finished");
        }
    }


	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub

        if(selection.compareTo("\"*\"")==0 || selection.compareTo("\"@\"")==0){
            suc_preSuc surPre = get_Suc_PreSuc(myPort);
            String [] successors = surPre.getSuccessors();
            String [] preSuccessors = surPre.getPreSuccessors();
            Log.e("Ports","own: " +myPort + " pre0: " + preSuccessors[0] +" pre1 " + preSuccessors[1]
                                + " suc0 " + successors[0] + " suc1 " +successors[1]);

            HashMap<String,Message.VV> result =new HashMap<String,Message.VV>();
            Log.e("query",selection + " " + "start");

            Message mst = new Message("query",selection,null,null,null);

            HashMap<String, Message.VV> partitionMap = toQuery(mst, myPort);
            eliminate(partitionMap,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> pre0 = toQuery(mst,preSuccessors[0]);
            eliminate(pre0,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> pre1 = toQuery(mst,preSuccessors[1]);
            eliminate(pre1,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> suc0 = toQuery(mst,successors[0]);
            eliminate(suc0,result,selection,preSuccessors,myPort);

            HashMap<String, Message.VV> suc1 = toQuery(mst,successors[1]);
            eliminate(suc1,result,selection,preSuccessors,myPort);

            MatrixCursor m = new MatrixCursor((new String[]{yxwDbh._Key_,yxwDbh._Value_}));

            for(String k: result.keySet()){
                Log.e(k, " " + result.get(k));
                m.newRow().add(k).add(result.get(k).getVal());
            }
            Log.e("query",selection + "finished");
            return m;
        }else{
            Log.e("query",selection + " "+ "start");
            String partitionPort = getPartitionPort(selection);
            suc_preSuc surPre = get_Suc_PreSuc(partitionPort);
            String [] successors = surPre.getSuccessors();
            String [] preSuccessors = surPre.getPreSuccessors();
            Log.e("Ports","partition: " +partitionPort + " suc0 " + successors[0] + " suc1 " +successors[1]);

            HashMap<String,Message.VV> result =new HashMap<String,Message.VV>();
            Log.e("query",selection + " " + "start");

            Message mst = new Message("query",selection,null,null,null);
            HashMap<String, Message.VV> partitionMap = toQuery(mst, partitionPort);
            eliminate(partitionMap,result,selection,preSuccessors,partitionPort);

            HashMap<String, Message.VV> suc0 = toQuery(mst,successors[0]);
            eliminate(suc0,result,selection,preSuccessors,partitionPort);

            HashMap<String, Message.VV> suc1 = toQuery(mst,successors[1]);
            eliminate(suc1,result,selection,preSuccessors,partitionPort);

            MatrixCursor m = new MatrixCursor((new String[]{yxwDbh._Key_,yxwDbh._Value_}));
            for(String k: result.keySet()){
                Log.e(k, " " + result.get(k));
                m.newRow().add(k).add(result.get(k).getVal());

            }
            Log.e("query",selection + "finished");
            return m;
        }
	}

    private void eliminate(HashMap<String, Message.VV> in, HashMap<String, Message.VV> out ,String selection,
                           String[] preSuc, String partitionPort){
        if(in != null) {
            if (selection.compareTo("\"*\"") == 0) {
                Log.e("*", "eliminating start");
                Log.e("*", preSuc[0] + " " + preSuc[1] + " " + partitionPort);
                for (String k : in.keySet()) {
                    if (out.containsKey(k)) {
                        String[] value_version = checkVersion(in.get(k), out.get(k));
                        Message.VV n = new Message.VV(value_version[0], value_version[1]);
                        out.put(k, n);
                    } else {
                        out.put(k, in.get(k));
                    }
                }
            } else if (selection.compareTo("\"@\"") == 0) {
                Log.e("@", "eliminating start");
                Log.e("@", preSuc[0] + " " + preSuc[1] + " " + partitionPort);
                for (String k : in.keySet()) {
                    String keyBelongedTo = getPartitionPort(k);
                    if (keyBelongedTo.compareTo(preSuc[0]) == 0
                            || keyBelongedTo.compareTo(preSuc[1]) == 0
                            || keyBelongedTo.compareTo(partitionPort) == 0) {
                        if (out.containsKey(k)) {
                            String[] value_version = checkVersion(in.get(k), out.get(k));
                            Message.VV n = new Message.VV(value_version[0], value_version[1]);
                            out.put(k, n);
                        } else {
                            out.put(k, in.get(k));
                        }
                    }
                }
            } else {
                Log.e("single", "eliminating start");
                for (String k : in.keySet()) {
                    if (k.compareTo(selection) == 0) {
                        if (out.containsKey(k)) {
                            String[] value_version = checkVersion(in.get(k), out.get(k));
                            Message.VV n = new Message.VV(value_version[0], value_version[1]);
                            out.put(k, n);
                        } else {
                            out.put(k, in.get(k));
                        }
                    }
                }
            }
        }else{
            Log.e("Eliminate","Null");
        }
    }

    private String[] checkVersion(Message.VV in, Message.VV out){
        String[] value_version = new String[2];
        value_version[0] = (in.getVer().compareTo(out.getVer())>0) ? in.getVal() : out.getVal();
        value_version[1] = (in.getVer().compareTo(out.getVer())>0) ? in.getVer() : out.getVer();
        return value_version;
    }

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
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
    private class ServerTask extends AsyncTask<ServerSocket,String,Void>{
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket connection = null;
            while(true){
                try{
                    connection = serverSocket.accept();
                    Message inMsg;
                    try{
                        ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
                        inMsg = (Message) in.readObject();
                        String messageType = inMsg.getReqType();
                        Log.e("Message type :", messageType);
                        if(messageType.compareTo("insert") == 0){

                            String KEY_FIELD = inMsg.getKey();
                            String VALUE_FIELD = inMsg.getValue();
                            int version = getVersion(KEY_FIELD)+1;
                            String VERSION_FIELD = Integer.toString(version);
                            Log.e("receiving to insert",KEY_FIELD + " " + VALUE_FIELD +" " + VERSION_FIELD);
                            ContentValues v  = new ContentValues();
                            v.put(yxwDbh._Key_,KEY_FIELD);
                            v.put(yxwDbh._Value_,VALUE_FIELD);
                            v.put(yxwDbh._Version_,VERSION_FIELD);
                            toInsert(v);
                        }
                        else if(messageType.compareTo("query")==0){
                              new toQueryBack(connection,inMsg).start();
                        }
                        else if(messageType.compareTo("delete")==0){
                            String selection = inMsg.getKey();
                            Log.e("Needed to be deleted",selection);
                            int f;
                            synchronized (lock) {
                                f = yxw_SQL.delete(yxwDbh.TableName, "key=?", new String[]{selection});
                            }
                            if(f == 0){
                                Log.e("Needed to be deleted","failed");
                            }else{
                                Log.e("Needed to be deleted","success");
                            }
                        }
                        else{
                            Log.e("Message Handling:","No such command");
                        }
                    }catch(ClassNotFoundException e){
                        Log.e("Incoming message","ClassNotFund");
                        e.printStackTrace();
                    }catch(IOException e){
                        Log.e("Incoming message", "IOException");
                    }
                }catch(IOException e){
                    Log.e("ServerTask","Accept failed");
                    e.printStackTrace();
                }
            }
        }
    }

    private int getVersion(String key){
        Cursor c;
        synchronized (lock) {
            c = yxw_SQL.rawQuery("SELECT version FROM " + yxwDbh.TableName + " WHERE key = ?", new String[]{key});
        }
        int version = 0;
        if(c.getCount()>0) {
            int colIn = c.getColumnIndex(yxwDbh._Version_);
            if(c.moveToFirst()==true){
                version = c.getInt(colIn);
                return version;
            }else{
                return -1;
            }
        }else {
            return -1;
        }
    }

    private class toQueryBack extends Thread{
        private Socket connection;
        private Message inMsg;
        public toQueryBack(Socket i, Message m){
            this.connection = i;
            this.inMsg = m;
        }
        public void run(){
            try {
                ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
                Cursor c;
                Log.e("receiving","to query");
                synchronized (lock) {
                    c = yxw_SQL.rawQuery("SELECT key, value, version FROM " + yxwDbh.TableName, null);
                }
                HashMap<String, Message.VV> map = new HashMap<String,Message.VV>();
                if(c != null){
                    int k = c.getColumnIndex(yxwDbh._Key_);
                    int v = c.getColumnIndex(yxwDbh._Value_);
                    int ver = c.getColumnIndex(yxwDbh._Version_);
                    if(k!=-1 && v!=-1 && ver!=-1){
                        if(c.moveToFirst()) {
                            while (c.isAfterLast() == false) {
                                String key = c.getString(k);
                                String value = c.getString(v);
                                String version = c.getString(ver);
                                Message.VV vv = new Message.VV(value,version);
                                map.put(key, vv);
                                c.moveToNext();
                            }
                        }
                    }
                }
                Message mtb = new Message("query back",null,null,null,map);
                out.writeObject(mtb);
                out.flush();
            }catch (IOException e){
                Log.e("toQueryBack","IO");
            }
        }
    }

    private Uri toInsert(ContentValues values){
        long id;
        synchronized (lock) {
            id = yxw_SQL.insertWithOnConflict(yxwDbh.TableName, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
        if(id > 0){
            // append the id;
            Uri res = ContentUris.withAppendedId(Uri.parse("edu.buffalo.cse.cse486586.simpledynamo.provider"),
                    id);
            Log.v("Insert", values.toString());
            getContext().getContentResolver().notifyChange(res, null);
            return res;
        }else{
            Log.e("Insert","insert failed");
            return null;
        }
    }

    private HashMap<String, Message.VV> toQuery(Message msgToSend, String remotePort){
        Log.e("Sending query", msgToSend.getKey() + " " + remotePort);
        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(remotePort));
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            try {
                out.writeObject(msgToSend);
                out.flush();
                socket.setSoTimeout(2200);
                Log.e("Waiting","Query response");
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Message msr = (Message) in.readObject();
                if(msr != null){
                    Log.e("Waiting","Got it back");
                    return msr.getKVVMap();
                }else{
                    Log.e("Waiting","nothing got back");
                    return null;
                }

            } catch(SocketTimeoutException e){
                Log.e("Waiting"+ " " + remotePort,"Timeout");
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                Log.e("Waiting","ClassNotFund");
                e.printStackTrace();
            } catch (InvalidClassException e) {
                Log.e("write failed", e.getMessage());
                e.printStackTrace();
            } catch (NotSerializableException e) {
                Log.e("write failed2", e.getMessage());
                e.printStackTrace();
            }
            socket.close();
        } catch (UnknownHostException e) {
            Log.e("Sending message", "ClientTask UnknownHostException");

        } catch (IOException e) {
            Log.e("Sending message", "ClientTask socket IOException");
        }
        return null;
    }

    private void sendRequest(Message msgToSend, String remotePort){
        Log.e("Sending",msgToSend.getReqType() + " " + remotePort);
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

    private String getPartitionPort(String key){
        try{
            String hashedKey = genHash(key);
            if(hashedKey.compareTo(IdRing[0])<=0 || hashedKey.compareTo(IdRing[4])>0){
                return RemotePortRing[0];
            }
            else if(hashedKey.compareTo(IdRing[0]) >0 && hashedKey.compareTo(IdRing[1])<=0){
                return RemotePortRing[1];
            }
            else if(hashedKey.compareTo(IdRing[1]) >0 && hashedKey.compareTo(IdRing[2])<=0){
                return RemotePortRing[2];
            }
            else if(hashedKey.compareTo(IdRing[2]) >0 && hashedKey.compareTo(IdRing[3])<=0){
                return RemotePortRing[3];
            }
            else if(hashedKey.compareTo(IdRing[3]) >0 && hashedKey.compareTo(IdRing[4])<=0){
                return RemotePortRing[4];
            }
            else{
                Log.e("getPartitionPort","No port found");
            }
        }catch(NoSuchAlgorithmException e){
            Log.e("getPartitionPort", "partition failed");
            e.printStackTrace();
        }
        return null;
    }

    public suc_preSuc get_Suc_PreSuc(String mPort){
        String[] successors = new String[2];
        String[] preSuccessors = new String [2];
        int pos = 0;
        for(int i = 0; i < 5; i++){
            if(mPort.compareTo(RemotePortRing[i])==0){
                pos = i;
                break;
            }
        }
        Log.e("Position",Integer.toString(pos));
        if(pos == 0){
            preSuccessors[0] = RemotePortRing[3];
            preSuccessors[1] = RemotePortRing[4];
            successors[0] = RemotePortRing[1];
            successors[1] = RemotePortRing[2];
        }
        else if(pos == 1){
            preSuccessors[0] = RemotePortRing[4];
            preSuccessors[1] = RemotePortRing[0];
            successors[0] = RemotePortRing[2];
            successors[1] = RemotePortRing[3];
        }
        else if(pos == 2){
            preSuccessors[0] = RemotePortRing[0];
            preSuccessors[1] = RemotePortRing[1];
            successors[0] = RemotePortRing[3];
            successors[1] = RemotePortRing[4];
        }
        else if(pos == 3){
            preSuccessors[0] = RemotePortRing[1];
            preSuccessors[1] = RemotePortRing[2];
            successors[0] = RemotePortRing[4];
            successors[1] = RemotePortRing[0];
        }
        else if(pos == 4){
            preSuccessors[0] = RemotePortRing[2];
            preSuccessors[1] = RemotePortRing[3];
            successors[0] = RemotePortRing[0];
            successors[1] = RemotePortRing[1];
        }else{
            Log.e("Position","Unknown");
        }
        suc_preSuc result = new suc_preSuc(successors,preSuccessors);
        return result;
    }

    protected static final class yxwDatabaseHelper extends SQLiteOpenHelper {

        public static final String dBName = "yxwDb";
        public static final int version = 1;
        public static final String TableName = "yxw_group_message";
        public static final String _Key_ = "key";
        public static final String _Value_ = "value";
        public static final String _Version_ = "version";

        //A string that defines the SQL statement for creating a table
        //reference: http://www.vogella.com/tutorials/AndroidSQLite/article.html
        private static final String SQL_Crete = "CREATE TABLE "+
                TableName
                + " ("
                + _Key_
                + " user, "
                + _Value_
                + " password, "
                + _Version_
                + " version );";

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

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private String getMyPort(){
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        String mp = String.valueOf((Integer.parseInt(portStr) * 2));
        return mp;
    }

    public static class suc_preSuc{
        String[] successors = new String[2];
        String[] preSuccessors = new String[2];
        public suc_preSuc(String[] successors, String[] preSuccessors){
            this.preSuccessors = preSuccessors;
            this.successors = successors;
        }


        public String[] getPreSuccessors() {
            return preSuccessors;
        }

        public String[] getSuccessors() {
            return successors;
        }
    }
}
