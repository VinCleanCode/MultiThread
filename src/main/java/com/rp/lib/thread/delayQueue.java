package com.rp.lib.thread;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

class News { //定义一个新闻类
    private String title;
    private String note;

    public News(String title, String note) {
        this.title = title;
        this.note = note;
    }

    @Override
    public String toString() {
        return "[新闻数据]title" + this.title + ",note=" + this.note;
    }
}

class DelayMsg<T> implements Delayed {
    private T msg; //设置要保存的数据
    private long expire;  // 设置缓存的失效时间
    private long delay;  //设置要保存的时间

    public DelayMsg(T item, long delay, TimeUnit unit) {
        this.msg = item;
        this.delay = TimeUnit.MILLISECONDS.convert(delay, unit);
        this.expire = System.currentTimeMillis() + this.delay;

    }

    public T getMsg() {
        return msg;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.expire - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return (int) (this.delay - this.getDelay(TimeUnit.MILLISECONDS));
    }
}

//定义一个缓存的操作类，该类之中需要用户设置保存的可以Key类型与value类型
class Cache<K, V> {
    //如果要实现多个线程的并发访问操作，必须考虑使用ConcurrentHashMap子类
    private ConcurrentHashMap<K, V> cacheObjectMap = new ConcurrentHashMap<>();
    private DelayQueue<DelayMsg<Pair>> delayQueue = new DelayQueue<>();

    private class Pair { //定义一个内部类，该类可以保存队列之中的K与V类型
        private K key;
        private V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    public Cache() {//如果要想清空不需要的缓存数据，则需要守护线程
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) { //守护线程要一直执行，当已经超时之后可以取出数据
                    DelayMsg<Pair> item = Cache.this.delayQueue.poll();//通过延迟队列获取数据
                    if (item != null) { //已经有数据超时了
                        Pair pair = item.getMsg();
                        Cache.this.cacheObjectMap.remove(pair.key, pair.value);
                    }
                }
            }
        }, "缓存守护线程");
        thread.setDaemon(true); //设置守护线程
        thread.start(); //启动守护线程
    }

    /**
     * 表示将要保存的数据写入缓存中个，如果一个对象重复保存了，则应该重置它的超时时间
     * @param key   要写入的k的内容
     * @param value 要写入的对象
     * @param time  保存的时间
     * @param unit  保存的时间单位
     */
    public void put(K key, V value, long time, TimeUnit unit) {
        //put()方法如果发现原来的key存在，则会用新的value替换掉旧的内容，同时返回旧的内容
        V oldValue = this.cacheObjectMap.put(key, value);
        if (oldValue != null) { //原来已经存储过此数据
            this.delayQueue.remove(key);
        }
        this.delayQueue.put(new DelayMsg<>(new Pair(key, value), time, unit));
    }

    public V get(K key) {
        return this.cacheObjectMap.get(key);
    }
}

public class delayQueue {
    public static void main(String[] args) throws Exception {
        Cache<String, News> cache = new Cache<>();
        cache.put("aaa", new News("aaa", "xxx"), 3, TimeUnit.SECONDS);
        cache.put("BBB", new News("BBB", "xxx"), 3, TimeUnit.SECONDS);
        System.out.println(cache.get("aaa"));
        System.out.println(cache.get("BBB"));
        TimeUnit.SECONDS.sleep(5);
        System.out.println(cache.get("aaa"));
        System.out.println(cache.get("BBB"));
    }
}