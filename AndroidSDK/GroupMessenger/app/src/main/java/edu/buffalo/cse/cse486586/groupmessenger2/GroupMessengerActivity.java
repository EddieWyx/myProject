package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();

    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;

    private ConcurrentHashMap< Integer,String> idPortMap; // maintain a id and port map
    private ConcurrentHashMap< String,Integer> portIdMap; // maintain a port and id map
    private final Lock lock = new ReentrantLock(true); // LOCK

    // maintain a suggest sequence map
    private ConcurrentHashMap< Integer,ConcurrentHashMap<Integer,Double> > suggested_list;
    // msg buffer
    private PriorityBlockingQueue<saveMsg> msgBuffer;// the priority Q buffer
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    //flag
    private final int REALMSG = 0;
    private final int PROSEQ = 1;
    private final int ACK = 2;
    private final int Pin = 3;
    // for content provider
    private ContentResolver mContentResolver;
    private Uri mUri;

    // for other variable
    private int myId;
    private int seq;
    private int counter; // messge id
    private volatile int activeMember;
    private int proSeq;
    private int BREAK;
    private volatile int C;
    //private int LOCK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        // create the server socket;
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        // start the server task;
        new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        // For content Provider
        // create the content Resolver;
        mContentResolver = getContentResolver();
        // create the Uri
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

        // for initializing variable
        seq = 0;
        counter =0;
        activeMember = 5;
        proSeq =0;
        BREAK =0;
        C =0;
        // create the arrayList list
        // and portMap
        // and message Q
        idPortMap = new ConcurrentHashMap<Integer,String>();
        msgBuffer = new PriorityBlockingQueue<saveMsg>(1000, new MyComparator());
        suggested_list = new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Double>>();
        portIdMap = new ConcurrentHashMap<String,Integer>();


        idPortMap.put(0, REMOTE_PORT0);
        portIdMap.put(REMOTE_PORT0,0);
        if(myPort.compareTo(REMOTE_PORT0) == 0){myId = 0;}

        idPortMap.put(1, REMOTE_PORT1);
        portIdMap.put(REMOTE_PORT1,1);
        if(myPort.compareTo(REMOTE_PORT1) == 0){myId = 1;}

        idPortMap.put(2, REMOTE_PORT2);
        portIdMap.put(REMOTE_PORT2,2);
        if(myPort.compareTo(REMOTE_PORT2) == 0){myId = 2;}

        idPortMap.put(3, REMOTE_PORT3);
        portIdMap.put(REMOTE_PORT3,3);
        if(myPort.compareTo(REMOTE_PORT3) == 0){myId = 3;}

        idPortMap.put(4, REMOTE_PORT4);
        portIdMap.put(REMOTE_PORT4,4);
        if(myPort.compareTo(REMOTE_PORT4) == 0){myId = 4;}


        // SEND BUTTON
        // get the msg from the editText and press the send button
        final EditText editText = (EditText) findViewById(R.id.editText1);
        Button s = (Button) findViewById(R.id.button4);
        s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText("");
//                tv.append("\t"+msg);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket connection = null;

            // a loop keep accepting the connections
            while (true) {
                if (serverSocket != null) {
                    try {
                        Message inMes = null;
                        // accept the socket
                        connection = serverSocket.accept();
                        try {
                            ObjectInputStream in = new ObjectInputStream((connection.getInputStream()));
                            inMes = (Message) in.readObject();
                        } catch (ClassNotFoundException e) {
                            e.getMessage();
                        }
                        /*
                           check the receive type
                           REALMSG means this is the real message
                           PROSEQ means this is the the proposed seq
                           ACK means the final message;
                        */

                        if(inMes.getF() == REALMSG){
                            // proposed message
                            Log.e("Receive real message ",inMes.getM());
                            Message proposedSeq = new Message();
                            proposedSeq.setF(PROSEQ); proposedSeq.setC(inMes.getC());
                            proposedSeq.setS((double) proSeq); proposedSeq.setM(inMes.getM());
                            proposedSeq.setI(myId);
                            // save to the Q
                            saveMsg save = new saveMsg(inMes.getM(),inMes.getC(),inMes.getI(),(double)proSeq,myId,false);
                            msgBuffer.add(save);
                            // send the proposed back
                            if(idPortMap.containsKey(inMes.getI())) {
                                B_MultipleCast(proposedSeq, idPortMap.get(inMes.getI()));
                            }
                            // increase the proposed sequence
                            proSeq++;
                        }

                        if(inMes.getF() == PROSEQ) {
                            // received proposed sequence number
                            Log.e("Receive Proposed Seq ", inMes.getM() + " " + Integer.toString(inMes.getI()));
                            int message_id = inMes.getC();
                            int process_id = inMes.getI();
                            double temp_seq_num = inMes.getS();
                            // save to the list
                            suggested_list.get(message_id).put(process_id, temp_seq_num);
                        }


                        if(inMes.getF() == ACK){
                            // received the final sequence number message
                            Log.e(" Processing ACK Message: ",inMes.getM());
                            Double final_seq = inMes.getS() + (double)(inMes.getK()/ 10);
                            Iterator<saveMsg> it = msgBuffer.iterator();
                            saveMsg current = null;
                            while(it.hasNext()){
                                current = it.next();
                                if(current.m_id == inMes.getC() && current.j_id==inMes.getI()){
                                    current.s_id = final_seq;
                                    current.i_id = inMes.getK();
                                    current.deliverable = true;
                                    Log.e("Processing ACk ","Find that message");
                                    break;
                                }
                            }

                            msgBuffer.remove(current);
                            msgBuffer.add(current);

                            if(msgBuffer.peek().deliverable == false){
                                Log.e("Message Buffer", "False top");
                                Log.e("False is: ",msgBuffer.peek().m);
                            }

                            while((!msgBuffer.isEmpty()) && (msgBuffer.peek().deliverable == true)){

                                saveMsg curr = msgBuffer.peek();
                                msgBuffer.remove(curr);
                                Log.e("deliver the msg", curr.m);
                                // create temp content value;
                                ContentValues cv = new ContentValues();
                                // parse the info to the content value
                                cv.put(KEY_FIELD, Integer.toString(seq));
                                cv.put(VALUE_FIELD, curr.m);

                                // save to the contentProvider by content Resolver
                                mContentResolver.insert(mUri, cv);

                                // pass the msg to publishProgress to invoke the onProgres
                                String displayMsg = ""+Integer.toString(seq)+": "+curr.m;
                                publishProgress(displayMsg);
                                seq++;
                            }
                            Log.e(" Processing ACK Message ","------Finished------");


                        }

                    } catch (IOException e) {
                        Log.e("Server", "Server IO");
                    }
                } else {
                    Log.e("serverSock", "is null");
                }
            }
        }

        protected void onProgressUpdate(String... strings) {

            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView t = (TextView) findViewById(R.id.textView1);
            t.append(strReceived + "\t\n");
            t.append("\n");

            String filename = "GroupMessengerOutPut";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Message msgToSend = new Message();
            msgToSend.setC(counter);
            msgToSend.setF(REALMSG);
            msgToSend.setI(myId);
            msgToSend.setM(msgs[0]);

            // create the sequence list
            ConcurrentHashMap<Integer, Double> sqList = new ConcurrentHashMap<Integer,Double>();
            suggested_list.put(counter,sqList);
            counter++;
            //send to everyone
            Log.e(" Sending Real Message: ",Integer.toString(msgToSend.getC()) +" "+"------Start------");
            Log.e(" Sending Real Message: ",msgToSend.getM());

            for(int i : idPortMap.keySet()){
                if(idPortMap.containsKey(i)) {
                    B_MultipleCast(msgToSend, idPortMap.get(i));
                }
            }
            Log.e(" Sending Real Message: ",Integer.toString(msgToSend.getC()) +" "+"------Finished------");
            new Check_five_pro(msgToSend).start();

            // start pin other node to test failure after send all the message;
            if (C == 4) {
                new Pin().start();
            }
            C++;
            return null;
        }
    }

    // Pin Thread
    // Pin other AVD to test failure at the end of finishing process the message
    // duration is 8 second or until it finds the failure avd
    class Pin implements Runnable{
        private Thread t;

        Pin(){
        }

        @Override
        public void run() {
            int count = 8;
            while(true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("Thread", "Interrupted");
                }

                if (activeMember == 4 || count == 0) {
                    break;
                }
                Message m = new Message();
                m.setF(Pin);
                m.setM("TEST");
                m.setaNumber(activeMember);
                for (int i : idPortMap.keySet()) {
                        if (idPortMap.containsKey(i)) {
                            B_MultipleCast(m, idPortMap.get(i));
                        }
                }
                count--;
            }
        }

        public void start(){
            if(t == null){
                t =new Thread(this);
                t.start();
            }
        }
    }

    // sending message according its port
    // close the socket after it sends
    // detect if the other side is reachable or not
    // process the removing the unreachable Avd's information
    private void B_MultipleCast(Message msgToSend, String remotePort){
        try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                ObjectOutputStream out = null;
                try {
                    out = new ObjectOutputStream((socket.getOutputStream()));
                    try {
                        out.writeObject(msgToSend);
                        out.flush();
                    } catch (InvalidClassException e) {
                        Log.e("write failed", e.getMessage());
                    } catch (NotSerializableException e) {
                        Log.e("write failed2", e.getMessage());
                    }
                } catch (IOException e) {
                    Log.e("B_Multicast", "failed to send");
                    lock.lock();
                    try {
                        if (BREAK == 0) {
                            // this means the certain AVD is dead
                            Log.e("Removing ", "Start......");
                            Log.e("Remove from ", " the IdPortMap and the PortIdMap");

                            int temp = -1;
                            if (portIdMap.containsKey(remotePort)) {
                                temp = portIdMap.get(remotePort);
                                portIdMap.remove(remotePort);
                                if (idPortMap.containsKey(temp)) {
                                    idPortMap.remove(temp);
                                }
                            }
                            Log.e("Remove from ", " the suggested list");
                            for (int c : suggested_list.keySet()) {
                                if (suggested_list.containsKey(c)) {
                                    if (suggested_list.get(c).containsKey(temp)) {
                                        if (suggested_list.get(c).get(temp) != null) {
                                            suggested_list.get(c).remove(temp);
                                        }
                                    }
                                }
                            }
                            Log.e("Remove from ", " the live item");
                            activeMember = 4;

                            Log.e("Remove from ", " the queue");
                            Iterator<saveMsg> it = msgBuffer.iterator();
                            saveMsg current = null;
                            ArrayList<saveMsg> deleteList = new ArrayList<saveMsg>();
                            while (it.hasNext()) {
                                current = it.next();
                                if (current.j_id == temp) {
                                    deleteList.add(current);
                                }
                            }
                            int size = deleteList.size();
                            if (size > 0) {
                                for (Iterator<saveMsg> i = deleteList.iterator(); i.hasNext(); ) {
                                    current = i.next();
                                    if (msgBuffer.contains(current)) {
                                        msgBuffer.remove(current);
                                    }
                                }
                                ReorderQ(msgBuffer.size());
                            }
                            Log.e("Removing ", "Finished");
                        }
                    }finally {
                        lock.unlock();
                    }
                }
                socket.close();
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        }
    }

    // reorder the message Q
    private void ReorderQ(int s){
        ArrayList<saveMsg> reOrderList = new ArrayList<saveMsg>();
        for(int i =0; i<s; i++){
            Iterator<saveMsg> it = msgBuffer.iterator();
            saveMsg current = null;
            while (it.hasNext()) {
                current = it.next();
                reOrderList.add(current);
                break;
            }
            msgBuffer.remove(current);
        }
        for(int i = 0; i <reOrderList.size();i++){
            msgBuffer.add(reOrderList.get(i));
        }
    }

    // Thread for checking if all the seq # has been recived
    class Check_five_pro implements Runnable{
        private Thread t;
        Message msgToSend;
        Check_five_pro( Message m){
            this.msgToSend =m;
        }
        @Override
        public void run() {
            while(true) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Log.e("Thread", "Interrupted");
                }

                lock.lock();
                try {
                    boolean finish = false;

                    Log.e("ActiveNumber", Integer.toString(activeMember));
                    Log.e("ActiveNumber", Integer.toString(suggested_list.get(msgToSend.getC()).size()));

                    if (suggested_list.get(msgToSend.getC()).size() == activeMember) {
                        finish = true;
                    }

                    if (finish) {
                        Double largest = -1.0;
                        int the_finalize_id = 0;

                        for (int z : idPortMap.keySet()) {
                            if (idPortMap.containsKey(z)) {
                                if (suggested_list.containsKey(msgToSend.getC())) {
                                    if (suggested_list.get(msgToSend.getC()).get(z) != null) {
                                        if (largest < suggested_list.get(msgToSend.getC()).get(z)) {
                                            largest = suggested_list.get(msgToSend.getC()).get(z);
                                            the_finalize_id = z;
                                        }
                                    }
                                }
                            }
                        }

                        Message ack = new Message();
                        ack.setF(ACK);
                        ack.setI(myId);
                        ack.setC(msgToSend.getC());
                        ack.setS(largest);
                        ack.setK(the_finalize_id);
                        ack.setM(msgToSend.getM());

                        for (int i : idPortMap.keySet()) {
                            if (idPortMap.containsKey(i)) {
                                Log.e("Sending ACK message: ", Integer.toString(i));
                                WriteBackACK(ack, idPortMap.get(i));
                            }
                        }
                        break;
                    }
                }finally {
                    lock.unlock();
                }
            }
        }
        public void start(){
            if(t == null){
                t =new Thread(this);
                t.start();
            }
        }
    }

    // write back the ACK to other avd
    private void WriteBackACK(Message msgToSend, String remotePort){
        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(remotePort));
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream((socket.getOutputStream()));
                try {
                    out.writeObject(msgToSend);
                    out.flush();
                    Log.e("Sending ACK message successfully: ", msgToSend.getM());

                } catch (InvalidClassException e) {
                    //  Log.e("write failed", e.getMessage());
                } catch (NotSerializableException e) {
                    //  Log.e("write failed2", e.getMessage());
                }
            } catch (IOException e) {
                Log.e("Write Back ACK", "failed to send");
                Log.e("Sending ACK message failed: ", msgToSend.getM());
                        if (BREAK == 0) {
                            Log.e("Removing ", "Start......");
                            Log.e("Remove from ", " the IdPortMap and the PortIdMap");
                            int temp = -1;
                            if (portIdMap.containsKey(remotePort)) {
                                temp = portIdMap.get(remotePort);
                                portIdMap.remove(remotePort);
                                if (idPortMap.containsKey(temp)) {
                                    idPortMap.remove(temp);
                                }
                            }
                            Log.e("Remove from ", " the suggestedList");
                            for (int c : suggested_list.keySet()) {
                                if (suggested_list.containsKey(c)) {
                                    if (suggested_list.get(c).containsKey(temp)) {
                                        if (suggested_list.get(c).get(temp) != null) {
                                            suggested_list.get(c).remove(temp);
                                        }
                                    }
                                }
                            }

                            Log.e("Remove from ", " the live item");
                            activeMember = 4;

                            Log.e("Remove from ", " the queue");
                            Iterator<saveMsg> it = msgBuffer.iterator();

                            saveMsg current = null;
                            ArrayList<saveMsg> deleteList = new ArrayList<saveMsg>();

                            while (it.hasNext()) {
                                current = it.next();
                                if (current.j_id == temp) {
                                    deleteList.add(current);
                                }
                            }

                            int size = deleteList.size();

                            if (size > 0) {
                                for (Iterator<saveMsg> i = deleteList.iterator(); i.hasNext(); ) {
                                    current = i.next();
                                    if (msgBuffer.contains(current)) {
                                        msgBuffer.remove(current);
                                    }
                                    //i.remove();
                                }
                                ReorderQ(msgBuffer.size());
                            }
                            Log.e("Removing ", "Finished");
                        }
                socket.close();
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        }
    }

    // class for saving message
    public class saveMsg{
        String m;
        int m_id;
        int j_id; // send
        Double s_id;
        int i_id; // proposed
        boolean deliverable;
        saveMsg(String m, int m_id,int j_id, Double s_id, int i_id, boolean deliverable){
            this.deliverable=deliverable;
            this.i_id = i_id;
            this.j_id = j_id;
            this.m = m;
            this.m_id = m_id;
            this.s_id = s_id;
        }

    }

    // priority Q comparator
    private static class MyComparator implements Comparator<saveMsg> {

        @Override
        public int compare(saveMsg arg0, saveMsg arg1) {
            // TODO Auto-generated method stub
            if (arg0.s_id > arg1.s_id) return 1;
            if (arg0.s_id < arg1.s_id) return -1;
            else{
                if(arg0.deliverable == false && arg1.deliverable == true) return -1;
                if(arg0.deliverable == true && arg1.deliverable == false) return 1;
                else{
                    if(arg0.i_id > arg1.i_id) return 1;
                    if(arg0.i_id < arg1.i_id) return -1;
                }
            }
            return 0;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

}