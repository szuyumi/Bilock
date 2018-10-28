package me.domin.bilock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements LockContract.View {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
    String date = df.format(new Date());// new Date()为获取当前系统时间，也可使用当前时间戳
    LockPresenter mPresenter;
    private static final String TAG = "MainActivity";

    @BindView(R.id.bt_svm_train)
    Button btGetPeaks;
    @BindView(R.id.mode_number)
    TextView tvShowModeNumber;
    //    @BindView(R.id.train_mode)
//    Switch switchMode;
    @BindView(R.id.bt_text)
    Button btText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mPresenter = new LockPresenter(this);

        //隐藏actionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivityPermissionsDispatcher.askForPermissionWithPermissionCheck(this);
    }

    //检查权限
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO})
    protected void askForPermission() {
        super.onResume();
        updateTextView();
    }

    @SuppressLint("SetTextI18n")
    void updateTextView() {

    }

    /**
     * 权限请求回调，提示用户之后，用户点击“允许”或者“拒绝”之后调用此方法
     * =
     *
     * @param requestCode  定义的权限编码
     * @param permissions  权限名称
     * @param grantResults 允许/拒绝
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


    //显示峰值
    @OnClick({R.id.bt_svm_train, R.id.bt_text})
    void showPeaks(View view) {
        if (view.getId() == R.id.bt_text) {
        } else {
//            try {
//            mPresenter.trainModel();
//                mPresenter.writeModel();
            mPresenter.svmTrain();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }

    }


    //清空textView
    @OnClick(R.id.button_clear)
    public void clear() {
        mPresenter.trainData();

        if (tvShowModeNumber.getText().length() != 0) {
            tvShowModeNumber.setText("");
        } else Toast.makeText(this, "Already Clear", Toast.LENGTH_SHORT).show();

        new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/WaveFile").delete();
        Toast.makeText(this, "Delete Success", Toast.LENGTH_SHORT).show();
    }

    @Override
    public InputStream getInputStream(String fileName) {
        InputStream in = null;
        try {
            in = getResources().getAssets().open(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return in;
    }

    @Override
    public void unlockSuccess() {

    }

    @Override
    public void unlockFail() {

    }

}
