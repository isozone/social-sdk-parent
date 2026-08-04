package cn.net.rjnetwork.xianyu.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * 用于 {@link org.springframework.scheduling.annotation.Async} 注解，给同步商品等长耗时后台任务提供独立线程池，
 * 避免挤占 Tomcat 请求线程。
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String SYNC_EXECUTOR = "syncTaskExecutor";
    /** AI 自动回复专用线程池 bean 名（与同步池隔离，避免 AI 雌塞挤占消息同步线程） */
    public static final String AUTO_REPLY_EXECUTOR = "autoReplyExecutor";

    @Bean(name = {SYNC_EXECUTOR, "taskExecutor"})
    public ThreadPoolTaskExecutor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：保持常驻的线程
        executor.setCorePoolSize(4);
        // 最大线程数：高峰期可扩容到的上限
        executor.setMaxPoolSize(8);
        // 任务排队队列容量，超过队列+maxPoolSize 会走拒绝策略
        executor.setQueueCapacity(50);
        // 线程名前缀，方便日志里区分
        executor.setThreadNamePrefix("sync-task-");
        // 非核心线程空闲存活秒数
        executor.setKeepAliveSeconds(120);
        // 拒绝策略：由调用方线程跑（降级同步），保证任务一定被执行，不会丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待正在跑的任务跑完再关
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 关键：@Async 未指定 executor 时走这里，避免 Spring 回退到 SimpleAsyncTaskExecutor 无限创建线程。
     */
    @Override
    public Executor getAsyncExecutor() {
        return syncTaskExecutor();
    }

    /**
     * AI 自动回复专用线程池：与消息同步池隔离。
     * AI 调用慢（数秒级），若与 syncTaskExecutor 共池会占住消息同步线程导致链路阻塞。
     * 本池独立队列 + 独立拒绝策略（CallerRuns），不挤占消息同步。
     */
    @Bean(name = AUTO_REPLY_EXECUTOR)
    public ThreadPoolTaskExecutor autoReplyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("auto-reply-");
        executor.setKeepAliveSeconds(120);
        // 拒绝策略由调用方线程跑（降级同步），保证回复一定被处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
