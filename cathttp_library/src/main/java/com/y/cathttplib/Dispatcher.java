package com.y.cathttplib;

import com.y.cathttplib.util.L;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Dispatcher {

    //最多同时请求
    int maxRequests;
    int maxRequestsPerHost;

    //线程池，发送异步请求
    private ExecutorService executorService;


    //等待执行队列
    private final Deque<Call.AsyncCall> readyAsyncCalls = new ArrayDeque<>();

    //正在执行队列
    private final Deque<Call.AsyncCall> runningAsyncCalls = new ArrayDeque<>();

    public Dispatcher() {
        this(64, 2);
    }

    public Dispatcher(int maxRequests, int maxRequestsPerHost) {
        this.maxRequests = maxRequests;
        this.maxRequestsPerHost = maxRequestsPerHost;
    }

    /**
     *    1、corePoolSize：线程池中核心线程数的最大值
     *    2、maximumPoolSize：线程池中能拥有最多线程数
     *    3、keepAliveTime：表示空闲线程的存活时间  60秒
     *    4、表示keepAliveTime的单位。
     *    5、workQueue：它决定了缓存任务的排队策略。
     *      SynchronousQueue<Runnable>：此队列中不缓存任何一个任务。向线程池提交任务时，
     *      如果没有空闲线程来运行任务，则入列操作会阻塞。当有线程来获取任务时，
     *      出列操作会唤醒执行入列操作的线程。
     *    6、指定创建线程的工厂
     */
    private synchronized ExecutorService executorService() {
        if (executorService == null) {
            ThreadFactory threadFactory = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread result = new Thread(runnable, "OkHttp Dispatcher");
                    return result;
                }
            };

            executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(), threadFactory);
        }
        return executorService;
    }

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

    /**
     * 同一host 的 同时请求数
     */
    private int runningCallsForHost(Call.AsyncCall call) {
        int count = 0;
        //如果执行这个请求，则相同的host数量是result
        for (Call.AsyncCall c : runningAsyncCalls) {
            if (c.host().equals(call.host())) {
                count++;
            }
        }
        return count;
    }

    /**
     *请求结束 移出正在运行队列
     *并判断是否执行等待队列中的请求
     */
    public void finished(Call.AsyncCall call) {
        L.e("Dispatcher:请求结束 移出正在运行队列，判断是否执行等待队列中的请求");
        synchronized (this) {
            runningAsyncCalls.remove(call);
            //判断是否执行等待队列中的请求
            promoteCalls();
        }
    }

    private void promoteCalls(){
        if(readyAsyncCalls.isEmpty()){
            return;
        }

        Iterator<Call.AsyncCall> iterators = readyAsyncCalls.iterator();
        while (iterators.hasNext()){
            if(runningAsyncCalls.size() >= maxRequests){
                return;
            }
            Call.AsyncCall call = iterators.next();
            if(runningCallsForHost(call) >= maxRequestsPerHost){
                continue;
            }

            iterators.remove();
            runningAsyncCalls.add(call);
            executorService().execute(call);

        }
    }
}
