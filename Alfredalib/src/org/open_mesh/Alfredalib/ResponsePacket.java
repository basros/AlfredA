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

import java.util.ArrayList;
import java.util.List;

public class ResponsePacket {

    public static final int RANDOM_ID_LENGTH = 2;
    public static final int SEQUENZ_NUMBER_LENGTH = 2;


    int requestID = 0; // default fact
    ArrayList<String> MacAddr;
    ArrayList<String> content;
    byte[] transactionID;
    byte[] sequenceNumber;

    /**
     *
     * @param transactionID 2 byte random IDs
     * @param requestID fact (1 byte)
     * @param macAddr MAC-Address as String-ArrayList (6 byte)
     * @param content Data as String-ArrayList
     * @param sequenceNumber 2 Byte sequence number
     */
    public ResponsePacket(byte[] transactionID, int requestID, ArrayList<String> macAddr, ArrayList<String> content, byte[] sequenceNumber) {
        this.requestID = requestID;
        this.MacAddr = macAddr;
        this.content = content;
        this.transactionID = transactionID;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Default constructor of ResponsePacket
     * initializes String-ArrayList for MAC-Addresses and Contents
     */
    public ResponsePacket() {
        MacAddr = new ArrayList<String>();
        content = new ArrayList<String>();
    }

    public ResponsePacket(byte[] transactionID){
        this();
        this.settransactionID(transactionID);
    }

    public byte[] getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(byte[] sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setRequestID(int requestID) {
        this.requestID = requestID;
    }

    public void setContent(ArrayList<String> content) {
        this.content = content;
    }

    public void setMacAddr(ArrayList<String> macAddr) {
        MacAddr = macAddr;
    }

    public int getRequestID() {
        return requestID;
    }

    public ArrayList<String> getMacAddr() {
        return MacAddr;
    }

    public ArrayList<String> getContent() {
        return content;
    }

    public byte[] gettransactionID() {
        return transactionID;
    }

    public void settransactionID(byte[] transactionID) {
        this.transactionID = transactionID;
    }
}
