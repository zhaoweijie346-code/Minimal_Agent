package com.zhaoweijie.minimalagent.runtime;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 按 Session 串行执行完整 Agent Loop，避免并发请求交叉覆盖消息和工具调用链。
 */
@Component
public class SessionExecutionCoordinator {

    /** 固定数量的锁分片，既约束同 Session 并发，也避免按 Session 建锁导致无界内存增长。 */
    private static final int LOCK_STRIPES = 64;

    /** 根据 Session 标识稳定映射的可重入锁分片。 */
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    /** 初始化全部锁分片。 */
    public SessionExecutionCoordinator() {
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    /**
     * 在目标 Session 对应的互斥区内执行一次完整请求。
     *
     * @param sessionId 已解析的 Session 标识
     * @param operation Agent Loop 操作
     * @param <T>       操作返回类型
     * @return Agent Loop 执行结果
     */
    public <T> T execute(String sessionId, Supplier<T> operation) {
        ReentrantLock lock = locks[Math.floorMod(sessionId.hashCode(), locks.length)];
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
