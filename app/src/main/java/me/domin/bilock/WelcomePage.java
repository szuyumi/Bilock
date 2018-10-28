package me.domin.bilock;

import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.stephentuso.welcome.BasicPage;
import com.stephentuso.welcome.WelcomeActivity;
import com.stephentuso.welcome.WelcomeConfiguration;
import com.stephentuso.welcome.WelcomeHelper;

import me.wangyuwei.particleview.ParticleView;

public class WelcomePage extends WelcomeActivity {
    ParticleView particleView;

//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_welcome);
//
////        ActionBar actionBar = getSupportActionBar();
////        actionBar.hide();
////
////        particleView = findViewById(R.id.particle);
////        particleView.startAnim();
////
////        particleView.setOnParticleAnimListener(new ParticleView.ParticleAnimListener() {
////            @Override
////            public void onAnimationEnd() {
////                Intent intent = new Intent(WelcomePage.this, LockScreenActivity.class);
//////                startActivity(intent);
//////                finish();
////            }
////        });
//
//        welcomeHelper = new WelcomeHelper(this, WelcomePage.class);
//        welcomeHelper.show(savedInstanceState);
//    }

//    @Override
//    protected void onSaveInstanceState(Bundle outState) {
//        super.onSaveInstanceState(outState);
//        welcomeHelper.onSaveInstanceState(outState);
//    }

    @Override
    protected WelcomeConfiguration configuration() {
        return new WelcomeConfiguration.Builder(this)
                .defaultTitleTypefacePath("Montserrat-Bold.ttf")
                .defaultHeaderTypefacePath("Montserrat-Bold.ttf")
                .page(new BasicPage(R.drawable.svg_lock, "Screen Lock", "An APP using biting sound to unlock.")
                        .background(R.color.orange))
                .page(new BasicPage(R.drawable.svg_wave, "Wave", "Using the sound of biting to identify users.")
                        .background(R.color.blue))
                .page(new BasicPage(R.drawable.svg_save, "Safe", "Only the legal user can unlock the phone.")
                        .background(R.color.pule))
                .swipeToDismiss(true)
                .exitAnimation(android.R.anim.fade_out)
                .build();
    }
}
