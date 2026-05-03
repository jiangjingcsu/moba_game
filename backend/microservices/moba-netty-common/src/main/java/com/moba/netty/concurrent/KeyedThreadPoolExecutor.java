package com.moba.netty.concurrent;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class KeyedThreadPoolExecutor {

    private final ExecutorService[] executors;
    private final int poolSize;

    public KeyedThreadPoolExecutor(int poolSize, String threadNamePrefix) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be > 0, got: " + poolSize);
        }
        this.poolSize = poolSize;
        this.executors = new ExecutorService[poolSize];
        for (int i = 0; i < poolSize; i++) {
            executors[i] = Executors.newSingleThreadExecutor(
                    new NamedThreadFactory(threadNamePrefix + "-" + i));
        }
        log.info("KeyedThreadPoolExecutor已创建: prefix={}, poolSize={}", threadNamePrefix, poolSize);
    }

    public void execute(long key, Runnable task) {
        int index = indexForKey(key);
        executors[index].execute(task);
    }

    public <T> Future<T> submit(long key, Callable<T> task) {
        int index = indexForKey(key);
        return executors[index].submit(task);
    }

    public int indexForKey(long key) {
        return (int) ((key & Long.MAX_VALUE) % poolSize);
    }

    public void shutdown() {
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }
        log.info("KeyedThreadPoolExecutor已关闭");
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        boolean allTerminated = true;
        for (ExecutorService executor : executors) {
            if (!executor.awaitTermination(timeout, unit)) {
                allTerminated = false;
            }
        }
        return allTerminated;
    }

    public int getPoolSize() {
        return poolSize;
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-t" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            if (t.getUncaughtExceptionHandler() == null) {
                t.setUncaughtExceptionHandler((thread, throwable) ->
                        log.error("线程未捕获异常: thread={}", thread.getName(), throwable));
            }
            return t;
        }
    }
}
