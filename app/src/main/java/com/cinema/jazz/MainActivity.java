package com.cinema.jazz;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.cinema.jazz.auth.AuthManager;
import com.cinema.jazz.auth.LoginActivity;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.ui.downloads.DownloadsFragment;
import com.cinema.jazz.ui.home.HomeFragment;
import com.cinema.jazz.ui.live.LiveTvFragment;
import com.cinema.jazz.ui.settings.SettingsFragment;
import com.cinema.jazz.ui.shorts.ShortsFragment;
import com.cinema.jazz.util.NoticeDialog;
import com.cinema.jazz.util.SecurityChecker;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;
    private final Handler mainH=new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle s){
        applyAppTheme(); super.onCreate(s);
        if(SecurityChecker.checkAndBlock(this)) return;
        if(!AuthManager.isLoggedIn(this)){startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_main);
        bottomNav=findViewById(R.id.bottom_nav);
        if(s==null) loadFragment(new HomeFragment());
        bottomNav.setOnItemSelectedListener(item->{
            int id=item.getItemId();
            if(id==R.id.nav_home){loadFragment(new HomeFragment());return true;}
            else if(id==R.id.nav_trending){loadFragment(new ShortsFragment());return true;}
            else if(id==R.id.nav_live){loadFragment(new LiveTvFragment());return true;}
            else if(id==R.id.nav_downloads){clearBadge();loadFragment(new DownloadsFragment());return true;}
            else if(id==R.id.nav_settings){loadFragment(new SettingsFragment());return true;}
            return false;
        });
        refreshBadge();
        mainH.postDelayed(()->{if(!isFinishing()) NoticeDialog.show(this);},1500);
    }

    private void applyAppTheme(){
        SharedPreferences p=getSharedPreferences("jazz_settings",MODE_PRIVATE);
        String t=p.getString(Constants.PREF_THEME,Constants.THEME_DEFAULT);
        if(Constants.THEME_BLUE_ORANGE.equals(t)) setTheme(R.style.AppTheme_BlueOrange);
        else setTheme(R.style.AppTheme);
    }

    @Override protected void onResume(){
        super.onResume();
        if(!AuthManager.isLoggedIn(this)){startActivity(new Intent(this,LoginActivity.class));finish();}
        else refreshBadge();
    }

    public void refreshBadge(){
        Executors.newSingleThreadExecutor().execute(()->{
            int n=AppDatabase.getInstance(this).downloadDao().count();
            mainH.post(()->{
                if(isFinishing()||bottomNav==null) return;
                BadgeDrawable b=bottomNav.getOrCreateBadge(R.id.nav_downloads);
                if(n>0){b.setVisible(true);b.setNumber(n);b.setBackgroundColor(0xFF00B4D8);b.setBadgeTextColor(0xFFFFFFFF);}
                else b.setVisible(false);
            });
        });
    }

    private void clearBadge(){BadgeDrawable b=bottomNav.getBadge(R.id.nav_downloads);if(b!=null)b.setVisible(false);}

    public void setBottomNavVisible(boolean visible){
        if(bottomNav!=null) bottomNav.setVisibility(visible?android.view.View.VISIBLE:android.view.View.GONE);
    }

    public void selectHomeNav(){if(bottomNav!=null) bottomNav.setSelectedItemId(R.id.nav_home);}

    private void loadFragment(@NonNull Fragment f){
        if(!(f instanceof ShortsFragment)) setBottomNavVisible(true);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,f).commit();
    }
}
