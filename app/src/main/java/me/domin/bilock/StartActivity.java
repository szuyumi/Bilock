package me.domin.bilock;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.stephentuso.welcome.WelcomeHelper;

import java.io.File;

import me.wangyuwei.particleview.ParticleView;

public class StartActivity extends AppCompatActivity {

    private static final String TAG = "StartActivity";
    private WelcomeHelper welcomeHelper;
    private ParticleView particleView;
    String username = "user";
    String path = LockPresenter.absolutePath + "/Bilock/" + username + File.separator;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        final Activity activity = this;

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
//
        particleView = findViewById(R.id.particle);
        particleView.startAnim();
//
        particleView.setOnParticleAnimListener(new ParticleView.ParticleAnimListener() {
            @Override
            public void onAnimationEnd() {
                welcomeHelper = new WelcomeHelper(activity, WelcomePage.class);
                welcomeHelper.show(savedInstanceState);
                welcomeHelper.forceShow();

            }
        });

        File[] files = new File(path).listFiles();
        if (files != null && files.length != 0)
        {
            startLockScreenActivity();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        startTeachActivity();
    }

    void startLockScreenActivity()
    {
        Intent intent = new Intent(StartActivity.this, LockScreenActivity.class);
        startActivity(intent);
//        onDestroy();
        finish();
    }

    void startTeachActivity()
    {
        Intent intent = new Intent(StartActivity.this, TeachActivity.class);
        startActivity(intent);
        finish();
//        onDestroy();

    }


}
