package com.moba.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;

    private static volatile SnowflakeIdGenerator defaultInstance;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId超出范围 [0, " + MAX_WORKER_ID + "], 当前值: " + workerId);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "datacenterId超出范围 [0, " + MAX_DATACENTER_ID + "], 当前值: " + datacenterId);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("SnowflakeIdGenerator初始化: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    public static synchronized SnowflakeIdGenerator getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new SnowflakeIdGenerator(1, 1);
        }
        return defaultInstance;
    }

    public static synchronized void initDefault(long workerId, long datacenterId) {
        if (defaultInstance != null) {
            log.warn("默认SnowflakeIdGenerator已初始化, 忽略重复初始化");
            return;
        }
        defaultInstance = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                try {
                    wait(offset << 1);
                    currentTimestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (currentTimestamp < lastTimestamp) {
                throw new IllegalStateException(
                        "时钟回拨: lastTimestamp=" + lastTimestamp + ", currentTimestamp=" + currentTimestamp);
            }
        }

        if (currentTimestamp == lastTimestamp) {
            long currentSeq = sequence.incrementAndGet() & SEQUENCE_MASK;
            if (currentSeq == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence.set(0L);
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | (sequence.get() & SEQUENCE_MASK);
    }

    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    public static long parseId(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            throw new IllegalArgumentException("ID字符串不能为空");
        }
        return Long.parseLong(idStr);
    }

    public static String formatId(long id) {
        return String.valueOf(id);
    }
}
