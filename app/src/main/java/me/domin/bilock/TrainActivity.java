package me.domin.bilock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.administrator.mfcc.MFCC;
import com.hanks.htextview.base.AnimationListener;
import com.hanks.htextview.base.HTextView;
import com.hanks.htextview.scale.ScaleText;
import com.race604.drawable.wave.WaveDrawable;
import com.robinhood.ticker.TickerUtils;
import com.robinhood.ticker.TickerView;
import com.sackcentury.shinebuttonlib.ShineButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainActivity extends AppCompatActivity {

    private static final int NUM_CHANGE = 1;
    private static final int NUM_MAX = 2;
    ShineButton shineButton;
    ImageView imageView;
    TextView hintText;
    TextView finishText;
    WaveDrawable waveDrawable;
    TickerView tickerView;
    private String username = "user";
    public String path = LockPresenter.absolutePath + "/Bilock/" + username + File.separator;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case NUM_CHANGE:
                    double num = getRandNumber(getFileNumber(path));
                    if (num > 100) {
                        num = 100.00;
                        shineButton.setVisibility(View.VISIBLE);
                        shineButton.performClick();
                        hintText.setText("完成声纹录制！");
                        tickerView.setText(num + "%");
                        waveDrawable.setLevel(getFileNumber(path) * 1000);

                    } else {
                        tickerView.setText(num + "%");
                        waveDrawable.setLevel(getFileNumber(path) * 1000);
                    }
                    break;
                case NUM_MAX:
                    Intent intent = new Intent(TrainActivity.this, LockScreenActivity.class);
                    startActivity(intent);
                    onStop();
                    finish();
                    break;
            }
        }
    };

    double getRandNumber(int num) {
        int rand = (int) (Math.random() * 900);
        double result = num * 10 + (double) rand / 100;
        return result;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        shineButton = findViewById(R.id.finish);
        shineButton.init(this);
        finishText = findViewById(R.id.finish_text);
        hintText = findViewById(R.id.hint_text);
        tickerView = findViewById(R.id.tickerView);
        tickerView.setCharacterLists(TickerUtils.provideNumberList());
        imageView = findViewById(R.id.image);
        waveDrawable = new WaveDrawable(getDrawable(R.drawable.bilock_logo));
        imageView.setImageDrawable(waveDrawable);
        waveDrawable.setWaveSpeed(10);
        waveDrawable.setWaveAmplitude(10);

        tickerView.setText("0%");
        tickerView.setGravity(Gravity.START);
        hintText.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        hintText.getPaint().setFakeBoldText(true);
        finishText.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        finishText.getPaint().setFakeBoldText(true);

        //权限
        initRecorder();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        executor.execute(new RecordTask());
    }


    public AudioRecord record;
    int sampleRate = 44100;
    int fftlen = 1024;
    int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    int numOfReadShort;
    int readChunkSize = fftlen;  // Every hopLen one fft result (overlapped analyze window)
    short[] audioSamples = new short[readChunkSize];
    int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;

    public void initRecorder() {
        record = new AudioRecord(6, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 2 * bufferSampleSize);
        record.startRecording();
        bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;
    }

    class RecordTask implements Runnable {
        WavWriter wavWriter = new WavWriter("/MFCC/", sampleRate);

        @Override
        public void run() {
            wavWriter.start();
            int fileNumber = 0;

            while (fileNumber < 10) {

                int max = 0;
                int num = 0;
                while (num != 2) {
                    numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                    max = wavWriter.pushAudioShortNew(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                    if (max == -1)
                        num++;
                }

                int[] singal = wavWriter.getSignal();
                BufferedWriter bw = null;

                double[] buffer = new double[singal.length];
                for (int i = 0; i < singal.length; i++) {
                    buffer[i] = singal[i];
                }

                DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss.SSS's'", Locale.US);
                String nowStr = df.format(new Date());
                Double[] featureDouble = null;
                try {
                    featureDouble = MFCC.mfcc(LockPresenter.absolutePath + "/MFCC/Feature.txt", buffer, singal.length, 44100);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    bw = createBufferedWriter(LockPresenter.absolutePath, "/Bilock/" + username + "/" + nowStr + ".txt");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //将所有MFCC特征写入文件
                //将数据存入文件
                writeData(featureDouble, bw);

                fileNumber++;
                Message message = new Message();
                message.what = NUM_CHANGE;
                handler.sendMessage(message);
            }
            synchronized (this) {
                try {
                    new Thread().sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            record.stop();
            record.release();
            Message message = new Message();
            message.what = NUM_MAX;
            handler.sendMessage(message);

        }
    }

    private BufferedWriter createBufferedWriter(String path, String name) throws IOException {
        File file = new File(path + name);
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();
        return new BufferedWriter(new FileWriter(file));
    }

    int getFileNumber(String path) {
        File file = new File(path);
        if (!file.exists())
            file.mkdirs();
        File[] files = new File(path).listFiles();
        return files.length;
    }

    private void writeData(Double feature[], BufferedWriter bw) {

        try {
            bw.write(1 + " ");
            for (int i = 0; i < feature.length; i++) {
                bw.write((i + 1) + ":" + String.valueOf(feature[i]));
                if (i != feature.length - 1)
                    bw.write(" ");
                else bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
            }

            bw.write(" \n");
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class MyScaleTextView extends HTextView {
        private ScaleText scaleText;

        private boolean animate = true;

        public MyScaleTextView(Context context) {
            this(context, null);
        }

        public MyScaleTextView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public MyScaleTextView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            scaleText = new ScaleText();
            scaleText.init(this, attrs, defStyleAttr);
            setMaxLines(1);
            setEllipsize(TextUtils.TruncateAt.END);
        }

        @Override
        public void setAnimationListener(AnimationListener listener) {
            scaleText.setAnimationListener(listener);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!animate) {
                super.onDraw(canvas);
            } else {
                scaleText.onDraw(canvas);
            }
        }

        @Override
        public void setProgress(float progress) {
            scaleText.setProgress(progress);
        }

        @Override
        public void animateText(CharSequence text) {
            this.animate = true;
            scaleText.animateText(text);
        }

        public void animateText(CharSequence text, boolean animate) {
            this.animate = animate;
            if (animate) {
                scaleText.animateText(text);
            } else {
                setText(text);
            }
        }
    }

}
