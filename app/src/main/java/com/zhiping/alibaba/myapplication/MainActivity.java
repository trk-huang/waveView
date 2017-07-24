package com.zhiping.alibaba.myapplication;

import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    private static final String LOGIN_BROADCAST_ACTION = "com.aliyun.xiaoyunmi.action.AYUN_LOGIN_BROADCAST";
    private static final String LOGOUT_BROADCAST_ACTION = "com.aliyun.xiaoyunmi.action.DELETE_ACCOUNT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
