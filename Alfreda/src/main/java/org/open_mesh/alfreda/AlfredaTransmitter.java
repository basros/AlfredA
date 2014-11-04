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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;


import org.apache.http.util.ByteArrayBuffer;
import org.open_mesh.Alfredalib.MACAddress;
import org.open_mesh.Alfredalib.ResponsePacket;
import org.open_mesh.Alfredalib.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AlfredaTransmitter extends BroadcastReceiver {

    DatagramSocket transmitSock = null;

    private InetAddress ipv6AlfredMaster = null;

    private Context context = null;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(AlfredaReceiver.LOG_TAG,"received Broadcast");

        this.context = context;

        Bundle b = intent.getExtras();

        assert b != null;
        int mode = b.getInt(Utils.CLIENTMODE_VALUE, -1);

        switch(mode){
            // request data
            case Utils.REQUEST_MODE:
                int fact = b.getInt(Utils.ALFRED_FACT, -1);
                byte[] randomID = b.getByteArray(Utils.ALFRED_RANDOM_ID);
                arrangeMaster();

                if(transmitSock == null){
                    try{
                        transmitSock = new DatagramSocket();
                    } catch (SocketException e) {
                        transmitSock = null;
                        e.printStackTrace();
                    }
                    byte[] requestData = buildRequestPacket(fact,randomID);
                    sendDataToMaster(requestData);
                }
                break;

            // push data
            case Utils.PUSH_MODE:
                // get values from bundle
                int push_fact = b.getInt(Utils.ALFRED_FACT, -1);
                byte[] data = b.getByteArray(Utils.ALFRED_DATA);

                arrangeMaster();

                // push data to alfred network
                if(transmitSock == null){
                    try{
                        transmitSock = new DatagramSocket();
                    } catch (SocketException e) {
                        transmitSock = null;
                        e.printStackTrace();
                    }
                    handlePushData(data,push_fact);
                }
                break;
            default:
                // do nothing
                break;
        }
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Broadcasts an Intent to registered apps
     *
     * @param rp content of the data we got from alfred-master (TEMP)
     */
    public void sendRequestAnswerToApplications(ResponsePacket rp,Context context){

        Intent i = new Intent("org.open_mesh.alfreda.android.alfreda_client");
        Bundle b = new Bundle();
        b.putByteArray(Utils.ALFRED_RANDOM_ID,rp.gettransactionID());

        b.putInt(Utils.ALFRED_FACT,rp.getRequestID());
        b.putStringArrayList(Utils.ALFRED_CONTENT_LIST, rp.getContent());
        b.putStringArrayList(Utils.ALFRED_MACADDR_LIST, rp.getMacAddr());

        // put amount of masters

        b.putByteArray(Utils.ALFRED_SEQUENCE_NUMBER, rp.getSequenceNumber());

        i.putExtras(b);

        context.sendBroadcast(i);
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Builds a RequestPacket for a given fact. Resulting array can be used with sendToMaster()
     * See http://www.open-mesh.org/projects/batman-adv/wiki/Alfred_architecture#Request-data for specs
     * @param requestID subject/topic client wants to receive (ID >= 64)
     * @return request packet to be sent to master
     */
    private byte[] buildRequestPacket(int requestID, byte[] randomID){
        // 02 00 03 $rand $rand $ID
        // Request + 2 random bytes
        byte[] requestType_txID = new byte[]{(byte) (requestID),
                randomID[0],
                randomID[1]};

        // calc length as uint16_t
        byte[] requestType_txID_length = new byte[] {(byte) (requestType_txID.length) ,
                (byte) (requestType_txID.length << 8) };

        byte[] header = new byte[] { (byte) (Utils.REQUEST_MODE), // TLV- TYPE-LENGTH-VERSION
                (byte) (Utils.ALFRED_VERSION),
                requestType_txID_length[1],
                requestType_txID_length[0] };

        // assemble data part of packet
        byte[] data = new byte[requestType_txID.length + header.length];

        System.arraycopy(header, 0, data, 0, header.length);
        System.arraycopy(requestType_txID, 0, data, header.length, requestType_txID.length);
        return data;
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     *
     * @param data
     * @param fact
     */
    private void handlePushData(byte[] data, int fact){

        byte[] transactionID = Utils.generateRandomID();

        // max data length: 65517
        int sequencenumber = 0;

        byte[] pushDataPacket = buildPushPacket(fact, data, transactionID, sequencenumber);

        Utils.logByte(AlfredaReceiver.LOG_TAG, "push packet", pushDataPacket, pushDataPacket.length);
        sendDataToMaster(pushDataPacket);


        // send transactionFinished Packet
        byte[] sequenceNumberByte = Utils.integerTo2ByteArray(sequencenumber+1);
        byte[] transactionfinishedPacket = {Utils.TRANSACTION_FINISHED_PACKET[0],
                                            Utils.TRANSACTION_FINISHED_PACKET[1],
                                            0x00,
                                            0x04, // length
                                            transactionID[0],
                                            transactionID[1],
                                            sequenceNumberByte[0],
                                            sequenceNumberByte[1]};

        sendDataToMaster(transactionfinishedPacket);

    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * creates a PushDataPacket
     * see http://www.open-mesh.org/projects/batman-adv/wiki/Alfred_architecture#Push-data for specs
     * @param fact
     * @param data
     * @param transactionID
     * @param sequenceNumber
     * @return ByteArray to be sent to master with sendToMaster()
     */
    private byte[] buildPushPacket(int fact, byte[] data, byte[] transactionID, int sequenceNumber){

        // clean up data stream
        for(int i = 0; i < data.length;i++){
            if(!Utils.isPrintableAscii(data[i])){
                data[i] = 0x5f;
            }
        }

        byte[] finaldata = new byte[data.length+1];
        for(int i = 0; i < data.length;i++){
            finaldata[i] = data[i];
        }
        finaldata[finaldata.length-1] = Utils.ENDOFFACTBYTE;

        // PUSH_DATA[0] & VERSION[0]
        byte[] header = Utils.PUSH_DATA_PACKET;
        int packetLength = 14 + finaldata.length;
        byte[] length = Utils.integerTo2ByteArray(packetLength);

        // if there is more than one packet, sequence number tells us which one
        byte[] sequenceNumberByte = Utils.integerTo2ByteArray(sequenceNumber);
        byte[] macAddr = getWifiMACAddr(); // to check if correct
        Log.d(AlfredaReceiver.LOG_TAG,"data length " + finaldata.length);

        //byte[] data_length = ByteBuffer.allocateDirect(2).putInt(data.length).array();
        byte[] tlv_header = new byte[4];

        tlv_header[0] = (byte) fact; // type
        tlv_header[1] = 0x00; // version

        byte[] data_length = Utils.integerTo2ByteArray(finaldata.length);

        tlv_header[2] = data_length[0];
        tlv_header[3] = data_length[1];

        // put all together (18 = normal header length)
        int finalPacketLength = 18 + finaldata.length;

        ByteArrayBuffer byteBuffer = new ByteArrayBuffer(finalPacketLength);
        byteBuffer.append(header,0,header.length);
        byteBuffer.append(length,0,length.length);
        byteBuffer.append(transactionID,0,transactionID.length);
        byteBuffer.append(sequenceNumberByte,0,sequenceNumberByte.length);
        byteBuffer.append(macAddr,0,macAddr.length);
        byteBuffer.append(tlv_header,0,tlv_header.length);
        byteBuffer.append(finaldata,0,finaldata.length);

        return byteBuffer.toByteArray();
    }

    /**
     * Asks the WifiManager for MAC-Address of wifi-device
     * @return MAC-Address of Wifi-Device
     */
    // TODO handle null
    public byte[] getWifiMACAddr() {
        WifiManager wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);

        if (wifi != null) {
            Log.d(AlfredaReceiver.LOG_TAG,"wifi MacAddr" + wifi.getConnectionInfo().getMacAddress());

            String[] mac = wifi.getConnectionInfo().getMacAddress().split(":");
            String clearMacAddr = "";
            for(String s : mac){
                clearMacAddr+=s;
            }
            MACAddress macAddr = MACAddress.valueOf(wifi.getConnectionInfo().getMacAddress());

            return macAddr.toBytes();
        }
        return null;
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void sendDataToMaster(byte[] idata)
    {
        final byte[] data = idata;
        Runnable r = new Runnable()
        {
            public void run()
            {
                try {
                    transmitSock.send(new DatagramPacket(data,data.length,ipv6AlfredMaster, AlfredaReceiver.PORT));
                    Log.d(AlfredaReceiver.LOG_TAG,"packet send to master");
                } catch (IOException e) {
                    Log.w(AlfredaReceiver.LOG_TAG, "fail to send request packet to master try again later", e);
                } catch (Exception e1) {

                    // send...toapplication

                    Log.w(AlfredaReceiver.LOG_TAG, "warrning, no master found");
                }
            }
        };
        new Thread(r, "transmit").start();
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Looks in SharedPreferences String-List for Alfred-Masters
     * @return IPv6 LinkLocal Address of Alfred-Master
     */
    @SuppressLint("NewApi")
    private String getMasterAddr(){
        SharedPreferences prefs = this.context.getSharedPreferences(
                "org.open_mesh.alfreda", Context.MODE_PRIVATE);
        Set<String> masters = prefs.getStringSet("masters", new HashSet<String>());

        for(String m : masters){
            Log.d(AlfredaReceiver.LOG_TAG,"Master " + m);
        }

        Log.d(AlfredaReceiver.LOG_TAG,"masters size in getAddr " + masters.size());
        if(masters.iterator().hasNext()){
            return masters.iterator().next();
        }
        return null;
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles Alfred-Masters and their timeouts
     */
    private void arrangeMaster() {
        String alfredMaster = getMasterAddr();
        if(alfredMaster==null){
            //TODO do this again in time(when a master is apeared
            Log.d(AlfredaReceiver.LOG_TAG,"couldn't request data. No known master\n " +
                    "please check if there is a master in your network");
            return;
        }
        Log.d(AlfredaReceiver.LOG_TAG,"build request packet to " + alfredMaster);
        try {
            this.ipv6AlfredMaster = InetAddress.getByName(alfredMaster);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}