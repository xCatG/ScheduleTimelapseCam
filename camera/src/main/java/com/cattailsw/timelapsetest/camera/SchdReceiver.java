package com.cattailsw.timelapsetest.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class SchdReceiver extends BroadcastReceiver {
    public SchdReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "got an alarm", Toast.LENGTH_SHORT).show();
    }
}
