package com.cinema.jazz;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class BlockedActivity extends AppCompatActivity {
    public static final String TYPE_BLOCKED="blocked", TYPE_MAINTENANCE="maintenance", TYPE_UPDATE="update";

    @Override protected void onCreate(Bundle s){
        super.onCreate(s); setContentView(R.layout.activity_blocked);
        String type=getIntent().getStringExtra("type");
        String msg=getIntent().getStringExtra("message");
        TextView txtTitle=findViewById(R.id.txt_blocked_title);
        TextView txtMessage=findViewById(R.id.txt_blocked_message);
        ImageView imgIcon=findViewById(R.id.img_blocked_icon);
        if(type==null) type=TYPE_BLOCKED;
        switch(type){
            case TYPE_MAINTENANCE:
                if(txtTitle!=null) txtTitle.setText("Under Maintenance");
                if(imgIcon!=null) imgIcon.setImageResource(android.R.drawable.ic_dialog_info); break;
            case TYPE_UPDATE:
                if(txtTitle!=null) txtTitle.setText("Update Required");
                if(imgIcon!=null) imgIcon.setImageResource(android.R.drawable.ic_menu_upload); break;
            default:
                if(txtTitle!=null) txtTitle.setText("App Disabled");
                if(imgIcon!=null) imgIcon.setImageResource(android.R.drawable.ic_dialog_alert); break;
        }
        if(txtMessage!=null&&msg!=null) txtMessage.setText(msg);
    }

    @Override public void onBackPressed(){ finishAffinity(); }
}
