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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;


    public class AlfredaStarter extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //here, check that the network connection is available. If yes, start your service. If not, stop your service.
            Log.d(AlfredaReceiver.LOG_TAG, "Alfreda!!!");

            WifiManager wifiManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            /*
            if (wifiManager != null && wifiManager.isWifiEnabled() &&
                    wifiManager.getConnectionInfo().getSSID() != null){

                String ssid = "";
                ssid = wifiInfo.getSSID();
                if(ssid.contains("BKA")){
                    // start service
                    Log.d(AlfredaReceiver.LOG_TAG, "Alfreda started");
                    Intent i = new Intent(context, AlfredaReceiver.class);
                    context.startService(i);
                }else {
                    //stop alfreda
                    Log.d(AlfredaReceiver.LOG_TAG, "Alfreda stopped");
                    Intent i = new Intent(context, AlfredaReceiver.class);
                    context.stopService(i);
                }
            }else{
                //stop alfreda
                Log.d(AlfredaReceiver.LOG_TAG, "Alfreda stopped");
                Intent i = new Intent(context, AlfredaReceiver.class);
                context.stopService(i);
            }
            */
        }
    }