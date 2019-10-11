package com.y.cathttplib.chain;

import com.y.cathttplib.Response;

import java.io.IOException;

public interface Interceptor {
    Response interceptor(Chain chain) throws IOException;
}
