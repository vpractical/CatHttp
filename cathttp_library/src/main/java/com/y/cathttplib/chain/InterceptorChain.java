package com.y.cathttplib.chain;

import com.y.cathttplib.Call;
import com.y.cathttplib.HttpConnection;
import com.y.cathttplib.Response;

import java.io.IOException;
import java.util.ArrayList;

public class InterceptorChain implements Chain {
    ArrayList<Interceptor> interceptors;
    Call call;
    HttpConnection httpConnection;
    int index;

    public InterceptorChain(ArrayList<Interceptor> interceptors, Call call, HttpConnection httpConnection, int index) {
        this.interceptors = interceptors;
        this.call = call;
        this.httpConnection = httpConnection;
        this.index = index;
    }


    @Override
    public Response proceed() throws IOException {
        return proceed(httpConnection);
    }

    @Override
    public Response proceed(HttpConnection httpConnection) throws IOException {
        Interceptor interceptor = interceptors.get(index);
        Chain chain = new InterceptorChain(interceptors,call,httpConnection,index + 1);
        return interceptor.interceptor(chain);
    }
}
