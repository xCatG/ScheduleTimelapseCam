package com.cattailsw.timelapsetest.camera;

import com.cattailsw.timelapsetest.camera.util.SystemUiHider;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
@SuppressWarnings("ResourceType")
public class CameraActivity extends Activity {
    private static final String TAG = "TimeLapseCamera";

    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;

    private boolean isRecording = false;
    private long captureStartTime = -1L;
    private Button captureButton;
    private Button dummyButton;
    private AlarmManager almgr = null;
    private boolean receiverRegistered = false;
    private LalaReceiver myReceiver = null;//

    private RecScheduleData recordingSchedule = null;
    private static final String BCAST_STR = "com.cattailsw.timelapsetest.timelapse_broadcast";
    private static final int PERM_REQUEST = 42;

    private View recStat = null;
    private TextView statText = null;
    private TextView schdText = null;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERM_REQUEST:
                if (grantResults.length == 0) {
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERM_REQUEST);
        }


        setContentView(R.layout.activity_camera);
        almgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        myReceiver = new LalaReceiver();

        mPreview = (TextureView) findViewById(R.id.surface_view);
        captureButton = (Button) findViewById(R.id.button_capture);
        dummyButton = (Button) findViewById(R.id.dummy_button);
        recStat = findViewById(R.id.rec_indicator);
        statText = (TextView) findViewById(R.id.stat_text);
        schdText = (TextView) findViewById(R.id.schd_text);
        startSchdUpdate();
    }

    private void startSchdUpdate() {
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SCHEDULE, 1000);
    }

    private static final String STR_NA = "N/A";

    private void updateRecStat() {
        recStat.setVisibility(isRecording ? View.VISIBLE : View.INVISIBLE);
        if (captureStartTime < 0) {
            statText.setText(""); // don't show anything
            return;
        }

        long displayTime = System.currentTimeMillis() - captureStartTime;
        String recTime = formatTimeToHHMMSS(displayTime);

        String remain = STR_NA;
        if (recordingSchedule != null && System.currentTimeMillis() < recordingSchedule.getEndTimeInMillis()) {
            remain = formatTimeToHHMMSS(recordingSchedule.getEndTimeInMillis() - System.currentTimeMillis());
        }

        statText.setText(String.format(getString(R.string.rec_stat_text), recTime, remain));
    }

    private void updateSchdStat() {
        if (recordingSchedule != null) {
            schdText.setText(String.format(getString(R.string.sch_text), recordingSchedule.getStartTimeString()));
            schdText.setVisibility(View.VISIBLE);
        } else {
            schdText.setVisibility(View.INVISIBLE);
        }
    }

    private String formatTimeToHHMMSS(long displayTime) {
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(displayTime),
                TimeUnit.MILLISECONDS.toMinutes(displayTime) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(displayTime)), // The change is in this line
                TimeUnit.MILLISECONDS.toSeconds(displayTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(displayTime))
        );
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        //delayedHide(100);
    }

    public static final int MSG_START_RECORDING = 42;
    public static final int MSG_END_RECORDING = MSG_START_RECORDING + 1;

    public static final int MSG_UPDATE_STAT = MSG_END_RECORDING + 1;
    public static final int MSG_UPDATE_SCHEDULE = MSG_UPDATE_STAT + 1;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_RECORDING:
                    Toast.makeText(CameraActivity.this, "start recording received", Toast.LENGTH_SHORT).show();
                    startOrStopRecording();
                    scheduleEndRecording();
                    mHandler.sendEmptyMessage(MSG_UPDATE_STAT);
                    break;

                case MSG_END_RECORDING:
                    Toast.makeText(CameraActivity.this, "End recording received", Toast.LENGTH_SHORT).show();
                    mHandler.removeMessages(MSG_UPDATE_STAT);
                    startOrStopRecording();
                    scheduleStartRecording();
                    break;
                case MSG_UPDATE_STAT:
                    updateRecStat();
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_STAT, 500);
                    break;
                case MSG_UPDATE_SCHEDULE:
                    updateSchdStat();
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SCHEDULE, 1000); // update every second
                    break;
            }
            super.handleMessage(msg);
        }
    };


    private void startOrStopRecording() {
        if (isRecording) {
            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
            } catch (RuntimeException re) {
                // can't really do anything, just swallow it I guess
                re.printStackTrace();
            }
            releaseMediaRecorder(); // release the MediaRecorder object

            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Capture");
            isRecording = false;
            //releaseCamera();
            captureStartTime = -1L;
            mHandler.removeMessages(MSG_UPDATE_STAT);
            updateRecStat();
            dummyButton.setEnabled(true);
            notifyMediaScanner();
        } else {
            mCamera.release();
            new MediaPrepareTask().execute(null, null, null);
            dummyButton.setEnabled(false);
        }
    }

    private void notifyMediaScanner() {
        Uri uri = null;
        if (currOutFileName == null) {
            File f = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
            uri = Uri.fromFile(f);
        } else {
            uri = Uri.fromFile(new File(currOutFileName));
        }
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        sendBroadcast(i);
    }

    public void onCaptureClick(View view) {
        startOrStopRecording();
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    Runnable startCameraPreviewRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                setupCameraAndGetProfile();
                mCamera.lock();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();

        mHandler.postDelayed(startCameraPreviewRunnable, 1000);

        recordingSchedule = new RecScheduleData(7, 0, 0, 12 * 60 * 60 * 1000, AlarmManager.INTERVAL_DAY);

        scheduleStartRecording();
    }

    private void registerReceiver() {
        if (!receiverRegistered) {
            registerReceiver(myReceiver, new IntentFilter(BCAST_STR));
            receiverRegistered = true;
        }
    }

    private void scheduleEndRecording() {
        PendingIntent pi = createPendingIntentForReceiver(S_END);
        almgr.set(AlarmManager.RTC_WAKEUP, recordingSchedule.startTimeInMillis + recordingSchedule.recordInterval,
                pi);
    }

    private void scheduleStartRecording() {
        PendingIntent pi = createPendingIntentForReceiver(S_START);

        if (recordingSchedule.startTimeInMillis < System.currentTimeMillis()) {
            recordingSchedule.startTimeInMillis += AlarmManager.INTERVAL_DAY;
            Log.d(TAG, "set schedule to one day later");
        }

        almgr.setRepeating(AlarmManager.RTC_WAKEUP, recordingSchedule.startTimeInMillis,
                recordingSchedule.repeatInterval, pi);
        Toast.makeText(this, "recording scheduled to start at " + recordingSchedule.getStartTimeString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
        releaseBcastReceiver();
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private void releaseBcastReceiver() {
        if (receiverRegistered) {
            unregisterReceiver(myReceiver);
            receiverRegistered = false;
        }
    }

    private String currOutFileName = null;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean prepareVideoRecorder() {

        CamcorderProfile profile = null;
        try {
            profile = setupCameraAndGetProfile();
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            //e.printStackTrace();
            return false;
        }


        // BEGIN_INCLUDE (configure_media_recorder)
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(profile);

        // Step 4: Set output file
        String outFileName = CameraHelper.getOutputMediaFile(
                CameraHelper.MEDIA_TYPE_VIDEO).toString();
        Log.d(TAG, "output filename=" + outFileName);
        mMediaRecorder.setOutputFile(outFileName);
        // END_INCLUDE (configure_media_recorder)

        // Step 5: set framerate for Time Lapse capture
        //mMediaRecorder.setCaptureRate(0.1f); // take one frame per 10 seconds

        mMediaRecorder.setCaptureRate(recordingSchedule != null ? recordingSchedule.recordingFPS : 0.1f); // take one frame per 1 seconds

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            currOutFileName = outFileName;
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            currOutFileName = null;
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            currOutFileName = null;
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    // BEGIN_INCLUDE (configure_preview)
    private CamcorderProfile setupCameraAndGetProfile() throws IOException {
        mCamera = CameraHelper.getDefaultCameraInstance();

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes,
                mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_1080P);
        // fix to 1080p
        //profile.videoFrameWidth = optimalSize.width;
        //profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        //parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        mCamera.setParameters(parameters);

        mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        mCamera.startPreview();
        return profile;
    }
    // END_INCLUDE (configure_preview)

    /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                mMediaRecorder.start();

                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                CameraActivity.this.finish();
            }
            // inform the user that recording has started
            captureStartTime = System.currentTimeMillis();
            setCaptureButtonText("Stop");
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_STAT, 100);
        }
    }

    public void onDummy(View v) {
        if (recordingSchedule == null) {
            // schedule at 7am everyday and record for 12 hours
            recordingSchedule = new RecScheduleData(7, 0, 0, 12 * 60 * 60 * 1000, AlarmManager.INTERVAL_DAY);
            //recordingSchedule = new RecScheduleData(17, 0, 0, 60 * 1000 /* 1 min*/, AlarmManager.INTERVAL_DAY);
        }

        scheduleStartRecording();
    }

    public static final String SCH_START = "schedule_start_time";
    public static final String SCH_END = "schedule_end_time";
    public static final int S_START = 1;
    public static final int S_END = S_START + 1;

    final PendingIntent createPendingIntentForReceiver(int type) {
        Intent piI = new Intent(BCAST_STR);
        switch (type) {
            case S_START:
                piI.putExtra(SCH_START, true);
                break;
            case S_END:
                piI.putExtra(SCH_END, true);
                break;
        }

        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                piI, PendingIntent.FLAG_UPDATE_CURRENT);

        return pi;
    }

    private class LalaReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(SCH_START)) {
                Toast.makeText(CameraActivity.this, "start received in LalaReceiver", Toast.LENGTH_SHORT).show();
                mHandler.sendEmptyMessage(MSG_START_RECORDING);
            } else if (intent.hasExtra(SCH_END)) {
                Toast.makeText(CameraActivity.this, "end received in LalaReceiver", Toast.LENGTH_SHORT).show();
                mHandler.sendEmptyMessage(MSG_END_RECORDING);
            }
        }
    }

    private class RecScheduleData {

        long startTimeInMillis;
        long recordInterval;
        long repeatInterval; // use AlarmManger.INTERVAL*

        float recordingFPS = 0.1f; // default to 1 frame per 10 second

        RecScheduleData(int startHR, int startMin, int startSec, long recInterval, long repeatType) {
            this(startHR, startMin, startSec, recInterval, repeatType, 0.1f);
        }

        RecScheduleData(int startHR, int startMin, int startSec, long recInterval, long repeatType, float fps) {
            repeatInterval = repeatType;
            recordInterval = recInterval;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, startHR);
            calendar.set(Calendar.MINUTE, startMin);
            calendar.set(Calendar.SECOND, startSec);
            startTimeInMillis = calendar.getTimeInMillis();
            recordingFPS = fps;
        }

        public long getEndTimeInMillis() {
            return startTimeInMillis + recordInterval;
        }

        public String getStartTimeString() {
            Date d = new Date(startTimeInMillis);
            SimpleDateFormat sdf = new SimpleDateFormat();
            return sdf.format(d);
        }
    }

}
