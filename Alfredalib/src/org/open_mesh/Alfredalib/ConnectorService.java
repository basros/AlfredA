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



package org.open_mesh.Alfredalib;

import android.app.Service;
import android.content.*;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Connection between BackgroundService and applications with activities
 * Sends Broadcast-Intents to apps
 * Sends Broadcast-Intent to BackgroundService
 * Receives intents from BackgroundService
 */
public class ConnectorService extends Service {

    // TODO list of all randomIDs + time of request and a thread which checks if a request is in que if there
    // was an answer. if not restart request

    private final IBinder mBinder = new AlfredaBinder();

    final static String LOG_TAG = "Alfredalib";
    private byte[] randomID;

    private ArrayList<String> masters = new ArrayList<String>();

    private BroadcastReceiver yourReceiver;
    private static final String ACTION = "org.open_mesh.alfreda.android.alfreda_client";

    private AlfredaOnReceiveInterface ari = null;


    public class AlfredaBinder extends Binder {
        public ConnectorService getService() {
            // Return this instance of LocalService so clients can call public methods
            Log.d(LOG_TAG, "AlfredaConnector bound");
            return ConnectorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return mBinder;
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate() {
        super.onCreate();
        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction(ACTION);
        this.yourReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle b = intent.getExtras();
                byte[] randomID = b.getByteArray(Utils.ALFRED_RANDOM_ID); // 2 bytes

                Log.d(LOG_TAG, "received packet with random id" + Utils.byteToString(randomID)
                        + " local random id = " + Utils.byteToString(ConnectorService.this.randomID));

                ConnectorService.this.masters = b.getStringArrayList(Utils.ALFRED_MASTER_LIST);
                Log.d(Utils.LOG_TAG,"ConnectorService masters " + ConnectorService.this.masters.size());
                if(masters.size() == 0){
                    if (ari != null) {
                        ari.alfredaNoMasterFound();
                    } else {
                        Log.d(LOG_TAG, "You maybe missed to set AlfredaOnReiceiveListener." +
                                " Implement AlfredaOnReceiveInterface and set it in the connector.");
                    }
                }

                // if this is our packet
                if (Arrays.equals(randomID, ConnectorService.this.randomID)) {

                    int fact = b.getInt(Utils.ALFRED_FACT, -1);
                    ArrayList<String> content = b.getStringArrayList(Utils.ALFRED_CONTENT_LIST);

                    ArrayList<String> macAddrArray =  b.getStringArrayList(Utils.ALFRED_MACADDR_LIST);
                    byte[] sequenceNumber =  b.getByteArray(Utils.ALFRED_SEQUENCE_NUMBER);

                    // build rp packet
                    ResponsePacket rp = new ResponsePacket(
                            randomID,
                            fact,
                            macAddrArray,
                            content,
                            sequenceNumber);

                    // call implementation of AlfredaOnReceiverInterface (ari)
                    if (ari != null) {
                        ari.alfredaOnReceive(rp);
                    } else {
                        Log.d(LOG_TAG, "You maybe missed to set AlfredaOnReiceiveListener." +
                                " Implement AlfredaOnReceiveInterface and set it in the connector.");
                    }
                }
            }
        };
        this.registerReceiver(this.yourReceiver, theFilter);
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(this.yourReceiver);
        Log.d(LOG_TAG, "onDestroy");
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Sends a request for a given fact to AlfredA-Backend (AlfredaTransmitter Class)
     * Maps RandomID to calling App
     * Does error handling (no master/nothing received) via interface
     * @param fact requested fact-id
     * @param ari Interface, to be implemented by apps
     */
    public void callAlfredaRequest(int fact, AlfredaOnReceiveInterface ari) {

        this.ari = ari;
        this.randomID = Utils.generateRandomID();

        Log.d(LOG_TAG, "send request with random id " + Utils.byteToString(this.randomID));

        Intent intent = new Intent("org.open_mesh.alfreda.android.AlfredaTransmitter");
        Bundle b = new Bundle();
        b.putInt(Utils.CLIENTMODE_VALUE, Utils.REQUEST_MODE);
        b.putInt(Utils.ALFRED_FACT, fact);
        b.putByteArray(Utils.ALFRED_RANDOM_ID, this.randomID);
        intent.putExtras(b);
        //TODO combine randomID with ari

        sendBroadcast(intent);
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * sends intent to alfred backend (AlfredaTransmitter Class) with data to be pushed into alfred network
     *
     * @param fact fact-ID
     * @param data actual information
     */
    public void callAlfredaPush(int fact, String data) {

        Log.d(LOG_TAG, "announce data \"" + data + "\" to alfred-network");

        // check byte length
        if (Utils.checkContentLength(data.getBytes())){
            Intent intent = new Intent("org.open_mesh.alfreda.android.AlfredaTransmitter");
            Bundle b = new Bundle();
            b.putInt(Utils.CLIENTMODE_VALUE, Utils.PUSH_MODE);
            b.putInt(Utils.ALFRED_FACT, fact);
            b.putByteArray(Utils.ALFRED_DATA, data.getBytes());
            intent.putExtras(b);

            sendBroadcast(intent); // send to BackgroundService
        }
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void callAlfredaPush(int fact, byte[] data) {

        Log.d(LOG_TAG, "announce data \"" + data + "\" to alfred-network");

        if (Utils.checkContentLength(data)) {
            Intent intent = new Intent("org.open_mesh.alfreda.android.AlfredaTransmitter");
            Bundle b = new Bundle();
            b.putInt(Utils.CLIENTMODE_VALUE, Utils.PUSH_MODE);
            b.putInt(Utils.ALFRED_FACT, fact);
            b.putByteArray(Utils.ALFRED_DATA, data);
            intent.putExtras(b);

            sendBroadcast(intent); // send to BackgroundService
        }
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int getMasterCount(){
        Log.d(Utils.LOG_TAG,"we found " + masters.size() + " masters");
        return masters.size();
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public ArrayList<String> getMasters(){
        return masters;
    }
}