/*
* This file is part of AlfredA.
* Copyright (C) 2014 Bastian rosner
* 
* AlfredA is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* AlfredA is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with AlfredA.  If not, see <http://www.gnu.org/licenses/>.
*/



package org.open_mesh.alfreda;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.open_mesh.Alfredalib.MACAddress;
import org.open_mesh.Alfredalib.ResponsePacket;
import org.open_mesh.Alfredalib.Utils;

/**
 * Class for receiving UDP Packets from ALFRED-Masters
 * Handles Master-announcements via multicast
 * receives requested facts via unicast
 * Needs a working wifi interface
 */
public class AlfredaReceiver extends Service {

    public final static String LOG_TAG = "Alfreda Log";
    private final static int PUSHDATATIMEOUT = 10000; // in ms
    private final static int MASTERTIMEOUT = 60; // in sec
    private final static int MASTERCHECKINTERVAL = 5000; // in ms


    private DatagramSocket receiveUnicastSock;
    private WifiManager.MulticastLock mLock;


    private Map<String, byte[]> transactionIDList = new ConcurrentHashMap<String, byte[]>();

    // list of all masters with last time announced
    private HashMap<String,Long> mastersTime = new HashMap<String,Long>();

    boolean runThread = true;

    public static final int PORT = 16962; // 0x4242

    private AlfredaTransmitter transmiter = null;

    private Thread networkThread = null;
    private Thread cleanPushDataThread = null;
    private Thread cleanKnownMastersThread = null;


    @Override
    public IBinder onBind(Intent intent) {
        //TODO for communication return IBinder implementation
        return null;
    }

    @SuppressLint("NewApi")
    @Override
    public void onCreate()
    {
        Log.d(LOG_TAG,"onCreate");

        clearMasterPrefs();

        try{
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);


            transmiter = new AlfredaTransmitter();
            receiveUnicastSock = makeUnicastListenSocket();

            initSocketThread();
            transactionIDListCleaner();
            knownMasterCleaner();

        }catch(IOException e){
            e.printStackTrace();
            Log.d(LOG_TAG,"failed to initialize multisocket");
        }
    }

    /**
     * Cleanup of AlfredA SharedPreferences. Removes old Masters
     */
    private void clearMasterPrefs() {
        getSharedPreferences( "org.open_mesh.alfreda", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {

        //TODO do something useful
        return Service.START_NOT_STICKY;
    }

    /**
     * disable receiving-thread
     * disable multicast lock
     */
    @SuppressLint("NewApi")
    public void onDestroy(){
        Log.d(AlfredaReceiver.LOG_TAG,"onDestroy");
        if(networkThread.isAlive()){
            runThread = false;
        }
        super.onDestroy();
    }

    /**
     * Creates Unicast UDP Socket on Port 16962
     * @return UDP DatagramSocket on Port 16962
     */
    private DatagramSocket makeUnicastListenSocket(){
        InetSocketAddress inetAddr = new InetSocketAddress(PORT);
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(inetAddr);

        } catch (SocketException e) {
            e.printStackTrace();
        }
        return socket;
    }

    /**
     * creates a thread that listens on multicastgroup and waits for
     * a packet like acknowledgePacket to find nearest master
     */
    @SuppressLint("NewApi")
    private void initSocketThread() throws IOException {

        Runnable r = new Runnable() {
            public void run()
            {
                Log.d(LOG_TAG, "listening for packets");
                byte[] buffer = new byte[1<<16 -1 ];
                byte[] emptybuffer = new byte[1<<16 - 1];
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length); // UDP Packet

                try {
                    while (AlfredaReceiver.this.runThread) {
                        receiveUnicastSock.receive(pkt);
                        byte[] contentHeader = Arrays.copyOfRange(pkt.getData(),0,2); // get 2 byte T(L)V-Header for type-checking
                        Utils.logByte(LOG_TAG,"contentHeader",contentHeader,contentHeader.length);

                        if(isMasterAnnoncement(contentHeader)){ // if TLV of master-announcement
                            Log.d(LOG_TAG,"master announcement packet\n");
                            SharedPreferences prefs = getSharedPreferences(
                                    "org.open_mesh.alfreda", Context.MODE_PRIVATE); //get SharedPrefs
                            HashSet<String> masters = (HashSet<String>) prefs.getStringSet("masters", new HashSet<String>());
                            String masterName = pkt.getAddress().getHostAddress();

                            if(!masters.add(masterName)) { // get IPv6 LinkLocal Address of master
                                mastersTime.remove(masterName);
                            }
                            mastersTime.put(masterName,System.currentTimeMillis()/1000);

                            prefs.edit().putStringSet("masters",masters).commit(); // save address to master

                            //TODO need wait function here to relook for a package after defined time
                        }else if(isPushedDataPacket(contentHeader)){
                            Log.d(LOG_TAG,"Pushed Data Packet\n");

                            // get transactionId
                            byte[] transactionID = Arrays.copyOfRange(pkt.getData(),4,6);

                            if(transactionIDList.containsKey(transactionID)) {
                                // if there is already an entry we ignore the secound packet
                                //transactionIDList.get(transactionID).add(Arrays.copyOf(pkt.getData(),pkt.getLength()));
                            }else{
                                transactionIDList.put(new String(transactionID), Arrays.copyOfRange(pkt.getData(), 0, pkt.getLength()));
                            }
                        }else if(isTransactionFinishedPacket(contentHeader)){

                            Log.d(LOG_TAG, "tansaction finished packet\n");
                            int HEADER_LENGTH = 18;

                            byte[] transactionFinishedPacket = pkt.getData();
                            byte[] transactionID = Arrays.copyOfRange(transactionFinishedPacket, 4, 6);

                            byte[] sequenceNumber = Arrays.copyOfRange(transactionFinishedPacket, 6, 8);
                            if (!Arrays.equals(sequenceNumber,new byte[] {0x00,0x00})) {

                                // read transactionID
                                String transactionIDString = new String(transactionID);
                                try {
                                    // count packetDatalength
                                    byte[] data = transactionIDList.get(transactionIDString);

                                    ResponsePacket rp = parseResponsePaket(data, data.length); // create RP
                                    transmiter.sendRequestAnswerToApplications(rp, AlfredaReceiver.this); // send RP to app

                                    // remove key-value from list
                                    transactionIDList.remove(transactionID);

                                } catch (Exception e) {
                                    Log.w(LOG_TAG, "transactionId not in List");
                                }

                            }else{
                                // if there was no pushdata packet -> means no data for these fact
                                ResponsePacket rp = new ResponsePacket(transactionID);
                                transmiter.sendRequestAnswerToApplications(rp, AlfredaReceiver.this); // send RP to app
                            }
                        }
                        pkt.setLength(buffer.length); // clean buffer
                        pkt.setData(emptybuffer); // clean buffer
                    }
                } catch (Exception e) {
                    Log.w(LOG_TAG, "multicast receive thread malfunction", e);
                }
            }
        };
        networkThread = new Thread(r);
        networkThread.start();
    }

    private void transactionIDListCleaner(){
        Runnable r = new Runnable() {
            public void run() {
                Map<String, byte[]> oldTransactionIDList = transactionIDList;
                if(oldTransactionIDList==null) {
                    oldTransactionIDList = new HashMap<String, byte[]>();
                }
                while(AlfredaReceiver.this.runThread){
                    try {
                        Thread.sleep(PUSHDATATIMEOUT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    for(String oldValue : oldTransactionIDList.keySet()){
                        if(transactionIDList.containsKey(oldValue)){
                            transactionIDList.remove(oldValue);
                        }
                    }
                    oldTransactionIDList = transactionIDList;
                }
            }
        };

        cleanPushDataThread = new Thread(r);
        cleanPushDataThread.start();
    }

    private void knownMasterCleaner(){
        Runnable r = new Runnable() {
            public void run() {
                while(AlfredaReceiver.this.runThread){
                    try {
                        Thread.sleep(MASTERCHECKINTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    SharedPreferences prefs = getSharedPreferences(
                            "org.open_mesh.alfreda", Context.MODE_PRIVATE); //get SharedPrefs
                    HashSet<String> masters = (HashSet<String>) prefs.getStringSet("masters", new HashSet<String>());

                    for(String masterName : mastersTime.keySet()) {
                        Long masterTime = mastersTime.get(masterName);
                        if((System.currentTimeMillis()/1000 - masterTime) > MASTERTIMEOUT){
                            Log.d(LOG_TAG,"master " + masterName + " removed from List. died out!");
                            mastersTime.remove(masterName);
                            masters.remove(masterName);
                        }
                    }
                    prefs.edit().putStringSet("masters",masters).commit(); // save address to master
                }
            }
        };
        cleanKnownMastersThread = new Thread(r);
        cleanKnownMastersThread.start();
    }


    /**
     * Reads a byteArray of ALFRED-Data and extracts valid information
     * @param data ALFRED-Data
     * @param length Length of ALFRED-Data ByteArray
     * @return ResponsePacket
     */
    @SuppressLint("NewApi")
    private static ResponsePacket parseResponsePaket(byte[] data, int length){

        ResponsePacket rp = new ResponsePacket();

        rp.settransactionID(Arrays.copyOfRange(data,4,6)); // 2 byte randomID
        rp.setSequenceNumber(Arrays.copyOfRange(data,7,8)); // 2 byte number of packets


        data = Arrays.copyOfRange(data,8,length);

        while(data.length != 0){

            // see documentation http://www.open-mesh.org/projects/batman-adv/wiki/Alfred_architecture
            for(int i = 0; i < data.length; i++){
                if(data[i] == Utils.ENDOFFACTBYTE){
                    byte[] tempData = Arrays.copyOfRange(data,0,i);

                    MACAddress macAddress = new MACAddress(Arrays.copyOfRange(tempData, 0, 6));
                    rp.getMacAddr().add(macAddress.toString());

                    rp.setRequestID((char)tempData[6]);
                    String content = "";
                    for(int j = 10; j < tempData.length;j++){
                        if(Utils.isPrintableAscii(tempData[j])) {
                            content += (char) tempData[j];
                        }else{
                            tempData[j] = 0x5f;
                        }
                    }
                    rp.getContent().add(content);

                    data = Arrays.copyOfRange(data,i+1,data.length);
                }
            }
        }
        Log.d(LOG_TAG,rp.getContent().toString());
        Log.d(LOG_TAG,rp.getMacAddr().toString());
        Log.d(LOG_TAG, Utils.byteToString(rp.gettransactionID()));
        return rp;
    }


    /**
     * @return true if packet is a master announcment
     */
    private boolean isMasterAnnoncement(byte[] data){

        return Arrays.equals(data,Utils.MASTER_ANNOUNCEMENT_PACKET);
    }

    /**
     * @return true if packet is a pushed data packet
     */
    private boolean isPushedDataPacket(byte[] data){
        return Arrays.equals(data,Utils.PUSH_DATA_PACKET);

    }

    /**
     * @return true if packet is a transaction finished packet
     */
    private boolean isTransactionFinishedPacket(byte[] data){

        return Arrays.equals(data,Utils.TRANSACTION_FINISHED_PACKET);
    }
}

/*
                        }else if(isPushedDataPacket(contentHeader)){
                            Log.d(LOG_TAG,"Pushed Data Packet\n");

                            // get transactionId
                            byte[] transactionID = Arrays.copyOfRange(pkt.getData(),4,6);

                            if(transactionIDList.containsKey(transactionID)) {
                                transactionIDList.get(transactionID).add(Arrays.copyOf(pkt.getData(),pkt.getLength()));
                            }else{
                                List<byte[]> l = new ArrayList<byte[]>();
                                l.add(Arrays.copyOf(pkt.getData(),pkt.getLength()));
                                transactionIDList.put(transactionID,l);
                            }
                        }else if(isTransactionFinishedPacket(contentHeader)){

                            Log.d(LOG_TAG, "tansaction finished packet\n");
                            int HEADER_LENGTH = 18;

                            byte[] transactionFinishedPacket = pkt.getData();

                            // read transactionID
                            byte[] transactionID = Arrays.copyOfRange(transactionFinishedPacket,4,6);
                            try {
                                // count packetDatalength
                                List<byte[]> dataList = transactionIDList.get(transactionID);
                                // remove key-value from list

                                int amoutOfPackets = 0;
                                int dataLength = 0; // implements onetime header + all data
                                for (byte[] data : dataList) {
                                    if (amoutOfPackets == 0) {
                                        dataLength += data.length;
                                    } else {
                                        // get dataLength
                                        dataLength += data.length - HEADER_LENGTH;
                                    }
                                    amoutOfPackets++;
                                }

                                // get sequence number from transactionfinished packet
                                byte[] transactionFinishedPacket_sequenceNumber = Arrays.copyOfRange(transactionFinishedPacket, 6, 8);

                                // byte to int of sequence number
                                ByteBuffer helperToGetSequenceAsInt = ByteBuffer.wrap(transactionFinishedPacket_sequenceNumber);
                                int sequenceNumber = helperToGetSequenceAsInt.getInt(); // 1

                                if (sequenceNumber == amoutOfPackets) {
                                    // build rp packet
                                    ByteArrayBuffer byteBuffer = new ByteArrayBuffer(dataLength);
                                    for (int i = 0; i < dataList.size(); i++) {
                                        if (i == 0) {
                                            byteBuffer.append(dataList.get(i), 0, dataList.get(i).length);
                                        } else {
                                            byteBuffer.append(dataList.get(i), HEADER_LENGTH, dataList.get(i).length);
                                        }
                                    }
                                    transactionIDList.remove(transactionID);

                                    ResponsePacket rp = parseResponsePaket(byteBuffer.toByteArray(), byteBuffer.toByteArray().length); // create RP
                                    transmiter.sendRequestAnswerToApplications(rp, AlfredaReceiver.this); // send RP to app
                                }
                            }catch(Exception e){
                                Log.w(LOG_TAG,"transactionId not in List");
                            }
                        }

                        pkt.setLength(buffer.length); // clean buffer
                        pkt.setData(emptybuffer); // clean buffer
                    }
                } catch (Exception e) {
                    Log.w(LOG_TAG, "multicast receive thread malfunction", e);
                }
 */