package com.y.cathttplib.chain;

import com.y.cathttplib.HttpConnection;
import com.y.cathttplib.Response;

import java.io.IOException;

public interface Chain {
    Response proceed() throws IOException;
    Response proceed(HttpConnection httpConnection) throws IOException;
}
