/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.superdeskclock;

import java.util.Calendar;


import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Alarm Clock alarm alert: pops visible indicator and plays alarm
 * tone. This activity is the full screen version which shows over the lock
 * screen with the wallpaper as the background.
 */
public class AlarmAlertFullScreen extends Activity {

    // These defaults must match the values in res/xml/settings.xml
    private static final String DEFAULT_SNOOZE = "10";
    private static final String DEFAULT_VOLUME_BEHAVIOR = "2";
    protected static final String SCREEN_OFF = "screen_off";

    //////////////////////////////////////////
    
    private SensorManager sensorManager;
    
    private static final int SENSOR_SHAKE = 10;
    private int total =10;
    private Button snooze;  
    private Button dismiss; 
    
    
    //////////////////////////////////////////
    
    protected Alarm mAlarm;
    private int mVolumeBehavior;
    
    
    
    
    
    //////////////////////////////////////////////////
    
    
    @Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (sensorManager != null) {// 取消监听器  
            sensorManager.unregisterListener(sensorEventListener);
           
            
        }  
	}

      
    @Override
    public boolean onKeyDown(int keyCode,KeyEvent keyEvent){
		switch (keyCode){
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_HOME:
			case KeyEvent.KEYCODE_MENU:
			case KeyEvent.KEYCODE_DEL:
			case KeyEvent.KEYCODE_CAMERA:
			case KeyEvent.KEYCODE_CALL:
			case KeyEvent.KEYCODE_MUTE:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_POWER:
			case KeyEvent.KEYCODE_SOFT_RIGHT:
				return true;
			default:
				return false;
		}
	}

    
    //////////////////////////////////////////////////
    
    
  


	// Receives the ALARM_KILLED action from the AlarmKlaxon,
    // and also ALARM_SNOOZE_ACTION / ALARM_DISMISS_ACTION from other applications
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Alarms.ALARM_SNOOZE_ACTION)) {
                snooze();
            } else if (action.equals(Alarms.ALARM_DISMISS_ACTION)) {
                dismiss(false);
            } else {
                Alarm alarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
                if (alarm != null && mAlarm.id == alarm.id) {
                    dismiss(true);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mAlarm = getIntent().getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
        
        //////////////////////////////////////
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        total = mAlarm.times;
        /////////////////////////////////////
        // Get the volume/camera button behavior setting
        final String vol =
                PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
                        DEFAULT_VOLUME_BEHAVIOR);
        mVolumeBehavior = Integer.parseInt(vol);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // Turn on the screen unless we are being launched from the AlarmAlert
        // subclass.
        if (!getIntent().getBooleanExtra(SCREEN_OFF, false)) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
//                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    );
        }

        updateLayout();

        // Register to get the alarm killed/snooze/dismiss intent.
        IntentFilter filter = new IntentFilter(Alarms.ALARM_KILLED);
        filter.addAction(Alarms.ALARM_SNOOZE_ACTION);
        filter.addAction(Alarms.ALARM_DISMISS_ACTION);
        registerReceiver(mReceiver, filter);
    }

    private void setTitle() {
        String label = mAlarm.getLabelOrDefault(this);
        TextView title = (TextView) findViewById(R.id.alertTitle);
        title.setText(label);
    }

    private void updateLayout() {
        LayoutInflater inflater = LayoutInflater.from(this);

        setContentView(inflater.inflate(R.layout.alarm_alert, null));

        /* snooze behavior: pop a snooze confirmation view, kick alarm
           manager. */
        snooze = (Button) findViewById(R.id.snooze);
        dismiss = (Button) findViewById(R.id.dismiss);
        snooze.setEnabled(false);
        dismiss.setEnabled(false);
        snooze.setText("努力摇 "+total+" 次");
        dismiss.setText("努力摇 "+total+" 次");
        snooze.requestFocus();
        snooze.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                snooze();
            }
        });

        /* dismiss button: close notification */
        dismiss.setOnClickListener(
                new Button.OnClickListener() {
                    public void onClick(View v) {
                        dismiss(false);
                    }
                });

        /* Set the title from the passed in alarm */
        setTitle();
    }

    // Attempt to snooze this alert.
    private void snooze() {
        // Do not snooze if the snooze button is disabled.
        if (!findViewById(R.id.snooze).isEnabled()) {
            dismiss(false);
            return;
        }
        final String snooze =
                PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsActivity.KEY_ALARM_SNOOZE, DEFAULT_SNOOZE);
        int snoozeMinutes = Integer.parseInt(snooze);

        final long snoozeTime = System.currentTimeMillis()
                + (1000 * 60 * snoozeMinutes);
        Alarms.saveSnoozeAlert(AlarmAlertFullScreen.this, mAlarm.id,
                snoozeTime);

        // Get the display time for the snooze and update the notification.
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(snoozeTime);

        // Append (snoozed) to the label.
        String label = mAlarm.getLabelOrDefault(this);
        label = getString(R.string.alarm_notify_snooze_label, label);

        // Notify the user that the alarm has been snoozed.
        Intent cancelSnooze = new Intent(this, AlarmReceiver.class);
        cancelSnooze.setAction(Alarms.CANCEL_SNOOZE);
        cancelSnooze.putExtra(Alarms.ALARM_ID, mAlarm.id);
        PendingIntent broadcast =
                PendingIntent.getBroadcast(this, mAlarm.id, cancelSnooze, 0);
        NotificationManager nm = getNotificationManager();
        Notification n = new Notification(R.drawable.stat_notify_alarm,
                label, 0);
        n.setLatestEventInfo(this, label,
                getString(R.string.alarm_notify_snooze_text,
                    Alarms.formatTime(this, c)), broadcast);
        n.flags |= Notification.FLAG_AUTO_CANCEL
                | Notification.FLAG_ONGOING_EVENT;
        nm.notify(mAlarm.id, n);

        String displayTime = getString(R.string.alarm_alert_snooze_set,
                snoozeMinutes);
        // Intentionally log the snooze time for debugging.
        Log.v(displayTime);

        // Display the snooze minutes in a toast.
        Toast.makeText(AlarmAlertFullScreen.this, displayTime,
                Toast.LENGTH_LONG).show();
        stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
        finish();
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    // Dismiss the alarm.
    private void dismiss(boolean killed) {
        Log.i(killed ? "Alarm killed" : "Alarm dismissed by user");
        // The service told us that the alarm has been killed, do not modify
        // the notification or stop the service.
        if (!killed) {
            // Cancel the notification and stop playing the alarm
            NotificationManager nm = getNotificationManager();
            nm.cancel(mAlarm.id);
            stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
        }
        finish();
    }

    /**
     * this is called when a second alarm is triggered while a
     * previous alert window is still active.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Log.LOGV) Log.v("AlarmAlert.OnNewIntent()");

        mAlarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

        setTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If the alarm was deleted at some point, disable snooze.
        if (Alarms.getAlarm(getContentResolver(), mAlarm.id) == null) {
            snooze = (Button) findViewById(R.id.snooze);
            snooze.setEnabled(false);
        }
        
        ///////////////////////////////
        if (sensorManager != null) {// 注册监听器  
            sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);  
            // 第一个参数是Listener，第二个参数是所得传感器类型，第三个参数值获取传感器信息的频率  
        } 
        ///////////////////////////////
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Log.LOGV) Log.v("AlarmAlert.onDestroy()");
        // No longer care about the alarm being killed.
        unregisterReceiver(mReceiver);
    }

    /*@Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Do this on key down to handle a few of the system keys.
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (event.getKeyCode()) {
            // Volume keys and camera keys dismiss the alarm
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                if (up) {
                    switch (mVolumeBehavior) {
                        case 1:
                            snooze();
                            break;

                        case 2:
                            dismiss(false);
                            break;

                        default:
                            break;
                    }
                }
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }*/

    @Override
    public void onBackPressed() {
        // Don't allow back to dismiss. This method is overriden by AlarmAlert
        // so that the dialog is dismissed.
        return;
    }
    
    
    /////////////////////////////////////////////

	/** 
     * 重力感应监听 
     */  
    private SensorEventListener sensorEventListener = new SensorEventListener() {  
  
        @Override  
        public void onSensorChanged(SensorEvent event) {  
            // 传感器信息改变时执行该方法  
            float[] values = event.values;  
            float x = values[0]; // x轴方向的重力加速度，向右为正  
            float y = values[1]; // y轴方向的重力加速度，向前为正  
            float z = values[2]; // z轴方向的重力加速度，向上为正  
            //Log.i(TAG, "x轴方向的重力加速度" + x +  "；y轴方向的重力加速度" + y +  "；z轴方向的重力加速度" + z);
            // 一般在这三个方向的重力加速度达到14就达到了摇晃手机的状态。  
            int medumValue = 13;
            
            if (Math.abs(x) > medumValue || Math.abs(y) > medumValue || Math.abs(z) > medumValue) {   
                Message msg = new Message();  
                msg.what = SENSOR_SHAKE;  
                handler.sendMessage(msg);
            }  

        }  
  
        @Override  
        public void onAccuracyChanged(Sensor sensor, int accuracy) {  
  
        }  
    };  
  
    /** 
     * 动作执行 
     */  
    Handler handler = new Handler() {  
  
        @Override  
        public void handleMessage(Message msg) {  
            super.handleMessage(msg);  
            switch (msg.what) {  
            case SENSOR_SHAKE:  
                Log.v("检测到摇晃，执行操作！");
                if(total>0){
                	total--;
                	snooze.setText("努力摇 "+total+" 次");
                    dismiss.setText("努力摇 "+total+" 次");
                }
                else if (total==0){
                	snooze.setText(AlarmAlertFullScreen.this.getString(R.string.alarm_alert_snooze_text));
                	dismiss.setText(AlarmAlertFullScreen.this.getString(R.string.alarm_alert_dismiss_text));
                	snooze.setEnabled(true);
                	dismiss.setEnabled(true);
                }
                break;  
            }  
        }  
  
    };  
    
    ////////////////////////////////////////////
    
    
}
