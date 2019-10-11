package com.y.cathttplib;

import android.text.TextUtils;

import java.net.MalformedURLException;
import java.net.URL;

public class HttpUrl {

    String protocol; //协议：http https
    String host; //1.1.1.1
    String file; //访问文件地址 main.jsp
    int port;

    public HttpUrl(String url) throws MalformedURLException {
        URL uUrl = new URL(url);
        protocol = uUrl.getProtocol();
        host = uUrl.getHost();
        file = TextUtils.isEmpty(uUrl.getFile()) ? "/" : uUrl.getFile();
        port = uUrl.getPort() <= 0 ? uUrl.getDefaultPort() : uUrl.getPort();
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public String getFile() {
        return file;
    }

    public int getPort() {
        return port;
    }
}
