package com.cinema.jazz.ui.live;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.*;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.live.*;
import com.cinema.jazz.ui.player.PlayerActivity;
import java.util.List;
public class LiveTvFragment extends Fragment {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView txtNowPlaying;
    private LinearLayout catTabs;
    private View playerPlaceholder;
    private RecyclerView recycler;
    private ImageButton btnFullscreen;
    private String currentCat="all";
    private Channel currentChannel;
    private String currentStreamUrl=Constants.GEO_NEWS_STREAM;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf,@Nullable ViewGroup c,@Nullable Bundle s){return inf.inflate(R.layout.fragment_live_tv,c,false);}
    @Override public void onViewCreated(@NonNull View v,@Nullable Bundle s){
        super.onViewCreated(v,s);
        playerView=v.findViewById(R.id.player_view);playerPlaceholder=v.findViewById(R.id.player_placeholder);
        txtNowPlaying=v.findViewById(R.id.txt_now_playing);catTabs=v.findViewById(R.id.cat_tabs);
        recycler=v.findViewById(R.id.recycler_channels);btnFullscreen=v.findViewById(R.id.btn_fullscreen);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        buildCategoryTabs();showChannels("all");initPlayer();
        Channel geo=findGeoNews();
        if(geo!=null)playChannel(geo);else playUrl(Constants.GEO_NEWS_STREAM,"Geo News");
        if(btnFullscreen!=null)btnFullscreen.setOnClickListener(x->openFullscreen());
    }
    private Channel findGeoNews(){for(Channel ch:ChannelsData.ALL)if(ch.name!=null&&ch.name.toLowerCase().contains("geo"))return ch;return null;}
    private void initPlayer(){player=new ExoPlayer.Builder(requireContext()).build();playerView.setPlayer(player);player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY&&playerPlaceholder!=null)playerPlaceholder.setVisibility(View.GONE);}});}
    private void playChannel(Channel ch){currentChannel=ch;currentStreamUrl=ch.streamUrl;playUrl(ch.streamUrl,ch.name);}
    private void playUrl(String url,String name){if(player==null)initPlayer();player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.play();if(playerPlaceholder!=null)playerPlaceholder.setVisibility(View.GONE);if(txtNowPlaying!=null){txtNowPlaying.setText("▶ "+name);txtNowPlaying.setVisibility(View.VISIBLE);}}
    private void buildCategoryTabs(){if(catTabs==null)return;catTabs.removeAllViews();addTab("all","📺 All");for(String cat:ChannelsData.categories())addTab(cat,ChannelsData.catLabel(cat));}
    private void addTab(String cat,String label){TextView tv=new TextView(requireContext());tv.setText(label);tv.setTextColor(0xFFFFFFFF);tv.setTextSize(12);tv.setPadding(28,8,28,8);tv.setTypeface(null,android.graphics.Typeface.BOLD);tv.setBackground(makeTabBg(cat.equals(currentCat)));tv.setOnClickListener(v->{currentCat=cat;buildCategoryTabs();showChannels(cat);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(4,0,4,0);catTabs.addView(tv,lp);}
    private android.graphics.drawable.GradientDrawable makeTabBg(boolean active){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setCornerRadius(24);d.setColor(active?0xFFE50914:0x33FFFFFF);return d;}
    private void showChannels(String cat){List<Channel>list=cat.equals("all")?ChannelsData.ALL:ChannelsData.byCategory(cat);TextView hdr=getView()!=null?getView().findViewById(R.id.txt_cat_header):null;if(hdr!=null)hdr.setText("📺 "+(cat.equals("all")?"All Channels":ChannelsData.catLabel(cat))+" ("+list.size()+")");if(recycler!=null)recycler.setAdapter(new ChannelAdapter(list,this::playChannel));}
    private void openFullscreen(){if(player!=null)player.stop();Intent i=new Intent(requireContext(),PlayerActivity.class);i.putExtra(Constants.KEY_TITLE,currentChannel!=null?currentChannel.name:"Live TV");i.putExtra(Constants.KEY_DRIVE_URL,currentStreamUrl);i.putExtra("is_live",true);startActivity(i);}
    @Override public void onPause(){super.onPause();if(player!=null)player.pause();}
    @Override public void onResume(){super.onResume();if(player!=null)player.play();}
    @Override public void onDestroyView(){super.onDestroyView();if(player!=null){player.release();player=null;}}
}
