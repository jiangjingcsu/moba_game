package com.moba.battleserver.util;

import java.util.ArrayDeque;
import java.util.function.Supplier;

public class ObjectPool<T> {
    private final ArrayDeque<T> pool;
    private final Supplier<T> factory;
    private final int maxSize;
    private int createdCount;

    public ObjectPool(Supplier<T> factory, int initialSize, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.pool = new ArrayDeque<>(initialSize);
        this.createdCount = 0;

        for (int i = 0; i < initialSize; i++) {
            pool.add(factory.get());
            createdCount++;
        }
    }

    public T acquire() {
        T obj = pool.poll();
        if (obj == null) {
            if (createdCount < maxSize) {
                obj = factory.get();
                createdCount++;
            } else {
                obj = factory.get();
            }
        }
        return obj;
    }

    public void release(T obj) {
        if (obj != null && pool.size() < maxSize) {
            pool.offer(obj);
        }
    }

    public int size() {
        return pool.size();
    }

    public int getCreatedCount() {
        return createdCount;
    }
}
