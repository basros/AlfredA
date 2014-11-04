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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Settings extends Activity implements OnClickListener {

    Button btn_start;
    Button btn_stop;
    /**
        TODO real activity with sharedPrefes:
        - deactivate alfreda
        - wifi device name
        - essid name
        - save master for time (desable multicast power save)
        - allow other application to talk to alfreda
     */


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_layout);

        btn_start = (Button) findViewById(R.id.start);
        btn_start.setOnClickListener(this);
        btn_stop = (Button) findViewById(R.id.stop);
        btn_stop.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        switch(v.getId()){
            case R.id.start:
                Intent intent = new Intent(this, AlfredaReceiver.class);
                startService(intent);
                break;
            case R.id.stop:
                Log.d(AlfredaReceiver.LOG_TAG,"STOP SERVICE");
                stopService(new Intent(this, AlfredaReceiver.class));
                break;
        }


    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        //stopService(new Intent(this, AlfredaReceiver.class));
    }
}
