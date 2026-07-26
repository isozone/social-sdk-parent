package cn.net.rjnetwork.xianyu.manager.notify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通道级速率限制（令牌桶）。每个通道一个桶，按每分钟令牌数均匀补充。
 * limit <= 0 表示不限制。令牌数上限 = 每分钟限额，避免突发。
 */
@Component
public class SendRateLimiter {

    @Value("${notify.rate-limit-per-minute:60}")
    private int defaultPerMinute;

    /** 通道桶最大保留数，避免通道 ID 异常增长时长期占用内存。 */
    @Value("${notify.rate-limit-max-buckets:512}")
    private int maxBuckets;

    /** 空闲多久后清理桶。 */
    @Value("${notify.rate-limit-idle-millis:1800000}")
    private long idleMillis;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试获取一个发送令牌。
     * @param channelId    通道 ID
     * @param channelLimit 通道级每分钟上限（0 或未配置则用全局默认）
     */
    public boolean tryAcquire(long channelId, int channelLimit) {
        int limit = channelLimit > 0 ? channelLimit : defaultPerMinute;
        if (limit <= 0) return true; // 不限制
        if (buckets.size() > maxBuckets) {
            cleanupIdleBuckets();
        }
        TokenBucket b = buckets.computeIfAbsent("ch:" + channelId, k -> new TokenBucket(limit));
        return b.tryAcquire();
    }

    @Scheduled(fixedDelayString = "${notify.rate-limit-cleanup-delay-ms:300000}")
    public void cleanupIdleBuckets() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TokenBucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TokenBucket> entry = it.next();
            if (now - entry.getValue().lastAccessMillis() > idleMillis) {
                it.remove();
            }
        }
        if (buckets.size() <= maxBuckets) {
            return;
        }
        int remove = buckets.size() - maxBuckets;
        it = buckets.entrySet().iterator();
        while (remove-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    public int bucketCount() {
        return buckets.size();
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastNanos;
        private volatile long lastAccessMillis;

        TokenBucket(int perMinute) {
            this.capacity = perMinute;
            this.refillPerSecond = perMinute / 60.0;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
            this.lastAccessMillis = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            lastAccessMillis = System.currentTimeMillis();
            long now = System.nanoTime();
            double elapsedSec = (now - lastNanos) / 1_000_000_000.0;
            if (elapsedSec > 0) {
                tokens = Math.min(capacity, tokens + elapsedSec * refillPerSecond);
                lastNanos = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        long lastAccessMillis() {
            return lastAccessMillis;
        }
    }
}
