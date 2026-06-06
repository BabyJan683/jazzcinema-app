package com.cinema.jazz.onboarding;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.cinema.jazz.R;
import com.cinema.jazz.auth.LoginActivity;
import com.cinema.jazz.util.WhatsAppHelper;
public class OnboardingActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        SharedPreferences p=getSharedPreferences("jazz_app",MODE_PRIVATE);
        if(p.getBoolean("onboarding_done",false)){goNext();return;}
        setContentView(R.layout.activity_onboarding);
        android.view.View btn=findViewById(R.id.btn_continue);
        if(btn!=null)btn.setOnClickListener(v->{p.edit().putBoolean("onboarding_done",true).apply();goNext();});
        android.view.View sub=findViewById(R.id.btn_subscribe);
        if(sub!=null)sub.setOnClickListener(v->WhatsAppHelper.showSubscribeDialog(this));
    }
    private void goNext(){startActivity(new Intent(this,LoginActivity.class));finish();}
}
