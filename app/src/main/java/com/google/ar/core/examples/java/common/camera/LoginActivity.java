package com.google.ar.core.examples.java.common.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.examples.java.common.helpers.AudioRecordUtil;
import com.google.ar.core.examples.java.helloar.R;
import com.google.mediapipe.components.PermissionHelper;

public class LoginActivity extends Activity implements View.OnClickListener{
    private static final String TAG = LoginActivity.class.getSimpleName();
    private static String server_ip;
    private boolean isFirstClick = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.login_main);
        bindView();
        PermissionHelper.checkAndRequestAudioPermissions(this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        isFirstClick = true;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy start.");
        super.onDestroy();
        Log.i(TAG, "onDestroy end.");
    }


    private void bindView(){
        Button enter_ip = findViewById(R.id.enter_ip);
        enter_ip.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        /*if (!isFirstClick) {
            return;
        } else {
            isFirstClick = false;
        }*/
        switch (v.getId()){
            case R.id.enter_ip:
                AlertDialog.Builder dialog = new AlertDialog.Builder(LoginActivity.this);
                dialog.setTitle("请输入服务器IP");
                final View view = View.inflate(LoginActivity.this, R.layout.udpserver_ip, null);
                EditText et = view.findViewById(R.id.server_ip);
                dialog.setView(view);
                dialog.setPositiveButton("确认" , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String ip = et.getText().toString();
                        Toast.makeText(LoginActivity.this, "Cloud server ip:" + ip, Toast.LENGTH_SHORT).show();
                        server_ip = ip;
                        dialogInterface.cancel();
                    }
                });

                dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                dialog.show();
                break;
            case R.id.btn_start:
                Intent intent = new Intent(this, HelloAr2Activity.class);
                intent.putExtra("server_ip", server_ip);
                startActivity(intent);
                break;
            default:
                Log.e(TAG, "onClick error!");
        }
    }
}
