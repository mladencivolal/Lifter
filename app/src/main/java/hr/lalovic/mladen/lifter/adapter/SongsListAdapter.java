package hr.lalovic.mladen.lifter.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import hr.lalovic.mladen.lifter.MusicService;
import hr.lalovic.mladen.lifter.R;
import hr.lalovic.mladen.lifter.Song;
import hr.lalovic.mladen.lifter.helpers.LifterHelper;
import hr.lalovic.mladen.lifter.widgets.MusicVisualizer;

public class SongsListAdapter extends RecyclerView.Adapter<SongsListAdapter.ItemHolder> {
    private List<Song> arraylist;
    private Context mContext;
    private long[] songIDs;

    private View.OnClickListener mOnItemClickListener;

    public SongsListAdapter(Context context, List<Song> arraylist) {
        this.arraylist = arraylist;
        this.mContext = context;
        this.songIDs = getSongIds();
    }

    @Override
    public ItemHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_item, null);
        ItemHolder ml = new ItemHolder(v);
        return ml;
    }

    @Override
    public void onBindViewHolder(ItemHolder itemHolder, int i) {
        Song localItem = arraylist.get(i);

        itemHolder.title.setText(localItem.getTitle());
        itemHolder.artist.setText("Artist: " + localItem.getArtist());
        itemHolder.album.setText("Album: " + localItem.getAlbum());
        itemHolder.songDur.setText(LifterHelper.formatSongTimeUnits(localItem.getDuration()));

        // display albumart image for currently playing audio
        ImageLoader.getInstance().displayImage(LifterHelper.getAlbumArtUri(localItem.albumId).toString(),
                itemHolder.albumArt, new DisplayImageOptions.Builder().cacheInMemory(true)
                        .showImageOnLoading(R.drawable.ic_disc)
                        .resetViewBeforeLoading(true).build());

        // display visualizer widget for itemholder carrying currently playing audio
        if (MusicService.mediaPlayer != null && MusicService.getSongId() == localItem.getId()) {
            itemHolder.title.setSelected(true);
            itemHolder.title.setTextColor(mContext.getResources().getColor(R.color.colorAccent));
            itemHolder.visualizer.setColor(mContext.getResources().getColor(R.color.colorAccent));
            itemHolder.visualizer.setVisibility(View.VISIBLE);
        } else {
            itemHolder.title.setSelected(false);
            itemHolder.title.setTextColor(Color.WHITE);
            itemHolder.visualizer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return (null != arraylist ? arraylist.size() : 0);
    }

    public void setOnItemClickListener(View.OnClickListener itemClickListener) {
        mOnItemClickListener = itemClickListener;
    }

    public long[] getSongIds() {
        long[] ret = new long[getItemCount()];
        for (int i = 0; i < getItemCount(); i++) {
            ret[i] = arraylist.get(i).id;
        }

        return ret;
    }

    public class ItemHolder extends RecyclerView.ViewHolder {
        protected TextView title, artist, album, songDur;
        protected ImageView albumArt, popupMenu;
        private MusicVisualizer visualizer;

        public ItemHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.title);
            this.artist = view.findViewById(R.id.artist);
            this.album = view.findViewById(R.id.album);
            this.albumArt = view.findViewById(R.id.albumArt);
            this.visualizer = view.findViewById(R.id.visualizer);
            this.songDur = view.findViewById(R.id.song_duration);
            view.setTag(this);
            view.setOnClickListener(mOnItemClickListener);
        }
    }
}
