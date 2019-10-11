package com.y.cathttplib.chain;

import com.y.cathttplib.HttpCodec;
import com.y.cathttplib.HttpConnection;
import com.y.cathttplib.Response;
import com.y.cathttplib.util.L;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class CallServiceInterceptor implements Interceptor {
    @Override
    public Response interceptor(Chain chain) throws IOException {
        L.e("CallServiceInterceptor:interceptor()");
        InterceptorChain interceptorChain = (InterceptorChain) chain;
        final HttpCodec httpCodec = new HttpCodec();
        HttpConnection connection = interceptorChain.httpConnection;
        InputStream is = connection.call(httpCodec);
        //HTTP/1.1 200 OK 空格隔开的响应状态
        String readLine = httpCodec.readLine(is);

        Map<String, String> headerMap = httpCodec.readHeaders(is);
        //是否保持连接
        boolean isKeepAlive = false;
        if(headerMap.containsKey(HttpCodec.HEAD_CONNECTION)){
            isKeepAlive = headerMap.get(HttpCodec.HEAD_CONNECTION).equalsIgnoreCase(HttpCodec.HEAD_VALUE_KEEP_ALIVE);
        }
        int contentLength = -1;
        if (headerMap.containsKey(HttpCodec.HEAD_CONTENT_LENGTH)) {
            contentLength = Integer.valueOf(headerMap.get(HttpCodec.HEAD_CONTENT_LENGTH));
        }
        //分块编码数据
        boolean isChunked = false;
        if (headerMap.containsKey(HttpCodec.HEAD_TRANSFER_ENCODING)) {
            isChunked = headerMap.get(HttpCodec.HEAD_TRANSFER_ENCODING).equalsIgnoreCase(HttpCodec.HEAD_VALUE_CHUNKED);
        }

        String body = null;
        if(contentLength > 0){
            byte[] bytes = httpCodec.readBytes(is, contentLength);
            body = new String(bytes);
        } else if(isChunked){
            body = httpCodec.readChunked(is);
        }

        String[] split = readLine.split(" ");
        int code = Integer.valueOf(split[1]);
        connection.updateLastUseTime();

        return new Response(code,contentLength,headerMap,body,isKeepAlive);
    }
}
