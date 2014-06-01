package com.cattailsw.timelapsetest.camera;

import com.cattailsw.timelapsetest.camera.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
@SuppressWarnings("ResourceType")
public class CameraActivity extends Activity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;

    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private Button captureButton;

    private AlarmManager almgr = null;
    private boolean receiverRegistered = false;
    private LalaReceiver myReceiver = null;//


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        almgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        myReceiver = new LalaReceiver();
        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        //final View contentView = findViewById(R.id.surface_view);
        mPreview = (TextureView) findViewById(R.id.surface_view);
        captureButton = (Button) findViewById(R.id.button_capture);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, mPreview, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
//        contentView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (TOGGLE_ON_CLICK) {
//                    mSystemUiHider.toggle();
//                } else {
//                    mSystemUiHider.show();
//                }
//            }
//        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        //delayedHide(100);
    }


    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    public static final int MSG_START_RECORDING = 42;
    public static final int MSG_END_RECORDING = MSG_START_RECORDING + 1;

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case MSG_START_RECORDING:
                    Toast.makeText(CameraActivity.this, "start recording received", Toast.LENGTH_SHORT).show();
                    startOrStopRecording();
                    scheduleEndRecording();
                    break;

                case MSG_END_RECORDING:
                    Toast.makeText(CameraActivity.this, "End recording received", Toast.LENGTH_SHORT).show();
                    startOrStopRecording();
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private void scheduleEndRecording(){
        Calendar calendar = Calendar.getInstance();
        // 7 AM
//        calendar.set(Calendar.HOUR_OF_DAY, 7);
//        calendar.set(Calendar.MINUTE, 0);
//        calendar.set(Calendar.SECOND, 0);
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, 5);
        PendingIntent pi = createPendingIntentForReceiver(S_END);
        almgr.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);

    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
//        mHandler.removeCallbacks(mHideRunnable);
//        mHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void startOrStopRecording(){
        if (isRecording) {

            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
            }
            catch(RuntimeException re){
                // can't really do anything, just swallow it I guess
                re.printStackTrace();
            }
            releaseMediaRecorder(); // release the MediaRecorder object

            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Capture");
            isRecording = false;
            //releaseCamera();

        } else {
            mCamera.release();
            new MediaPrepareTask().execute(null, null, null);
        }
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
        //setupScheduledCallback();
    }

    private void registerReceiver() {
        if(!receiverRegistered){
            registerReceiver(myReceiver, new IntentFilter(BCAST_STR));
            receiverRegistered = true;
        }
    }

    private static final String BCAST_STR = "com.cattailsw.timelapsetest.timelapse_broadcast";

    private void setupScheduledCallback(){
        Calendar calendar = Calendar.getInstance();
        // 7 AM
//        calendar.set(Calendar.HOUR_OF_DAY, 7);
//        calendar.set(Calendar.MINUTE, 0);
//        calendar.set(Calendar.SECOND, 0);
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, 3);
        PendingIntent pi = createPendingIntentForReceiver(S_START);
        almgr.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
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

    private void releaseBcastReceiver(){
        if(receiverRegistered) {
            unregisterReceiver(myReceiver);
            receiverRegistered = false;
        }
    }

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
        mMediaRecorder.setCaptureRate(1.0f); // take one frame per 1 seconds
        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
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
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
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
            setCaptureButtonText("Stop");

        }
    }

    public void onDummy(View v){
       setupScheduledCallback();
    }

    public static final String SCH_START = "schedule_start_time";
    public static final String SCH_END = "schedule_end_time";
    public static final int S_START = 1;
    public static final int S_END = S_START + 1;

    final PendingIntent createPendingIntentForReceiver(int type){
        Intent piI = new Intent(BCAST_STR);
        switch(type) {
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
            if(intent.hasExtra(SCH_START)) {
                Toast.makeText(CameraActivity.this, "start received in LalaReceiver", Toast.LENGTH_SHORT).show();
                mHandler.sendEmptyMessage(MSG_START_RECORDING);
            }
            else if(intent.hasExtra(SCH_END)){
                Toast.makeText(CameraActivity.this, "end received in LalaReceiver", Toast.LENGTH_SHORT).show();
                mHandler.sendEmptyMessage(MSG_END_RECORDING);
            }
        }
    }

}
