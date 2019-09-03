package hr.lalovic.mladen.lifter;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hr.lalovic.mladen.lifter.adapter.SongsListAdapter;
import hr.lalovic.mladen.lifter.helpers.LifterHelper;
import hr.lalovic.mladen.lifter.helpers.LyricsHelper;
import hr.lalovic.mladen.lifter.storage.StorageUtilities;

import static android.os.Build.VERSION.SDK_INT;
import static hr.lalovic.mladen.lifter.Constants.Constants.SAVE_DATA;
import static hr.lalovic.mladen.lifter.Constants.Constants.SaveLyrics;
import static hr.lalovic.mladen.lifter.MusicService.NO_REPEAT;
import static hr.lalovic.mladen.lifter.MusicService.REPEAT_CURRENT;
import static hr.lalovic.mladen.lifter.MusicService.SHUFFLE_OFF;
import static hr.lalovic.mladen.lifter.MusicService.SHUFFLE_ON;
import static hr.lalovic.mladen.lifter.MusicService.activeAudio;
import static hr.lalovic.mladen.lifter.MusicService.getSongId;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener {
    public static final int IMAGES_NUM = 23;
    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    public static final String Broadcast_PLAY_NEW_AUDIO = "hr.lalovic.mladen.lifter.PlayNewAudio";

    private MusicService player;
    private boolean serviceBound = false;
    private ArrayList<Song> audioList;

    private LinearLayout playerInfo;
    private SongsListAdapter adapter;
    private static SharedPreferences mPreferences;
    private android.support.v7.widget.LinearLayoutManager mLayoutManager;
    private AppBarLayout appBarLayout;

    private ImageView collapsingImageView;
    private ImageView repeatButton;
    private ImageView shuffleButton;
    private ImageView skipPrevButton;
    private ImageView skipNextButton;
    private ImageView playPauseButton;
    private TextView playingAlbum;
    private TextView playingSong;
    private TextView songDurationText;
    private TextView songPositionText;
    private SeekBar seekBar;
    private TextView lrcView;
    private RecyclerView recyclerView;
    private FloatingActionButton collapsingImageChangeButton;
    private FloatingActionButton shareButton;
    private FloatingActionButton lyricsButton;
    private FloatingActionButton jumpToTopButton;

    private int imageIndex = 0;
    private int songIndex;
    private boolean paused = false;
    private int imageSwitchInterval;
    private boolean autoImageUpdateEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle("");
        getSupportActionBar().setIcon(R.drawable.lifter_logo);

        createDirectory();

        initUiElements();
        initOnClickListeners();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        loadCollapsingImage(imageIndex);

        // handler to switch images in desired interval.
        imageSwitchHandler();

        //Instantiate ImageLoader for albumart display
        ImageLoader.getInstance().init(ImageLoaderConfiguration.createDefault(getApplicationContext()));

        // checks permissions, requests new permissions if needed, loads all available local audio files and sends them to recyclerview
        if (checkAndRequestPermissions()) {
            loadAudioList();
        }
    }

    // clicklistener for recyclerview
    private View.OnClickListener onItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // This viewHolder will have all required values.
            int position;
            RecyclerView.ViewHolder viewHolder = (RecyclerView.ViewHolder) view.getTag();
            position = viewHolder.getAdapterPosition();

            playAudio(position, audioList);

            songIndex = position;
            paused = false;

            playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
            playingSong.setText(audioList.get(position).getTitle());
            playingAlbum.setText(audioList.get(position).getArtist());

            // handler to update recyclerview itemholder
            updateItemHolderHandler();
        }
    };

    public static SharedPreferences getmPreferences() {
        return mPreferences;
    }

    public void onButtonShowPopupLyricsClick(View view) {
        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.lyricsview, null);
        final View lyricsView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.lyricsview, new LinearLayout(getApplicationContext()), false);
        lrcView = lyricsView.findViewById(R.id.lyrics1);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(lyricsView, width, height, focusable);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        LyricsHelper.LoadLyrics(getApplicationContext(), player.getSong(), player.getArtist(), player.getAlbum(), player.getData(), lrcView);

        // dismiss the popup window when touched
        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                popupWindow.dismiss();
                return true;
            }
        });
    }

    public static boolean saveData() {
        return getmPreferences().getBoolean(SAVE_DATA, true);
    }

    public static boolean saveLyrics() {
        return getmPreferences().getBoolean(SaveLyrics, true);
    }

    private void loadAudioList() {
        loadAudio();
        initRecyclerView();
    }

    private boolean checkAndRequestPermissions() {
        if (SDK_INT >= Build.VERSION_CODES.M) {
            int permissionReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
            int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            int permissionWriteStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            List<String> listPermissionsNeeded = new ArrayList<>();

            if (permissionReadPhoneState != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (permissionWriteStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        String TAG = "LOG_PERMISSION";
        Log.d(TAG, "Permission callback called-------");
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                // initialize the map with both permissions
                perms.put(Manifest.permission.READ_PHONE_STATE, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // check for 3 permissions
                    if (perms.get(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, getResources().getString(R.string.phone_state_storage_permissions_required));
                        // process the normal flow
                        // else any one or both the permissions are not granted
                        loadAudioList();
                    } else {
                        Log.d(TAG, getResources().getString(R.string.some_perms_not_granted));
                        // permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
//                      // shouldShowRequestPermissionRationale will return true
                        //show the dialog saying its necessary and try again otherwise proceed with setup.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                            showDialogOK(getResources().getString(R.string.app_requests_perms_text),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which) {
                                                case DialogInterface.BUTTON_POSITIVE:
                                                    checkAndRequestPermissions();
                                                    break;
                                                case DialogInterface.BUTTON_NEGATIVE:
                                                    // proceed with logic by disabling the related features or quit the app.
                                                    break;
                                            }
                                        }
                                    });
                        }
                        // permission is denied (and never ask again is  checked)
                        // shouldShowRequestPermissionRationale will return false
                        else {
                            Toast.makeText(this, R.string.you_must_enable_permissions, Toast.LENGTH_LONG)
                                    .show();
                            // proceed with logic by disabling the related features or quit the app.
                        }
                    }
                }
            }
        }
    }

    private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("CANCEL", okListener)
                .create()
                .show();
    }

    private void initRecyclerView() {
        if (audioList != null && audioList.size() > 0) {

            recyclerView = findViewById(R.id.recyclerview);

            adapter = new SongsListAdapter(getApplicationContext(), audioList);
            recyclerView.setAdapter(adapter);
            adapter.setOnItemClickListener(onItemClickListener);

            mLayoutManager = new LinearLayoutManager(this);
            recyclerView.setLayoutManager(mLayoutManager);

            // scrolls to currently playing item
            playerInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (player != null) {
                        appBarLayout.setExpanded(false);
                        recyclerView.scrollToPosition(songIndex);
                        if (songIndex == 0) {
                            mLayoutManager.scrollToPositionWithOffset(songIndex, 0);
                        } else {
                            mLayoutManager.scrollToPositionWithOffset(songIndex - 1, 0);
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void loadCollapsingImage(int i) {
        // loads image from image array
        TypedArray array = getResources().obtainTypedArray(R.array.images);
        collapsingImageView.setImageDrawable(array.getDrawable(i));
        // takes auto image change interval from sharedprefs
        if (mPreferences.contains("interval")) {
            imageSwitchInterval = Integer.parseInt(mPreferences.getString("interval", null));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // inflate the menu
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(MainActivity.this,
                        SettingsActivity.class));
                return true;
            case R.id.action_exit:
                finishAndRemoveTask();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    // binding this client to the player service
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;

            updateSeekBarHandler();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;

        }
    };

    private void playAudio(int audioIndex, ArrayList<Song> audioList) {
        // check is service is active
        if (!serviceBound) {
            // store serializable audioList to SharedPreferences
            StorageUtilities storage = new StorageUtilities(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);
            Intent playerIntent = new Intent(this, MusicService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);

            songIndex = audioIndex;
            // sets song title and album title for currently playing audio
            playingSong.setText(audioList.get(songIndex).getTitle());
            playingAlbum.setText(audioList.get(songIndex).getArtist());
            // changes play/pause button according to playing status
            if (playPauseButton.getDrawable().equals(R.drawable.ic_pause_black_24dp)) {
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                paused = false;
            } else {
                playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
                paused = true;
            }
        } else {
            // store the new audioIndex to SharedPreferences
            StorageUtilities storage = new StorageUtilities(getApplicationContext());
            storage.storeAudioIndex(audioIndex);
            // service is active
            // send broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_black_24dp);
        }
    }

    private void loadAudio() {
        // load all local audio files into arraylist
        ContentResolver contentResolver = getContentResolver();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);

        if (cursor != null && cursor.getCount() > 0) {
            audioList = new ArrayList<>();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                int albumId = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                // Save to audioList
                audioList.add(new Song(id, albumId, data, title, album, artist, duration));
            }
        }
        if (cursor != null)
            cursor.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            player.stopSelf();
        }
    }

    public void playNext() {
        // play next song, load audio info, update audio index.
        player.skipToNext();
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
        paused = false;
        if (player.getShuffleMode() == SHUFFLE_ON) {
            if (audioList.size() <= 1) {
                songIndex = 0;
            } else {
                songIndex = MusicService.getAudioIndex();
            }
        } else if (player.getRepeatMode() == REPEAT_CURRENT) {
            // if last in playlist, set index to 0
        } else if (songIndex < audioList.size() - 1) {
            songIndex++;
        } else {
            songIndex = 0;
        }
        //scroll to currently playing item
        //appBarLayout.setExpanded(false);
        //recyclerView.smoothScrollToPosition(songIndex);
        //mLayoutManager.scrollToPositionWithOffset(songIndex - 1, 0);

        recyclerView.getAdapter().notifyDataSetChanged();
        playingSong.setText(audioList.get(songIndex).getTitle());
        playingAlbum.setText(audioList.get(songIndex).getArtist());
    }

    public void playPrevious() {
        // play previous song, load audio info, update audio index.
        player.skipToPrevious();
        if (player.getRepeatMode() == REPEAT_CURRENT) {
        } else if (songIndex > 0) {
            songIndex--;
        } else {
            songIndex = audioList.size() - 1;
        }
        //scroll to currently playing item
        //appBarLayout.setExpanded(false);
        //recyclerView.smoothScrollToPosition(songIndex);
        //mLayoutManager.scrollToPositionWithOffset(songIndex - 1, 0);
        recyclerView.getAdapter().notifyDataSetChanged();
        playingSong.setText(audioList.get(songIndex).getTitle());
        playingAlbum.setText(audioList.get(songIndex).getArtist());
    }

    private void initUiElements() {
        playerInfo = findViewById(R.id.player_info);
        appBarLayout = findViewById(R.id.app_bar);
        collapsingImageView = findViewById(R.id.collapsingImageView);
        repeatButton = findViewById(R.id.repeat_button);
        shuffleButton = findViewById(R.id.shuffle_button);
        skipPrevButton = findViewById(R.id.skip_prev_button);
        skipNextButton = findViewById(R.id.skip_next_button);
        playPauseButton = findViewById(R.id.play_pause_button);
        seekBar = findViewById(R.id.seekTo);
        playingAlbum = findViewById(R.id.playing_album);
        playingSong = findViewById(R.id.playing_song);
        playingSong.setSelected(true);
        songDurationText = findViewById(R.id.duration);
        songPositionText = findViewById(R.id.song_position);
        collapsingImageChangeButton = findViewById(R.id.collapsingImageChangeButton);
        shareButton = findViewById(R.id.share_button);
        lyricsButton = findViewById(R.id.lyrics_button);
        jumpToTopButton = findViewById(R.id.top);
    }

    private void initOnClickListeners() {
        skipPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player != null) {
                    playPrevious();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                }
            }
        });
        skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player != null) {
                    playNext();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                }
            }
        });
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player != null) {
                    player.playPause();
                    if (paused) {
                        paused = false;
                        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
                    } else {
                        paused = true;
                        playPauseButton.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                }
            }
        });
        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player.getRepeatMode() == player.NO_REPEAT) {
                    player.setRepeatMode(REPEAT_CURRENT);
                    player.setShuffleMode(SHUFFLE_OFF);
                    repeatButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_repeat_on));
                    shuffleButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shuffle_off));
                } else if (player.getRepeatMode() == REPEAT_CURRENT) {
                    player.setRepeatMode(NO_REPEAT);
                    repeatButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_repeat_off));
                }
            }
        });
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player.getShuffleMode() == SHUFFLE_OFF) {
                    player.setShuffleMode(SHUFFLE_ON);
                    player.setRepeatMode(NO_REPEAT);
                    shuffleButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shuffle_on));
                    repeatButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_repeat_off));
                } else if (player.getShuffleMode() == SHUFFLE_ON) {
                    player.setShuffleMode(SHUFFLE_OFF);
                    shuffleButton.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shuffle_off));
                }
            }
        });
        collapsingImageChangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (imageIndex >= IMAGES_NUM) {
                    imageIndex = 0;
                    loadCollapsingImage(imageIndex);
                } else {
                    loadCollapsingImage(++imageIndex);
                }
            }
        });
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    LifterHelper.shareMusic(getSongId(), getApplicationContext());
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                }
            }
        });
        lyricsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    onButtonShowPopupLyricsClick(recyclerView);
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                }
            }
        });
        jumpToTopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView.scrollToPosition(0);
                adapter.notifyDataSetChanged();
                appBarLayout.setExpanded(true);
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    player.seekToPosition(seekBar.getProgress());
                } else {
                    Toast.makeText(getApplicationContext(), R.string.must_select_song_first, Toast.LENGTH_SHORT).show();
                    seekBar.setProgress(0);
                }
            }
        });
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playNext();
    }

    //Create local dir to save lyrics
    private void createDirectory() {
        LifterHelper.createAppDir("Lyrics");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPreferences.contains("interval")) {
            imageSwitchInterval = Integer.parseInt(mPreferences.getString("interval", null));
        }
        if (mPreferences.contains("switch_preference")) {
            autoImageUpdateEnabled = mPreferences.getBoolean("switch_preference", true);
        }
    }

    private void updateSeekBarHandler() {

        // handler to update seekbar progress bar/text/duration
        final Handler updateSeekBarHandler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (MusicService.mediaPlayer != null) {
                    seekBar.setMax(player.getDuration());
                    seekBar.setProgress(player.getCurrentPosition());
                    songPositionText.setText(LifterHelper.formatSongTimeUnits(player.getCurrentPosition()));
                    songDurationText.setText(LifterHelper.formatSongTimeUnits(player.getDuration()));
                    if (player != null && ((player.getCurrentPosition() >= player.getDuration() - 100) || (seekBar.getProgress() >= seekBar.getMax() - 100))) {
                        playNext();
                        recyclerView.smoothScrollToPosition(songIndex);
                        mLayoutManager.scrollToPositionWithOffset(songIndex - 1, 0);
                    }
                    updateSeekBarHandler.postDelayed(this, 1000);
                }
            }
        };
        updateSeekBarHandler.post(runnable);
    }

    public void updateItemHolderHandler() {
        // handler to update itemholder. Before I added this, visualiser didn't work properly. (iteholder needed to be clicked twice for visualizer to show).
        final Handler updateItemHolderHandler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
                updateItemHolderHandler.postDelayed(this, 50000);
            }
        };
        updateItemHolderHandler.post(runnable);
    }

    public void imageSwitchHandler() {
        // handler to change images in collapsingimagetoolbar. Change interval is taken from sharedprefs.
        final Handler imageSwitchHandler = new Handler();
        if (mPreferences.contains("switch_preference")) {
            autoImageUpdateEnabled = mPreferences.getBoolean("switch_preference", true);
        }
        if (mPreferences.contains("interval")) {
            imageSwitchInterval = Integer.parseInt(mPreferences.getString("interval", null));
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    if (!paused)
                        if (autoImageUpdateEnabled) {
                            if (imageIndex >= IMAGES_NUM) {
                                imageIndex = 0;
                                loadCollapsingImage(imageIndex);
                            } else {
                                loadCollapsingImage(++imageIndex);
                            }
                        }
                }
                imageSwitchHandler.postDelayed(this, imageSwitchInterval * 1000);
            }
        };
        imageSwitchHandler.post(runnable);
    }
}