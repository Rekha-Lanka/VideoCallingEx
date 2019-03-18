package com.sample.videocallingex;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.intel.webrtc.base.MediaCodecs.AudioCodec.OPUS;
import static com.intel.webrtc.base.MediaCodecs.AudioCodec.PCMU;
import static com.intel.webrtc.base.MediaCodecs.VideoCodec.H264;
import static com.intel.webrtc.base.MediaCodecs.VideoCodec.VP8;
import static com.intel.webrtc.base.MediaConstraints.VideoTrackConstraints.CameraFacing.BACK;
import static com.intel.webrtc.base.MediaConstraints.VideoTrackConstraints.CameraFacing.FRONT;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.intel.webrtc.base.ActionCallback;
import com.intel.webrtc.base.AudioCodecParameters;
import com.intel.webrtc.base.ContextInitialization;
import com.intel.webrtc.base.IcsError;
import com.intel.webrtc.base.IcsVideoCapturer;
import com.intel.webrtc.base.LocalStream;
import com.intel.webrtc.base.MediaConstraints;
import com.intel.webrtc.base.MediaConstraints.VideoTrackConstraints;
import com.intel.webrtc.base.VideoCapturer;
import com.intel.webrtc.base.VideoCodecParameters;
import com.intel.webrtc.base.VideoEncodingParameters;
import com.intel.webrtc.conference.ConferenceClient;
import com.intel.webrtc.conference.ConferenceClientConfiguration;
import com.intel.webrtc.conference.ConferenceInfo;
import com.intel.webrtc.conference.Participant;
import com.intel.webrtc.conference.Publication;
import com.intel.webrtc.conference.PublishOptions;
import com.intel.webrtc.conference.RemoteMixedStream;
import com.intel.webrtc.conference.RemoteStream;
import com.intel.webrtc.conference.SubscribeOptions;
import com.intel.webrtc.conference.SubscribeOptions.AudioSubscriptionConstraints;
import com.intel.webrtc.conference.SubscribeOptions.VideoSubscriptionConstraints;
import com.intel.webrtc.conference.Subscription;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.RTCStatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.socket.client.Manager;

public class MainActivity extends AppCompatActivity
        implements VideoFragment.VideoFragmentListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        ConferenceClient.ConferenceClientObserver, NavigationView.OnNavigationItemSelectedListener,
        View.OnClickListener {
    private Boolean isFabOpen = false;
    MenuItem action_icon;
    ImageView fabright,fab0,fab1,fab2,fab3;
    private Animation fab_open,fab_close,rotate_forward,rotate_backward;
    private final int interval = 1000; // 1 Second
    static final int STATS_INTERVAL_MS = 5000;
    private static final String TAG = "ICS_CONF";
    private static final int ICS_REQUEST_CODE = 100;
    private static boolean contextHasInitialized = false;
    EglBase rootEglBase;
    private boolean fullScreen = false;
   // private boolean settingsCurrent = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Timer statsTimer;
   // private LoginFragment loginFragment;
    private VideoFragment videoFragment;
    private SettingsFragment settingsFragment;
    private View fragmentContainer;
    private View bottomView;
    private Button leftBtn, rightBtn, middleBtn;
    private ConferenceClient conferenceClient;
    private ConferenceInfo conferenceInfo;
    private Publication publication;
    private Subscription subscription;
    private LocalStream localStream;
    private RemoteStream stream2Sub;
    private IcsVideoCapturer capturer;
    private LocalStream screenStream;
    private IcsScreenCapturer screenCapturer;
    private Publication screenPublication;
    private SurfaceViewRenderer localRenderer, remoteRenderer;
    private boolean isChecked = true;



    ActionBar actionBar;
    //Toolbar toolbar;
    DrawerLayout drawer;
    private View.OnClickListener screenControl = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (fullScreen) {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.hide();
                }
                bottomView.setVisibility(View.GONE);
                fullScreen = false;
            } else {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.show();
                }
                bottomView.setVisibility(View.VISIBLE);
                fullScreen = true;
            }
        }
    };

//    private View.OnClickListener settings = new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
////            if (settingsCurrent) {
////                switchFragment(loginFragment);
////                rightBtn.setText(R.string.settings);
////            }
// //           else {
//                if (settingsFragment == null) {
//                    settingsFragment = new SettingsFragment();
//                }
//                switchFragment(settingsFragment);
//
//               // rightBtn.setText(R.string.back);
//            rightBtn.setVisibility(View.GONE);
// //           }
// //           settingsCurrent = !settingsCurrent;
//        }
//    };
    private View.OnClickListener leaveRoom = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            executor.execute(() -> conferenceClient.leave());
        }
    };
    private View.OnClickListener unpublish = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            localRenderer.setVisibility(View.GONE);
            rightBtn.setText(R.string.publish);
            rightBtn.setOnClickListener(publish);
            videoFragment.clearStats(true);

            executor.execute(() -> {
                publication.stop();
                localStream.detach(localRenderer);

                capturer.stopCapture();
                capturer.dispose();
                capturer = null;

                localStream.dispose();
                localStream = null;
            });
        }
    };
    private View.OnClickListener publish = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            rightBtn.setEnabled(false);
            rightBtn.setTextColor(Color.DKGRAY);
            executor.execute(() -> {
                boolean front = settingsFragment == null || settingsFragment.cameraFront;
                VideoTrackConstraints.CameraFacing cameraFacing = front ? FRONT : BACK;
                boolean vga = settingsFragment == null || settingsFragment.resolutionVGA;
                VideoTrackConstraints vmc =
                        VideoTrackConstraints.create(true)
                                .setCameraFacing(cameraFacing)
                                .setResolution(vga ? 640 : 1280, vga ? 480 : 720);
                capturer = new IcsVideoCapturer(vmc);
                localStream = new LocalStream(capturer,
                        new MediaConstraints.AudioTrackConstraints());
                localStream.attach(localRenderer);

                VideoEncodingParameters h264 = new VideoEncodingParameters(H264);
                VideoEncodingParameters vp8 = new VideoEncodingParameters(VP8);

                PublishOptions options = PublishOptions.builder()
                        .addVideoParameter(h264)
                        .addVideoParameter(vp8)
                        .build();

                ActionCallback<Publication> callback = new ActionCallback<Publication>() {
                    @Override
                    public void onSuccess(final Publication result) {
                        runOnUiThread(() -> {
                            localRenderer.setVisibility(View.VISIBLE);

                            rightBtn.setEnabled(true);
                            rightBtn.setTextColor(Color.WHITE);
                            rightBtn.setText(R.string.unpublish);
                            rightBtn.setOnClickListener(unpublish);
                        });

                        publication = result;

                        try {
                            JSONArray mixBody = new JSONArray();
                            JSONObject body = new JSONObject();
                            body.put("op", "add");
                            body.put("path", "/info/inViews");
                            body.put("value", "common");
                            mixBody.put(body);

                            String serverUrl = settingsFragment.getServerUrl();
                            String uri = serverUrl
                                    + "/rooms/" + conferenceInfo.id()
                                    + "/streams/" + result.id();
                            HttpUtils.request(uri, "PATCH", mixBody.toString(), true);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(final IcsError error) {
                        runOnUiThread(() -> {
                            rightBtn.setEnabled(true);
                            rightBtn.setTextColor(Color.WHITE);
                            rightBtn.setText(R.string.publish);
                            Toast.makeText(MainActivity.this,
                                    "Failed to publish " + error.errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });

                    }
                };

                conferenceClient.publish(localStream, options, callback);
            });
        }
    };
    private View.OnClickListener joinRoom = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            leftBtn.setEnabled(false);
            leftBtn.setTextColor(Color.DKGRAY);
            leftBtn.setText(R.string.connecting);
            rightBtn.setEnabled(false);
            rightBtn.setTextColor(Color.DKGRAY);

            executor.execute(() -> {
                String serverUrl = settingsFragment.getServerUrl();
                String roomId = settingsFragment == null ? "" : settingsFragment.getRoomId();

                JSONObject joinBody = new JSONObject();
                try {
                    joinBody.put("role", "presenter");
                    joinBody.put("username", "user");
                    joinBody.put("room", roomId.equals("") ? "" : roomId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String uri = serverUrl + "/createToken/";
                String token = HttpUtils.request(uri, "POST", joinBody.toString(), true);

                conferenceClient.join(token, new ActionCallback<ConferenceInfo>() {
                    @Override
                    public void onSuccess(ConferenceInfo conferenceInfo) {
                        MainActivity.this.conferenceInfo = conferenceInfo;
                        requestPermission();
                    }

                    @Override
                    public void onFailure(IcsError e) {
                        runOnUiThread(() -> {
                            leftBtn.setEnabled(true);
                            leftBtn.setTextColor(Color.WHITE);
                            leftBtn.setText(R.string.connect);
                            rightBtn.setEnabled(true);
                            rightBtn.setTextColor(Color.WHITE);
                        });
                    }
                });
            });
        }
    };
    private View.OnClickListener shareScreen = new View.OnClickListener() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onClick(View v) {
            middleBtn.setEnabled(false);
            middleBtn.setTextColor(Color.DKGRAY);
            if (middleBtn.getText().equals("ShareScreen")) {
                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(manager.createScreenCaptureIntent(), ICS_REQUEST_CODE);
            } else {
                executor.execute(() -> {
                    if (screenPublication != null) {
                        screenPublication.stop();
                        screenPublication = null;
                    }
                });
                middleBtn.setEnabled(true);
                middleBtn.setTextColor(Color.WHITE);
                middleBtn.setText(R.string.share_screen);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_videomain);
        fullScreen = true;
        bottomView = findViewById(R.id.bottom_bar);
        fragmentContainer = findViewById(R.id.fragment_container);
        leftBtn = findViewById(R.id.multi_func_btn_left);
        leftBtn.setOnClickListener(joinRoom);
        rightBtn = findViewById(R.id.multi_func_btn_right);
        rightBtn.setOnClickListener(publish);
        rightBtn.setVisibility(View.GONE);
        middleBtn = findViewById(R.id.multi_func_btn_middle);
        middleBtn.setOnClickListener(shareScreen);
        middleBtn.setVisibility(View.GONE);
      //  toolbar = (Toolbar) findViewById(R.id.toolbar);
       // setSupportActionBar(toolbar);

      //   actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.setDisplayHomeAsUpEnabled(true);
//        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);


//        loginFragment = new LoginFragment();
//        switchFragment(loginFragment);
          settingsFragment=new SettingsFragment();
          switchFragment(settingsFragment);
        initConferenceClient();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, R.drawable.settings, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        fabright=(ImageView)findViewById(R.id.fabright);
        fab0 = (ImageView) findViewById(R.id.fab0);
        fab1 = (ImageView) findViewById(R.id.fab1);
        fab2 = (ImageView) findViewById(R.id.fab2);
        fab3 = (ImageView) findViewById(R.id.fab3);

        fab_open = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.rotate_backward);



        fabright.setOnClickListener(this);
        fab0.setOnClickListener(this);
        fab1.setOnClickListener(this);
        fab2.setOnClickListener(this);
        fab3.setOnClickListener(this);


    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    @Override
    protected void onPause() {
        if (localStream != null) {
            localStream.detach(localRenderer);
        }
        if (stream2Sub != null) {
            stream2Sub.detach(remoteRenderer);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (localStream != null) {
            localStream.attach(localRenderer);
        }
        if (stream2Sub != null) {
            stream2Sub.attach(remoteRenderer);
        }
    }

    private void initConferenceClient() {
        rootEglBase = EglBase.create();

        if (!contextHasInitialized) {
            ContextInitialization.create()
                    .setApplicationContext(this)
                    .setCodecHardwareAccelerationEnabled(true)
                    .setVideoHardwareAccelerationOptions(
                            rootEglBase.getEglBaseContext(),
                            rootEglBase.getEglBaseContext())
                    .initialize();
            contextHasInitialized = true;
        }

        HttpUtils.setUpINSECURESSLContext();
//        PeerConnection.RTCConfiguration rtcConfiguration =
//                new PeerConnection.RTCConfiguration(new LinkedList<>());
//        rtcConfiguration.continualGatheringPolicy =
//                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        ConferenceClientConfiguration configuration
                = ConferenceClientConfiguration.builder()
                //.setRTCConfiguration(rtcConfiguration)
                .setHostnameVerifier(HttpUtils.hostnameVerifier)
                .setSSLContext(HttpUtils.sslContext)
                .build();
        conferenceClient = new ConferenceClient(configuration);
        conferenceClient.addObserver(this);
    }

    private void requestPermission() {
        String[] permissions = new String[]{Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO};

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    permission) != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        permissions,
                        ICS_REQUEST_CODE);
                return;
            }
        }

        onConnectSucceed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == ICS_REQUEST_CODE
                && grantResults.length == 2
                && grantResults[0] == PERMISSION_GRANTED
                && grantResults[1] == PERMISSION_GRANTED) {
            onConnectSucceed();
        }
    }

    private void onConnectSucceed() {
        runOnUiThread(() -> {
            if (videoFragment == null) {
                //action_icon.setVisible(true);
                fabright.setVisibility(View.VISIBLE);
                videoFragment = new VideoFragment();

            }
            videoFragment.setListener(MainActivity.this);
            switchFragment(videoFragment);
            leftBtn.setEnabled(true);
            leftBtn.setTextColor(Color.WHITE);
            leftBtn.setText(R.string.disconnect);
            leftBtn.setOnClickListener(leaveRoom);
            rightBtn.setEnabled(true);
            rightBtn.setTextColor(Color.WHITE);
            rightBtn.setText(R.string.publish);
            rightBtn.setOnClickListener(publish);
            fragmentContainer.setOnClickListener(screenControl);
        });

        if (statsTimer != null) {
            statsTimer.cancel();
            statsTimer = null;
        }
        statsTimer = new Timer();
        statsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                getStats();
            }
        }, 0, STATS_INTERVAL_MS);
//try{
//    subscribeMixedStream();
//}catch (Exception e){
//
//}


        Timer t = new Timer();
//Set the schedule function and rate
        t.scheduleAtFixedRate(new TimerTask() {

                                  @Override
                                  public void run() {
                                      subscribeMixedStream();
                                      //Called each time when 1000 milliseconds (1 second) (the period parameter)
                                  }

                              },
//Set how long before to start calling the TimerTask (in milliseconds)
                0,
//Set the amount of time between each execution (in milliseconds)
                1000);

    }

    private void subscribeMixedStream() {
        try {
            executor.execute(() -> {
                for (RemoteStream remoteStream : conferenceClient.info().getRemoteStreams()) {
                    if (remoteStream instanceof RemoteMixedStream
                            && ((RemoteMixedStream) remoteStream).view.equals("common")) {
                        stream2Sub = remoteStream;
                        break;
                    }
                }
                final RemoteStream finalStream2bSub = stream2Sub;
                VideoSubscriptionConstraints videoOption =
                        VideoSubscriptionConstraints.builder()
                                .setResolution(640, 480)
                                .setFrameRate(24)
                                .addCodec(new VideoCodecParameters(H264))
                                .addCodec(new VideoCodecParameters(VP8))
                                .build();

                AudioSubscriptionConstraints audioOption =
                        AudioSubscriptionConstraints.builder()
                                .addCodec(new AudioCodecParameters(OPUS))
                                .addCodec(new AudioCodecParameters(PCMU))
                                .build();

                SubscribeOptions options = SubscribeOptions.builder(true, true)
                        .setAudioOption(audioOption)
                        .setVideoOption(videoOption)
                        .build();

                conferenceClient.subscribe(stream2Sub, options,
                        new ActionCallback<Subscription>() {
                            @Override
                            public void onSuccess(Subscription result) {
                                MainActivity.this.subscription = result;
                                finalStream2bSub.attach(remoteRenderer);
                            }

                            @Override
                            public void onFailure(IcsError error) {
                                Log.e(TAG, "Failed to subscribe "
                                        + error.errorMessage);
                            }
                        });
            });
        }catch (Exception e){

        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        screenCapturer = new IcsScreenCapturer(data, 1280, 720);
        screenStream = new LocalStream((VideoCapturer) screenCapturer);
        executor.execute(
                () -> conferenceClient.publish(screenStream, new ActionCallback<Publication>() {
                    @Override
                    public void onSuccess(Publication result) {
                        runOnUiThread(() -> {
                            middleBtn.setEnabled(true);
                            middleBtn.setTextColor(Color.WHITE);
                            middleBtn.setText(R.string.stop_screen);
                        });
                        screenPublication = result;
                    }

                    @Override
                    public void onFailure(IcsError error) {
                        runOnUiThread(() -> {
                            middleBtn.setEnabled(true);
                            middleBtn.setTextColor(Color.WHITE);
                            middleBtn.setText(R.string.share_screen);
                        });
                        screenCapturer.stopCapture();
                        screenCapturer.dispose();
                        screenCapturer = null;
                        screenStream.dispose();
                        screenStream = null;
                    }
                }));
    }

    private void getStats() {
        if (publication != null) {
            publication.getStats(new ActionCallback<RTCStatsReport>() {
                @Override
                public void onSuccess(RTCStatsReport result) {
                    videoFragment.updateStats(result, true);
                }

                @Override
                public void onFailure(IcsError error) {

                }
            });
        }
        if (subscription != null) {
            subscription.getStats(new ActionCallback<RTCStatsReport>() {
                @Override
                public void onSuccess(RTCStatsReport result) {
                    videoFragment.updateStats(result, false);
                }

                @Override
                public void onFailure(IcsError error) {

                }
            });
        }
    }

    private void switchFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
        if (fragment instanceof VideoFragment) {
            middleBtn.setVisibility(View.VISIBLE);
            rightBtn.setVisibility(View.VISIBLE);
        } else {
            middleBtn.setVisibility(View.GONE);
            rightBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRenderer(SurfaceViewRenderer localRenderer, SurfaceViewRenderer remoteRenderer) {
        this.localRenderer = localRenderer;
        this.remoteRenderer = remoteRenderer;
    }

    @Override
    public void onStreamAdded(RemoteStream remoteStream) {

    }

    @Override
    public void onParticipantJoined(Participant participant) {

    }

    @Override
    public void onMessageReceived(String message, String from, String to) {

    }

    @Override
    public void onServerDisconnected() {
        runOnUiThread(() -> {
            switchFragment(settingsFragment);
            fabright.setVisibility(View.INVISIBLE);
            leftBtn.setEnabled(true);
            leftBtn.setTextColor(Color.WHITE);
            leftBtn.setText(R.string.connect);
            leftBtn.setOnClickListener(joinRoom);
            rightBtn.setEnabled(true);
            rightBtn.setTextColor(Color.WHITE);
            rightBtn.setText(R.string.settings);
            rightBtn.setVisibility(View.GONE);
            //rightBtn.setOnClickListener(settings);

            fragmentContainer.setOnClickListener(null);
        });

        if (statsTimer != null) {
            statsTimer.cancel();
            statsTimer = null;
        }

        if (capturer != null) {
            capturer.stopCapture();
            capturer.dispose();
            capturer = null;
        }

        if (localStream != null) {
            localStream.dispose();
            localStream = null;
        }

        publication = null;
        subscription = null;
        stream2Sub = null;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
                getMenuInflater().inflate(R.menu.activity_menu_drawer, menu);
       // Menu menu =navigationView.getMenu();
        // action_icon = menu.findItem(R.id.switchId);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_close) {
            Toast.makeText(getApplicationContext(), "menu select", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.menu_option) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.fabright:
                animateFAB();

                    fab0.setVisibility(View.VISIBLE);
                    fab2.setVisibility(View.VISIBLE);
                    fab1.setVisibility(View.GONE);
                    fab3.setVisibility(View.GONE);
               //     isChecked=false;

                break;
            case R.id.fab0:
                Toast.makeText(this, "fab0", Toast.LENGTH_SHORT).show();
               if(fab0.getVisibility()==View.VISIBLE){
                   fab0.setVisibility(View.GONE);
                   fab1.setVisibility(View.VISIBLE);
               }
                break;
            case R.id.fab1:
                Toast.makeText(this, "fab1", Toast.LENGTH_SHORT).show();
                if(fab1.getVisibility()==View.VISIBLE){
                    fab1.setVisibility(View.GONE);
                    fab0.setVisibility(View.VISIBLE);

                }
                break;
            case R.id.fab2:
                Toast.makeText(this, "fab2", Toast.LENGTH_SHORT).show();
                if(fab2.getVisibility()==View.VISIBLE){
                    fab2.setVisibility(View.GONE);
                    fab3.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.fab3:
                Toast.makeText(this, "fab3", Toast.LENGTH_SHORT).show();
                if(fab3.getVisibility()==View.VISIBLE){
                    fab3.setVisibility(View.GONE);
                    fab2.setVisibility(View.VISIBLE);
                }
                break;

        }
    }

    public void animateFAB(){

        if(isFabOpen){
            fabright.startAnimation(rotate_backward);
            fab0.startAnimation(fab_close);
            fab1.startAnimation(fab_close);
            fab2.startAnimation(fab_close);
            fab3.startAnimation(fab_close);

            fab0.setClickable(false);
            fab1.setClickable(false);
            fab2.setClickable(false);
            fab3.setClickable(false);

            isFabOpen = false;
            Log.d("Rekha", "close");

        } else {

            fabright.startAnimation(rotate_forward);
            fab0.setVisibility(View.VISIBLE);
            fab1.setVisibility(View.VISIBLE);
            fab2.setVisibility(View.VISIBLE);
            fab3.setVisibility(View.VISIBLE);


            fab0.startAnimation(fab_open);
            fab1.startAnimation(fab_open);
            fab2.startAnimation(fab_open);
            fab3.startAnimation(fab_open);


            fab0.setClickable(true);
            fab1.setClickable(true);
            fab2.setClickable(true);
            fab3.setClickable(true);

            isFabOpen = true;
            Log.d("Rekha","open");

        }
    }

}
