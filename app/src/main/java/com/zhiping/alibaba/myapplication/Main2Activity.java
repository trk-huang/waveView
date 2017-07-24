package com.zhiping.alibaba.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Main2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        final TestView v= (TestView) findViewById(R.id.test_view);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    v.postInvalidate();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
