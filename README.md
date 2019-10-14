[TOC]

记录下异步请求实现思路,

# 使用
* 创建CatHttpClient对象
* 创建Request对象
* 创建Call对象
* 调用Call.enqueue(Callback)开始运行过程
```
                CatHttpClient catHttpClient = new CatHttpClient();
                Request request = new Request.Builder()
                        .url("http://www.baidu.com")
                        .get()
                        .build();
                Call call = catHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, Throwable throwable) {
                        L.e("MainActivity:onFailure:" + throwable.toString());
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        L.e("MainActivity:onResponse");
                    }
                });
```

# 代码调用过程
1.Call中：调用了Dispatcher对象的enqueue方法，传入AsyncCall对象，这是一个Runnable
```
public Call enqueue(Callback callback){
        synchronized (this){
            if(isExecuted){
                throw new IllegalStateException("call has already executed");
            }
            isExecuted = true;
        }
        catHttpClient.dispatcher().enqueue(new AsyncCall(callback));
        return this;
    }
```
2.Dispatcher中：AsyncCall(runnable)放入线程池，调用它的run方法
```
    //最多同时请求
    int maxRequests;
    int maxRequestsPerHost;
    //线程池，发送异步请求
    private ExecutorService executorService;
    //等待执行队列
    private final Deque<Call.AsyncCall> readyAsyncCalls = new ArrayDeque<>();
    //正在执行队列
    private final Deque<Call.AsyncCall> runningAsyncCalls = new ArrayDeque<>();

    public void enqueue(Call.AsyncCall call){
        if(runningAsyncCalls.size() >= maxRequests || runningCallsForHost(call) >= maxRequestsPerHost){
            L.e("Dispatcher:超出最大请求数，放入等待执行队列");
            readyAsyncCalls.add(call);
        }else{
            L.e("Dispatcher:开始执行，放入正在执行队列");
            runningAsyncCalls.add(call);
            executorService().execute(call);
        }
    }
```
3.Call中内部类AsyncCall.run()，getResponse()是执行任务，finally中通知Dispatcher任务结束
```
final class AsyncCall implements Runnable{
        Callback callback;
        public AsyncCall(Callback callback){
            this.callback = callback;
        }

        public String host(){
            return request.url().host;
        }

        @Override
        public void run() {
            L.e("Call.AsyncAll.run方法开始，拦截器链条开始");
            try {
                Response response = getResponse();
                if(isCanceled){
                    callback.onFailure(Call.this,new IOException("call canceled"));
                }else{
                    callback.onResponse(Call.this,response);
                }
            }catch (Exception e){
                callback.onFailure(Call.this,e);
            }finally {
                catHttpClient.dispatcher().finished(this);
            }
        }
    }
```
4.Call中：OkHttp的重点在于拦截器实现的责任链模式,将拦截器放入一个list中，然后通过责任链的方式调用，从CallServiceInterceptor获取Response，再层层返回，每层拦截器，处理对应的业务
```
private Response getResponse() throws IOException{
        ArrayList<Interceptor> interceptors = new ArrayList<>();
        interceptors.addAll(catHttpClient.interceptors());
        interceptors.add(new RetryInterceptor());
        interceptors.add(new HeaderInterceptor());
        interceptors.add(new ConnectInterceptor());
        interceptors.add(new CallServiceInterceptor());

        Chain chain = new InterceptorChain(interceptors,this,null,0);
        return chain.proceed();
    }
```
5.Interceptor和chain的实现方式
5.1.Chain链条，每个链条中包含他在整个拦截器链的index，Call中任务开始的地方创建了第一个Chain并调用了他的proceed()，传入index=0，proceed()中，获取到index=0的拦截器，调用拦截器的interceptor方法，返回值response作为proceed()的返回值返回，同时创建好链条中下一个Chain，由下一个链条去处理index=1的拦截器.有几个拦截器就有几个Chain.
5.2.Chain链中Chain的返回值Response是他处理的Interceptor的返回值，Interceptor的参数是下一个index的Chain，返回值也是下一个Chain的返回值，这样一直往下取得是最后一个拦截器，即实际连网发送请求的CallServiceInterceptor的返回值.
```
public interface Interceptor {
    Response interceptor(Chain chain) throws IOException;
}
```
```
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
    public Response proceed(HttpConnection httpConnection) throws IOException {
        Interceptor interceptor = interceptors.get(index);
        Chain chain = new InterceptorChain(interceptors,call,httpConnection,index + 1);
        return interceptor.interceptor(chain);
    }
}
```
6.RetryInterceptor：自带的第一个拦截器实现请求重试，因为责任链的实现方式，所以直接取下一个Chain的返回值，这包含了后面所有拦截器的处理结果，如果有异常，表示请求过程需要重试，直接循环调用下一个Chain的proceed()
```
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
```
7.HeaderInterceptor：处理Header
```
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
```
8.ConnectInterceptor：实现连接复用,将每个请求封装成一个对象，包含有url，host，port等信息，一般的做法是，每个请求重复1打开连接-2写入请求-3读取响应-4释放连接这个过程，但经常的，同一个应用中，大部分请求都是对同一个host，port地址的请求，所以可以在步骤3后不进行4，让这个连接存在一段时间，下一次请求直接进行步骤2，避免频繁打开连接。需要注意的是连接的缓存时间和连接需要是长连接Keep-Alive的 
```
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
```
8.1.连接池的实现方式：每个连接保存一个最近使用的时间。这里创建了一个守护线程池用于回收连接，当有请求结束，连接被put到连接池中时，回收线程创建，调用clean()，clean()中判断所有连接的最短缓存时间还有多久以及回收超时的连接，wait后，会继续调用clean()。中间如果被重用，则当又put回来时更新最近使用时间，重新开始过程。clean()中如果连接池回收完了，则回收停止。put()时开始回收程序
```
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
```
9.CallServiceInterceptor：最核心的拦截器，使用socket实现请求，返回Response对象
```
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
```
10.生成Response后，经过每个拦截器处理后，最终返回到3.Call中内部类AsyncCall.run()，getResponse()返回值中,这里回收Callback的成功失败方法，通知Dispatcher请求完毕
```
    public void finished(Call.AsyncCall call) {
        L.e("Dispatcher:请求结束 移出正在运行队列，判断是否执行等待队列中的请求");
        synchronized (this) {
            runningAsyncCalls.remove(call);
            //判断是否执行等待队列中的请求
            promoteCalls();
        }
    }
```

















