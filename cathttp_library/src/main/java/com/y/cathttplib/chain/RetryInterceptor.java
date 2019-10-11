package com.y.cathttplib.chain;

import com.y.cathttplib.Call;
import com.y.cathttplib.Response;
import com.y.cathttplib.util.L;

import java.io.IOException;

public class RetryInterceptor implements Interceptor {
    @Override
    public Response interceptor(Chain chain) throws IOException {
        L.e("RetryInterceptor:interceptor()");
        InterceptorChain interceptorChain = (InterceptorChain) chain;
        Call call = interceptorChain.call;
        int retries = call.client().retries();
        IOException ioException = null;
        for (int i = 0; i < retries; i++) {
            if(call.isCanceled()){
                throw new IOException("call canceled!");
            }
            try{
                return chain.proceed();
            }catch(IOException e){
                ioException = e;
            }
        }
        throw ioException;
    }
}
