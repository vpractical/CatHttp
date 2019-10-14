package com.y.cathttplib;

import com.y.cathttplib.util.L;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * keep-alive 就是浏览器和服务端之间保持长连接，这个连接是可以复用的。在HTTP1.1中是默认开启的。
 * <p>
 * 连接的复用为什么会提高性能呢？
 * <p>
 * (一次响应的过程) 通常我们在发起http请求的时候首先要完成tcp的三次握手，然后传输数据，最后再释放连接
 * <p>
 * 如果在高并发的请求连接情况下或者同个客户端多次频繁的请求操作，无限制的创建会导致性能低下。
 * 如果使用keep-alive，在timeout空闲时间内，连接不会关闭，相同重复的request将复用原先的connection，
 * 减少握手的次数，大幅提高效率。（并非keep-alive的timeout设置时间越长，就越能提升性能。
 * 长久不关闭会造成过多的僵尸连接和泄露连接出现）
 */
public class HttpConnectionPool {

    //长连接最大时间
    private final long keepAliveDuration;

    //复用队列
    private final Deque<HttpConnection> httpConnections = new ArrayDeque<>();

    private boolean isCleanRunning;

    public HttpConnectionPool() {
        this(1, TimeUnit.MINUTES);
    }

    public HttpConnectionPool(long keepAliveDuration, TimeUnit timeUnit) {
        this.keepAliveDuration = timeUnit.toMillis(keepAliveDuration);
    }

    private static final Executor executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "catHttp.socket回收线程");
            //守护线程,当java虚拟机中没有非守护线程在运行的时候，java虚拟机会关闭
            thread.setDaemon(true);
            return thread;
        }
    });

    //检测闲置socket并对其进行清理
    private Runnable cleanRunnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                long waitTime = clean();
                L.e("waitTime = " + waitTime);
                if (waitTime <= 0) {
                    return;
                }
                synchronized (HttpConnectionPool.this) {
                    try {
                        //调用某个对象的wait()方法能让当前线程阻塞，
                        // 并且当前线程必须拥有此对象的monitor（即锁）
                        HttpConnectionPool.this.wait(waitTime);
                    } catch (InterruptedException e) {
                        L.e(e.toString());
                    }
                }
            }
        }
    };

    public void put(HttpConnection connection) {
        L.e("HttpConnectionPool:连接池新增");
        //执行检测清理
        if (!isCleanRunning) {
            L.e("HttpConnectionPool:连接池回收程序开始");
            isCleanRunning = true;
            executor.execute(cleanRunnable);
        }
        httpConnections.add(connection);
    }

    public HttpConnection get(String host, int port) {
        Iterator<HttpConnection> iterator = httpConnections.iterator();
        while (iterator.hasNext()) {
            HttpConnection connection = iterator.next();
            //查连接是否复用( 同样的host )
            if (connection.isSameAddress(host, port)) {
                //正在使用的移出连接池
                iterator.remove();
                L.e("HttpConnectionPool:连接池获取");
                return connection;
            }
        }
        return null;
    }

    private long clean() {
        long now = System.currentTimeMillis();
        long longestIdleDuration = -1;
        synchronized (this) {
            L.e("HttpConnectionPool:连接池清理......");
            for (Iterator<HttpConnection> i = httpConnections.iterator(); i.hasNext(); ) {
                HttpConnection connection = i.next();
                //获得闲置时间 多长时间没使用这个了
                long idleDuration = now - connection.lastUseTime;
                //如果闲置时间超过允许
                if (idleDuration > keepAliveDuration) {
                    connection.closeQuietly();
                    i.remove();
                    L.e("HttpConnectionPool:移出连接池");
                    continue;
                }
                //获得最大闲置时间
                if (longestIdleDuration < idleDuration) {
                    longestIdleDuration = idleDuration;
                }
            }
            //下次检查时间
            if (longestIdleDuration >= 0) {
                return keepAliveDuration - longestIdleDuration;
            } else {
                //连接池没有连接 可以退出
                L.e("HttpConnectionPool:连接池空，连接池回收程序结束");
                isCleanRunning = false;
                return longestIdleDuration;
            }
        }
    }
}
