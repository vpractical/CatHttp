package com.y.cathttplib;

import com.y.cathttplib.chain.CallServiceInterceptor;
import com.y.cathttplib.chain.Chain;
import com.y.cathttplib.chain.ConnectInterceptor;
import com.y.cathttplib.chain.HeaderInterceptor;
import com.y.cathttplib.chain.Interceptor;
import com.y.cathttplib.chain.InterceptorChain;
import com.y.cathttplib.chain.RetryInterceptor;
import com.y.cathttplib.util.L;

import java.io.IOException;
import java.util.ArrayList;

public class Call {
    private Request request;
    private CatHttpClient catHttpClient;
    private boolean isExecuted;
    private boolean isCanceled;

    public Call(Request request, CatHttpClient client) {
        this.request = request;
        this.catHttpClient = client;
    }

    public Call enqueue(Callback callback){

        synchronized (this){
            if(isExecuted){
                throw new IllegalStateException("call has already executed");
            }
            isExecuted = true;
        }
        catHttpClient.dispatcher().enqueue(new AsyncCall(callback));
        return this;
    }

    public void cancel() {
        isCanceled = true;
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    public CatHttpClient client() {
        return catHttpClient;
    }
    public Request request() {
        return request;
    }


    final class AsyncCall implements Runnable{
        Callback callback;
        public AsyncCall(Callback callback){
            this.callback = callback;
        }

        public String host(){
            return request.url().host;
        }

        @Override
        public void run() {
            L.e("Call.AsyncAll.run方法开始，拦截器链条开始");
            try {
                Response response = getResponse();
                if(isCanceled){
                    callback.onFailure(Call.this,new IOException("call canceled"));
                }else{
                    callback.onResponse(Call.this,response);
                }
            }catch (Exception e){
                callback.onFailure(Call.this,e);
            }finally {
                catHttpClient.dispatcher().finished(this);
            }
        }
    }

    private Response getResponse() throws IOException{
        ArrayList<Interceptor> interceptors = new ArrayList<>();
        interceptors.addAll(catHttpClient.interceptors());
        interceptors.add(new RetryInterceptor());
        interceptors.add(new HeaderInterceptor());
        interceptors.add(new ConnectInterceptor());
        interceptors.add(new CallServiceInterceptor());

        Chain chain = new InterceptorChain(interceptors,this,null,0);
        return chain.proceed();
    }

}
