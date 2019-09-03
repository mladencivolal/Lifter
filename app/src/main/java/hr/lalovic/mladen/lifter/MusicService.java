package hr.lalovic.mladen.lifter;

import java.io.IOException;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.RequiresApi;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import hr.lalovic.mladen.lifter.helpers.LifterHelper;
import hr.lalovic.mladen.lifter.storage.StorageUtilities;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener {

    public static final String ACTION_PLAY = "hr.lalovic.mladen.lifter.ACTION_PLAY";
    public static final String ACTION_PAUSE = "hr.lalovic.mladen.lifter.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "hr.lalovic.mladen.lifter.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "hr.lalovic.mladen.lifter.ACTION_NEXT";
    public static final String ACTION_STOP = "hr.lalovic.mladen.lifter.ACTION_STOP";

    public static MediaPlayer mediaPlayer;

    // used to pause/resume MediaPlayer
    public int resumePosition;

    // binder given to clients
    private final IBinder iBinder = new LocalBinder();

    // list of local audio files
    private static ArrayList<Song> audioList;
    private static int audioIndex = -1;
    public static Song activeAudio; //currently playing audio object

    // repeat flags
    public static final int NO_REPEAT = 1;
    public static final int REPEAT_CURRENT = 2;
    private static int repeatMode = NO_REPEAT;

    // shuffle flags
    public static final int SHUFFLE_OFF = 1;
    public static final int SHUFFLE_ON = 2;
    private static int shuffleMode = SHUFFLE_OFF;

    public static int getRepeatMode() {
        return repeatMode;
    }

    public static void setRepeatMode(int repeatMode) {
        MusicService.repeatMode = repeatMode;
    }

    public static int getShuffleMode() {
        return shuffleMode;
    }

    public static void setShuffleMode(int shuffleMode) {
        MusicService.shuffleMode = shuffleMode;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        // listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
    }

    // system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // load data from SharedPreferences
            StorageUtilities storage = new StorageUtilities(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if (audioIndex != -1 && audioIndex < audioList.size()) {
                // index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }

        initMediaPlayer();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        // clear cached playlist
        new StorageUtilities(getApplicationContext()).clearCachedAudioPlaylist();
    }

    // service binder
    public class LocalBinder extends Binder {
        public MusicService getService() {
            // return this instance of LocalService so clients can call public methods
            return MusicService.this;
        }
    }

    // MediaPlayer callback methods
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // when playback of a media source has ended
        stopMedia();
        // stop the service
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error",  getResources().getString(R.string.media_error_not_valid_progressive_playback) + " " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", getResources().getString(R.string.media_error_server_died) + " " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", getResources().getString(R.string.media_error_unknown) + " " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        // when the media source is ready for playback.
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
    }

    // MediaPlayer actions
    private void initMediaPlayer() {
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance
        // setup MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        // reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // set the data source to the mediaFile location
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    public static void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    public void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    public void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }

    public void playPause() {
        if (mediaPlayer.isPlaying()) {
            pauseMedia();
        } else {
            mediaPlayer.seekTo(resumePosition);
            resumeMedia();
        }
    }

    public void skipToNext() {
        if (getShuffleMode() == SHUFFLE_ON) {
            int newSong = audioIndex;
            // checks if audio list is big enough for shuffle operation (More than 1 item).
            if (!(audioList.size() <= 1)) {
                // generate random index
                Random rand = new Random();
                while (newSong == audioIndex) {
                    newSong = rand.nextInt(audioList.size());
                }
            }
            audioIndex = newSong;
            activeAudio = audioList.get(audioIndex);
        } else if (getRepeatMode() == REPEAT_CURRENT) {
            activeAudio = audioList.get(audioIndex);
        } else if (audioIndex == audioList.size() - 1) {
            // if last in playlist
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            // get next in playlist
            activeAudio = audioList.get(++audioIndex);
        }
        // update stored index
        new StorageUtilities(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        // reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    public void skipToPrevious() {
        if (getRepeatMode() == REPEAT_CURRENT) {
            activeAudio = audioList.get(audioIndex);
        } else if (audioIndex == 0) {
            // if first in playlist
            // set index to the last of audioList
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
            // get previous in playlist
            activeAudio = audioList.get(--audioIndex);
        }
        // update stored index
        new StorageUtilities(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        // reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    public void seekToPosition(int progress) {
        mediaPlayer.seekTo(progress);
    }

    // ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // get the new media index from SharedPreferences
            audioIndex = new StorageUtilities(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
            // PLAY_NEW_AUDIO action received
            // reset mediaPlayer to play the new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
        }
    };

    private void register_playNewAudio() {
        // register playNewAudio receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    public int getDuration() {
        int duration = mediaPlayer.getDuration();
        return duration;
    }

    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public String getAlbum() {
        return activeAudio.getAlbum();
    }

    public String getSong() {
        return activeAudio.getTitle();
    }

    public String getArtist() {
        return activeAudio.getArtist();
    }

    public String getData() {
        return activeAudio.getData();
    }

    public static final long getSongId() {
        return activeAudio.getId();
    }

    public static int getAudioIndex() {
        return audioIndex;
    }
}
