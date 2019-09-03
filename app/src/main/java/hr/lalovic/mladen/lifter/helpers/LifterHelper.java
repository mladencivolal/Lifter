package hr.lalovic.mladen.lifter.helpers;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hr.lalovic.mladen.lifter.Constants.Constants;
import hr.lalovic.mladen.lifter.MainActivity;

public class LifterHelper {

    // converts milisecs to min:sec format
    public static String formatSongTimeUnits(long milis) {
        return String.format(
                Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(milis),
                TimeUnit.MILLISECONDS.toSeconds(milis) - TimeUnit.MINUTES.toSeconds(
                        TimeUnit.MILLISECONDS.toMinutes(
                                milis
                        )
                )
        );
    }

    // get album art Uri that will be passed to ImageLoader
    public static Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
    }

    // share audio
    public static void shareMusic(long id, Context context) {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Uri trackUri = Uri.parse(uri.toString() + "/" + id);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, trackUri);
            intent.setType("audio/*");
            context.startActivity(Intent.createChooser(intent, ("Share")));
    }

    // create Lifter dir for saving lyrics
    public static String createAppDir(String direName) {
        File file = new File(Environment.getExternalStorageDirectory() + "/" + "Lifter", direName);
        if (!file.exists()) {
            file.mkdirs();
        }
        return null;
    }

    public static String stringFilter(String str) {
        if (str == null) {
            return null;
        }
        // compile the given regular expression passed as the string
        Pattern lineMatcher = Pattern.compile("\\n[\\\\/:*?\\\"<>|]((\\[\\d\\d:\\d\\d\\.\\d\\d\\])+)(.+)");
        // match string argument against regular expression
        Matcher m = lineMatcher.matcher(str);
        return m.replaceAll("").trim();
    }

    // remove special characters from given String. Replace whitespaces with given replaceWord.
    @NonNull
    private static String queryLyrics(String all, String replaceWord) {
        return all
                .trim()
                .replaceAll("[\\\\/:*?\"<>|]", "")
                //  .replaceAll("[^A-Za-z0-9\\[\\]]", replaceWord)
                .replaceAll("\\s+", replaceWord)
                .toLowerCase();
    }

    // set lyrics to given view
    private static void setLyrics(Context context, String songName, String path, String lyrics, TextView lrcView) {
        if (lrcView == null) {
            return;
        }
        String savePath = LyricsHelper.saveLyrics(songName);
        File file = new File(savePath);
        Log.e("Path", savePath);
        if (MainActivity.saveLyrics()) {
            if (!file.exists()) {
                LyricsHelper.writeLyrics(savePath, lyrics);
            }
        }
        try {
            LyricsHelper.insertLyrics(path, lyrics);
        }catch (Exception e) {
            e.printStackTrace();
        }
        lrcView.setText(lyrics);
    }

    // return location path of lyrics save directory
    public static String getDirLocation() {
        return Environment.getExternalStorageDirectory() + "/Lifter/" + "Lyrics/";
    }

    // returns non empty string
    static String setFileName(String title) {
        if (TextUtils.isEmpty(title)) {
            title = "unknown";
        }
        return title;
    }

    // uses given parameters to parse lyrics from indicine webpage.
    public static void indicineLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.indicineUrl + "movies/lyrics/" + queryLyrics(songName, "-") + "-lyrics-" + queryLyrics(album, "-") + "/";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div class=\"lyrics\">";
                            String scrapEnd = "<a title=\"Hindi Lyrics\" href=\"http://www.indicine.com/hindi-lyrics/\"";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<div class=\"lyrics\">", "")
                                            .replace("<h2>", "")
                                            .replace("</h2>", "")
                                            .replace("<h1>", "")
                                            .replace("</h1>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replaceAll("<a.*?</a>", "")
                                            .replace(queryLyrics(songName, " "), "")
                                            .replace("<br />", "")
                                            .replace("<p>", "")
                                            .replace("</p>", "\n")
                                            .replace("&#8217;", "'")
                                            .replace("&#8230", "")
                                            .replace("</div>", "")
                                            .replace("</div>", "\n");
                                    StringBuffer buffer = new StringBuffer(fin);
                                    String start = "<div style=\"float: right;\">";
                                    String end = "</iframe>";
                                    if (fin.contains(start) && fin.contains(end)) {
                                        int removeStart = fin.indexOf(start);
                                        int removeEnd = fin.indexOf(end);
                                        buffer = buffer.replace(removeStart, removeEnd, "");
                                        String filter = buffer.toString();
                                        filter = filter.replace(end, "");
                                        // set lyrics
                                        setLyrics(context, songName, path, filter, lyrics);
                                        Log.e("LifterHelper", "lyrics from Indicine");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        // if error, pass the parameters to lyricsedLyrics
                        lyricsedLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void lyricsedLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.lyricsedUrl + queryLyrics(songName, "-") + "-song-lyrics-" + queryLyrics(album, "-") + "/";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div class=\"entry\">";
                            String scrapEnd = "<span style=\"display:none\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance

                                    fin = fin
                                            .trim()
                                            .replace("<div class=\"entry\">", "")
                                            .replace("<h2>", "")
                                            .replace("</h2>", "")
                                            .replace("<br />", "")
                                            .replace("<p>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replace("</p>", "\n")
                                            .replace("&#8217;", "'")
                                            .replace("&#8230;", "...")
                                            .replace("<em>", " ")
                                            .replace("</em>", " ")
                                            .replaceAll("<a.*?</a>", "")
                                            .replaceAll("<!--.*?-->", "");
                                    StringBuffer buffer = new StringBuffer(fin);
                                    String start = "<strong>";
                                    String end = "</span>";
                                    if (fin.contains(start) && fin.contains(end)) {
                                        int removeStart = fin.indexOf(start);
                                        int removeEnd = fin.indexOf(end);
                                        buffer = buffer.replace(removeStart, removeEnd, "");
                                        String filter = buffer.toString();
                                        filter = filter
                                                .replace("</div>", "\n")
                                                .replace(end, "");
                                        // set lyrics
                                        setLyrics(context, songName, path, filter, lyrics);
                                        Log.e("NetworkHelper", "lyrics from Lyricssed");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        songsLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void songsLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.songlyricsUrl + queryLyrics(artistName, "-") + "/" + queryLyrics(songName, "-") + "-lyrics" + "/";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div id=\"songLyricsDiv-outer\">";
                            String scrapEnd = "<div itemscope itemtype=\"http://schema.org/MusicRecording\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<div id=\"songLyricsDiv-outer\">", "")
                                            .replaceAll("<p id=\"songLyricsDiv\"  class=\"songLyricsV14 iComment-text\">", "")
                                            .replace("<br />", "")
                                            .replaceAll("<a.*?</a>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replace("<br>", "")
                                            .replace("</div>", "")
                                            .replace("</p>", "");
                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from SongLyrics");
                                }
                            }
                        }
                    }
                    @Override
                    public void onError(ANError anError) {
                        lyricsbogieLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void lyricsbogieLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.lyricsbogieUrl + queryLyrics(album, "-") + "/" + queryLyrics(songName, "-") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div id=\"lyricsDiv\" class=\"left\">";
                            String scrapEnd = "<div class=\"right\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<div id=\"lyricsDiv\" class=\"left\">", "")
                                            .replace("<p id=\"view_lyrics\">", "")
                                            .replace("</blockquote>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replace("<br/>", "\n")
                                            .replace("<blockquote>", "")
                                            .replace("<p>", "")
                                            .replace("</div>", "")
                                            .replace("</p>", "\n\n");

                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from LyricsBoogie");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        atozLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void atozLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.atozUrl + queryLyrics(artistName, "") + "/" + queryLyrics(songName, "") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div>";
                            String scrapEnd = "<div class=\"noprint\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replaceAll("<!--.*?-->", "")
                                            .replace("<div>", "")
                                            .replace("<b>", "")
                                            .replace("</b>", "")
                                            .replace("<br>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replaceAll("<a.*?</a>", "")
                                            .replace("</div>", "");
                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from AtoZ");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        lyricsondemandLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void lyricsondemandLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        char firstLetter = artistName.charAt(0);
        String gotFirstChar = String.valueOf(firstLetter)
                .toLowerCase();
        String extraStuff = "lyrics";
        String url = Constants.lyricsondemandUrl + gotFirstChar + "/" + queryLyrics(artistName, "") + extraStuff + "/" + queryLyrics(songName, "") + extraStuff + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div class=\"lcontent\" >";
                            String scrapEnd = "<div id=\"lfcredits\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<div class=\"lcontent\" >", "")
                                            .replace("</div>", "")
                                            .replace("<br />", "\n")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replaceAll("<!--.*?-->", "");
                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from LyricsOnDemand");
                                }
                            }
                        }
                    }
                    @Override
                    public void onError(ANError anError) {
                        absolutesLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void absolutesLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.absolutelyricsUrl + queryLyrics(artistName, "_") + "/" + queryLyrics(songName, "_");
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<p id=\"view_lyrics\">";
                            String scrapEnd = "<div id=\"view_lyricsinfo\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<p id=\"view_lyrics\">", "")
                                            .replace("</p>", "")
                                            .replace("<br />", "\n")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replace("<script language=JavaScript>", "")
                                            .replace("</script>", "")
                                            .replaceAll("document.write(.*)", "")
                                            .replaceAll("<!--.*?-->", "");

                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from Absolute");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        vagLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void vagLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.vagUrl + queryLyrics(artistName, "-") + "/" + queryLyrics(songName, "-") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div itemprop=description>";
                            String scrapEnd = "<div id=lyrFoot>";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                int start = response.indexOf(scrapStart);
                                int end = response.indexOf(scrapEnd);
                                if (start >= 0 && end >= 0) {
                                    String fin = TextUtils.substring(response, start, end);
                                    if (fin.length() > 0) {
                                        // other unwanted stuff clearance
                                        fin = fin
                                                .trim()
                                                .replace("<div id=lyrFoot>", "")
                                                .replace("<div itemprop=description>", "")
                                                .replace("<hr>", "")
                                                .replace("<i>", "")
                                                .replace("</i>", "")
                                                .replaceAll("<a.*?</a>", "")
                                                .replace("<br/>", "\n")
                                                .replace("</div>", "");
                                        // set lyrics
                                        setLyrics(context, songName, path, fin, lyrics);
                                        Log.e("LifterHelper", "lyrics from Vag");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        metroLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void metroLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.metroUrl + queryLyrics(songName, "-") + "-lyrics-" + queryLyrics(artistName, "-") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div id=\"lyrics-body-text\" class=\"js-lyric-text\">";
                            String scrapEnd = "<p class=\"writers\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                int start = response.indexOf(scrapStart);
                                int end = response.indexOf(scrapEnd);
                                if (start >= 0 && end >= 0) {
                                    String fin = TextUtils.substring(response, start, end);
                                    if (fin.length() > 0) {
                                        // other unwanted stuff clearance
                                        fin = fin
                                                .trim()
                                                .replace("<div id=\"lyrics-body-text\" class=\"js-lyric-text\">", "")
                                                .replace("<p class='verse'>", "\n")
                                                .replace("<br>", "")
                                                .replace("<i>", "")
                                                .replace("</i>", "")
                                                .replaceAll("<a.*?</a>", "")
                                                .replace("</p>", "\n")
                                                .replace("</div>", "");
                                        // set lyrics
                                        setLyrics(context, songName, path, fin, lyrics);
                                        Log.e("LifterHelper", "lyrics from Metro");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        directLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void directLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.directUrl + queryLyrics(artistName, "-") + queryLyrics(songName, "-") + "-lyrics.html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<div class=\"lyrics lyricsselect\">";
                            String scrapEnd = "<ul class=\"menu\">";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin.trim()
                                            .replace(scrapStart, "")
                                            .replace("<br>", "")
                                            .replace("</p>", "\n")
                                            .replace("<p>", "")
                                            .replace("<hr>", "")
                                            .replace("<i>", "")
                                            .replace("</i>", "")
                                            .replaceAll("<a.*?</a>", "")
                                            .replace("<div class=\"ad\">", "")
                                            .replace("</div>", "")
                                            .replace("<ins id=\"lyrics-inline-300x250\">", "")
                                            .replace("</ins>", "");
                                    StringBuffer buffer = new StringBuffer(fin);
                                    String start = "<script>";
                                    String end = "</script>";
                                    if (fin.contains(start) && fin.contains(end)) {
                                        int removeStart = fin.indexOf(start);
                                        int removeEnd = fin.indexOf(end);
                                        buffer = buffer.replace(removeStart, removeEnd, "");
                                        String filter = buffer.toString();
                                        filter = filter.replace(end, "");
                                        // set lyrics
                                        setLyrics(context, songName, path, filter, lyrics);
                                        Log.e("LifterHelper", "lyrics from Direct");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        HindiGeetLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void HindiGeetLyrics(final Context context, final String artistName, final String songName, final String album, final String path, final TextView lyrics) {
        String url = Constants.hindigeetUrl + "song/" + queryLyrics(songName, "_") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<pre>";
                            String scrapEnd = "</pre>";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<pre>", "")
                                            .replace("</pre>", "");
                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from HindiGeet");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        tekstoviLyrics(context, artistName, songName, album, path, lyrics);
                    }
                });
    }

    public static void tekstoviLyrics(final Context context, String artistName, final String songName, String album, final String path, final TextView lyrics) {
        String url = Constants.tekstoviUrl + "song/" + queryLyrics(songName, "_") + ".html";
        AndroidNetworking.get(url)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        if (!response.isEmpty()) {
                            String scrapStart = "<pre>";
                            String scrapEnd = "</pre>";
                            if (response.contains(scrapStart) && response.contains(scrapEnd)) {
                                String fin = TextUtils.substring(response, response.indexOf(scrapStart), response.indexOf(scrapEnd));
                                if (fin.length() > 0) {
                                    // other unwanted stuff clearance
                                    fin = fin
                                            .trim()
                                            .replace("<pre>", "")
                                            .replace("</pre>", "");
                                    // set lyrics
                                    setLyrics(context, songName, path, fin, lyrics);
                                    Log.e("LifterHelper", "lyrics from tekstovipesama");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        lyrics.setText(LyricsHelper.getInbuiltLyrics(path));
                    }
                });
    }
}
