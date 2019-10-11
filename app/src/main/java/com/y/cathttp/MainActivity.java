package com.y.cathttp;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.y.cathttplib.Call;
import com.y.cathttplib.Callback;
import com.y.cathttplib.CatHttpClient;
import com.y.cathttplib.Request;
import com.y.cathttplib.Response;
import com.y.cathttplib.util.L;

public class MainActivity extends AppCompatActivity {
    CatHttpClient catHttpClient = new CatHttpClient();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Request request = new Request.Builder()
                        .url("http://www.baidu.com")
                        .get()
                        .build();
                Call call = catHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, Throwable throwable) {
                        L.e("MainActivity:onFailure:" + throwable.toString());
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        L.e("MainActivity:onResponse");
                    }
                });
            }
        });



    }
}
