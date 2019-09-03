package hr.lalovic.mladen.lifter.helpers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.TextView;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagOptionSingleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import hr.lalovic.mladen.lifter.MainActivity;

import static hr.lalovic.mladen.lifter.helpers.LifterHelper.stringFilter;

public class LyricsHelper {

    // load lyrics from lyrics save directory or else download from provided urls
    public static void LoadLyrics(Context context, String title, String artist, String album, String path, TextView lrcView) {

        if (title == null || artist == null || album == null || path == null) {
            return;
        }
        File file = new File(saveLyrics(title));
        if (file.exists()) {
            if (file.getName().equals(title)) {
                readLyricsFromFile(file, lrcView);
            }
        } else {
            try {
                if (MainActivity.saveData()) {
                    LifterHelper.indicineLyrics(context, artist, title, album, path, lrcView);
                } else {
                    Log.d("LyricsHelper", "not allowed network");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lrcView.setText(getInbuiltLyrics(path));
            }
        }
    }

    // read lyrics from lyrics file
    public static void readLyricsFromFile(File file, TextView textView) {
        if (file != null) {
            FileInputStream iStr;
            try {
                iStr = new FileInputStream(file);
                BufferedReader fileReader = new BufferedReader(new InputStreamReader(iStr));
                String TextLine = "";
                String TextBuffer = "";
                try {
                    while ((TextLine = fileReader.readLine()) != null) {
                        TextBuffer += TextLine + "\n";
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                textView.setText(TextBuffer);
                try {
                    fileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            Log.d("Helper", "error");
        }
    }

    // inserts lyrics into audio file
    public static boolean insertLyrics(String path, String lyrics) {
        File f = new File(path);
        if (f.exists()) {
            try {
                AudioFile audioFile = AudioFileIO.read(f);
                if (audioFile == null) {
                    return false;
                }
                TagOptionSingleton.getInstance().setAndroid(true);
                org.jaudiotagger.tag.Tag tag = audioFile.getTag();
                if (tag == null) {
                    return false;
                }
                tag.deleteField(FieldKey.LYRICS);
                tag.setField(FieldKey.LYRICS, lyrics);
                audioFile.setTag(tag);
                AudioFileIO.write(audioFile);
                return true;
            } catch (CannotReadException | CannotWriteException | InvalidAudioFrameException | TagException | IOException | ReadOnlyFileException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    // save lyrics to specified local path
    public static void writeLyrics(String path, String content) {
        try {
            if (!content.isEmpty() && !path.isEmpty() && content.length() > 0 && path.length() > 0) {
                FileWriter writer = new FileWriter(path);
                writer.flush();
                writer.write(stringFilter(content));
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // get built-in preloaded lyrics, if available
    @NonNull
    public static String getInbuiltLyrics(String path) {
        String lyrics;
        if (path != null) {
            File file = new File(path);
            AudioFile audioFile = null;
            org.jaudiotagger.tag.Tag tag = null;
            if (file.exists()) {
                try {
                    audioFile = AudioFileIO.read(file);
                    if (audioFile != null) {
                        tag = audioFile.getTag();
                        if (tag != null) {
                            lyrics = tag.getFirst(FieldKey.LYRICS);
                            //lyrics = lyrics.replaceAll("\n", "</br>");
                            return lyrics;
                        } else {
                            return "No Lyrics found";
                        }
                    }
                } catch (CannotReadException | ReadOnlyFileException | InvalidAudioFrameException | TagException | IOException e) {
                    e.printStackTrace();
                }
            } else {
                Log.e("LyricsHelper", "file not exists");
            }
        }
        return "No Lyrics found";
    }

    // return save lyrics dir location
    public static String saveLyrics(String name) {
        return LifterHelper.getDirLocation() + LifterHelper.setFileName(name);
    }
}
