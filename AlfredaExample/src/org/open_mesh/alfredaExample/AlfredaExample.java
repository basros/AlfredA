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



package org.open_mesh.alfredaExample;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import org.open_mesh.Alfredalib.*;

import java.util.ArrayList;

/**
 * Example app for AlfredA
 * Needs to implement an AlfredaOnReceiveInterface for error-handling and mapping of facts
 */
public class AlfredaExample extends Activity implements AlfredaOnReceiveInterface {

    Button btn_request_data; // request some fact
    Button btn_push_data; // push some data

    EditText edit_fact; // InputField for fact-ID (should be integer only)
    EditText edit_push_data; // InputField for arbitrary data (text only for now)


    private ListView mainListView;
    private ArrayAdapter<String> listAdapter;

    final String LOG_TAG = "AlfredaExampleLog"; // Default LogTag for debugging


    ConnectorService mService;
    boolean mBound = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);

        // Find the ListView resource.
        mainListView = (ListView) findViewById(R.id.listView);

        edit_fact = (EditText) findViewById(R.id.request_push_fact);
        edit_push_data = (EditText) findViewById(R.id.to_push_information);


        btn_request_data = (Button) findViewById(R.id.btn_start_thread);
        btn_request_data.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mBound) {
                    try {

                        int fact = Integer.valueOf(edit_fact.getText().toString());

                        mService.callAlfredaRequest(fact, AlfredaExample.this);

                        int masterCount = mService.getMasterCount();

                        //Toast.makeText(AlfredaExample.this,"there are " + masterCount + " masters online!",Toast.LENGTH_LONG).show();

                    }catch(Exception e){
                        Toast.makeText(AlfredaExample.this,"Fact must be a number!",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btn_push_data = (Button) findViewById(R.id.btn_start_push);
        btn_push_data.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBound) {
                    String pushInformation = edit_push_data.getText().toString();

                    try {
                        int fact = Integer.valueOf(edit_fact.getText().toString());
                        mService.callAlfredaPush(fact,pushInformation);

                    }catch(Exception e){
                        Toast.makeText(AlfredaExample.this,"Fact must be a number!",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        ArrayList<String> list = new ArrayList<String>();

        // Create ArrayAdapter using the list.
        listAdapter = new ArrayAdapter<String>(this, R.layout.list_element, list);

        // Set the ArrayAdapter as the ListView's adapter.
        mainListView.setAdapter(listAdapter);

         }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onStart(){
        super.onStart();
        Intent intent = new Intent(this, ConnectorService.class);
        //startService(intent);
        getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * copy from http://developer.android.com/guide/components/bound-services.html
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(LOG_TAG,"Service Bound");

            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ConnectorService.AlfredaBinder binder = (ConnectorService.AlfredaBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * remove old content from list. cleanup only.
     */
    private void clearList(){
        listAdapter.clear();
        listAdapter.notifyDataSetChanged();
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Example implementation for fact data handling. Just prints string contents into listAdapter
     * @param rp ResponsePacket - there should be content in there
     */
    @Override
    public void alfredaOnReceive(ResponsePacket rp) {

        clearList(); // remove old stuff

        for (int i = 0; i < rp.getContent().size(); i++) {
            MACAddress macAddress = MACAddress.valueOf(rp.getMacAddr().get(i));
            listAdapter.add(macAddress.toString() + " " + rp.getContent().get(i));
            listAdapter.notifyDataSetChanged();
        Log.d(LOG_TAG,"OnReceive " + Utils.byteToString(rp.gettransactionID()));
        }
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void alfredaNoMasterFound() {
        Toast.makeText(this,"No Master Found",Toast.LENGTH_LONG).show();
    }
}