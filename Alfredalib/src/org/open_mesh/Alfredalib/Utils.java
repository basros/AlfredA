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

import android.util.Log;

import java.net.DatagramPacket;
import java.util.List;
import java.util.Random;

/**
 * Helperclass with lots of shared code.
 * some useful debug methods
 */
public class Utils {

    public static final String LOG_TAG = "AlfredaLib";

    public static final int REQUEST_MODE = 2;
    public static final int PUSH_MODE = 3;

    public static final String CLIENTMODE_VALUE = "clientmode";
    public static final String ALFRED_FACT = "alfred_request";
    public static final String ALFRED_RANDOM_ID = "alfred_random_id";
    public static final String ALFRED_DATA = "alfred_data";

    public static final String ALFRED_CONTENT_LIST = "alfred_content";
    public static final String ALFRED_MACADDR_LIST = "alfred_macaddr";
    public static final String ALFRED_MASTER_LIST = "alfred_masters";
    public static final String ALFRED_SEQUENCE_NUMBER = "alfred_sequence_number";

    public static final int ALFRED_VERSION = 0; // change here for new features in protocol

    public final static String ALFRED_MASTER_HOST = "ALFRED_MASTER_HOST";
    public static final String SOURCE_OF_MESSAGE = "SOURCE_SOCKET";

    public static final String RECEIVED_PACKET_FROM_MASTER = "RECEIVED_PACKET_FROM_MASTER";

    public static byte[] MASTER_ANNOUNCEMENT_PACKET = new byte[] {0x01, ALFRED_VERSION}; // 00 01 00 00
    public static byte[] PUSH_DATA_PACKET = new byte[] {0x00, ALFRED_VERSION}; // 00 00
    public static byte[] TRANSACTION_FINISHED_PACKET = new byte[] {0x03, ALFRED_VERSION}; // 03 00 00 $rand $rand 00 01

    public static byte ENDOFFACTBYTE = 012; // a newline for defined as last byte of a fact


    public static int maxPushDataPacketSize = 65516; // max data -1 for /0x0a


    /**
     * @return 2 Bytes of randomness
     */
    public static byte[] generateRandomID() {
        Random rand = new Random();
        byte[] random = new byte[2];
        rand.nextBytes(random);

        return random;
    }

    /**
     * @deprecaded use MACAddress class now
     *
     * @param macAddrByte
     * @return
     */
    @Deprecated
    public static String macAddrByteToString(byte[] macAddrByte){
        return byteToString(macAddrByte);
    }

    /** Debug-Method. Prints contents of ByteArray to logcat
     *
     * @param identifier to find source in LOGCAT
     * @param bytearray to analyze
     * @param length size of bytearray
     */
    public static void logByte(String log_tag,String identifier,byte[] bytearray,int length){
        Log.d(log_tag, identifier + " byteArray size: " + Integer.toHexString(length));
        Log.d(log_tag,byteToString(bytearray));
    }

    public static String byteToString(byte[] b){
        String byteInString = "";
        for(int i = 0;i< b.length;i++){
            byteInString += Integer.toString((b[i]), 16);
        }
        return byteInString;
    }

    /**
     * Debug function
     * @param log_tag
     * @param identifier part of application which called this
     * @param dp UDP datagram
     */
    public static void printPackageInfo(String log_tag,String identifier, DatagramPacket dp){

        logByte(log_tag,"Content: " + identifier + "\n", dp.getData(), dp.getLength());
        Log.d(log_tag, Boolean.toString(dp.getAddress().isMulticastAddress()));
        Log.d(log_tag,dp.getAddress().getHostAddress());
        Log.d(log_tag,dp.getSocketAddress().toString());
    }

    /**
     * debug function for correctly printing ByteArrays.
     * copies content of ByteArray into ByteArray of correct size
     * @param bytearray
     * @param length real length of bytearray
     * @return
     */
    public static byte[] getActualByteArray(byte[] bytearray,int length){
        byte[] sized = new byte[length];

        for(int i = 0; i< length; i++){
            sized[i] = bytearray[i];
        }
        return sized;
    }

    public static byte[] integerTo2ByteArray(int i){
        byte[] b = new byte[2];
        b[0] =  (byte) (i >> 8);
        b[1] =  (byte) (i >> 0);

        return b;
    }

    public static boolean checkContentLength(byte[] content){
        if(content.length >= 65517){
            Log.w(LOG_TAG,"Message to long, could not send");
            return false;
        }else{
            return true;
        }
    }

    public static boolean isPrintableAscii(byte value)
    {
        if (value >= 32 ){
            return true;
        }
        return false;
    }

}
