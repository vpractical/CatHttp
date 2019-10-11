package com.y.cathttplib.chain;

import com.y.cathttplib.HttpCodec;
import com.y.cathttplib.Request;
import com.y.cathttplib.Response;
import com.y.cathttplib.util.L;

import java.io.IOException;
import java.util.Map;

public class HeaderInterceptor implements Interceptor {
    @Override
    public Response interceptor(Chain chain) throws IOException {
        L.e("HeaderInterceptor:interceptor()");
        InterceptorChain interceptorChain = (InterceptorChain) chain;
        Request request = interceptorChain.call.request();
        Map<String, String> headers = request.headers();
        headers.put(HttpCodec.HEAD_HOST, request.url().getHost());
        headers.put(HttpCodec.HEAD_CONNECTION, HttpCodec.HEAD_VALUE_KEEP_ALIVE);
        if (null != request.body()) {
            String contentType = request.body().contentType();
            if (contentType != null) {
                headers.put(HttpCodec.HEAD_CONTENT_TYPE, contentType);
            }
            long contentLength = request.body().contentLength();
            if (contentLength != -1) {
                headers.put(HttpCodec.HEAD_CONTENT_LENGTH, Long.toString(contentLength));
            }
        }
        return chain.proceed();
    }
}
