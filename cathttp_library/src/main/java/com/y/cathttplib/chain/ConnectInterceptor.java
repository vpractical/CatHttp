package com.y.cathttplib.chain;

import com.y.cathttplib.CatHttpClient;
import com.y.cathttplib.HttpConnection;
import com.y.cathttplib.HttpUrl;
import com.y.cathttplib.Request;
import com.y.cathttplib.Response;
import com.y.cathttplib.util.L;

import java.io.IOException;

public class ConnectInterceptor implements Interceptor {
    @Override
    public Response interceptor(Chain chain) throws IOException {
        L.e("ConnectInterceptor:interceptor()");
        InterceptorChain interceptorChain = (InterceptorChain) chain;
        Request request = interceptorChain.call.request();
        CatHttpClient client = interceptorChain.call.client();
        HttpUrl url = request.url();
        String host = url.getHost();
        int port = url.getPort();

        HttpConnection httpConnection = client.connectionPool().get(host, port);
        if(null == httpConnection){
            L.e("ConnectInterceptor:连接池没有，new HttpConnection()");
            httpConnection = new HttpConnection();
        }else{
            L.e("ConnectInterceptor:从连接池得到HttpConnection");
        }
        httpConnection.setRequest(request);
        Response response = chain.proceed(httpConnection);
        if(response.isKeepAlive()){
            client.connectionPool().put(httpConnection);
        }
        return response;
    }
}
