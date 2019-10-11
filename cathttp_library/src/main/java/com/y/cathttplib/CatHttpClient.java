package com.y.cathttplib;

import com.y.cathttplib.chain.Interceptor;

import java.util.ArrayList;
import java.util.List;

public class CatHttpClient {

    private Dispatcher dispatcher = new Dispatcher();
    private HttpConnectionPool connectionPool = new HttpConnectionPool();
    private int retries;
    private ArrayList<Interceptor> interceptors;


    public CatHttpClient(){
        this(new Builder());
    }

    public CatHttpClient(Builder builder){
        this.retries = builder.retries;
        this.interceptors = builder.interceptors;
    }


    public Call newCall(Request request){
        return new Call(request,this);
    }

    public int retries() {
        return retries;
    }

    public Dispatcher dispatcher() {
        return dispatcher;
    }

    public HttpConnectionPool connectionPool() {
        return connectionPool;
    }

    public List<Interceptor> interceptors() {
        return interceptors;
    }


    public static final class Builder{
        //默认重试3次
        int retries = 3;
        ArrayList<Interceptor> interceptors = new ArrayList<>();

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }
    }
}
