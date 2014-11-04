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


import android.os.Parcelable;

/**
 * Interface to be implemented by AlfredA-Apps
 * Handles incoming ResponsePackets as the author of an app wishes
 * Needed for customized error-handling
 */
public interface AlfredaOnReceiveInterface{

    /**
     * Defines what to do with received content
     * @param rp ResponsePacket with data in it
     */
    public void alfredaOnReceive(ResponsePacket rp);

    // TODO implement error
    public void alfredaNoMasterFound();
    // (no alfreda in background , or response from master)
}
