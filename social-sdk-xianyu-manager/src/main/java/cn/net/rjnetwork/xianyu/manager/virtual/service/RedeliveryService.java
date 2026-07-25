package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动补发货服务 —— A9。
 * <p>定时扫「FAILED / SKIPPED 但未超重试上限」的 virtual_ship_task，按指数退避重新执行 A8 主链路。
 * 对标参考项目 redelivery_service.py：有限次重试（默认 5 次）+ 批次日志 + 超限转人工。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫 retryCount &lt; maxRetry（默认 5）的 FAILED/SKIPPED task，且 nextRunAt 已到（退避间隔）；</li>
 *   <li>retryCount++，调 {@link AutoShipService#processShipTask} 重跑主链路；</li>
 *   <li>结果仍失败：retryCount 未超上限则按指数退避（30s/2m/10m/1h/6h）排下次，超上限则标 RETRY_EXHAUSTED 并推人工；</li>
 *   <li>结果成功：标 SUCCESS，清 errorMessage；</li>
 *   <li>批次日志复用 B9 BatchJobService（job_type=redelivery）。</li>
 * </ol>
 */
@Service
public class RedeliveryService {

    private static final Logger log = LoggerFactory.getLogger(RedeliveryService.class);
    private static final String JOB_TYPE = "redelivery";
    private static final int DEFAULT_MAX_RETRY = 5;
    /** 指数退避间隔（秒）：30s / 2m / 10m / 1h / 6h */
    private static final int[] BACKOFF_SECONDS = {30, 120, 600, 3600, 21600};

    private final VirtualShipTaskMapper taskMapper;
    private final AutoShipService autoShipService;
    private final BatchJobService batchJobService;

    public RedeliveryService(VirtualShipTaskMapper taskMapper,
                              AutoShipService autoShipService,
                              BatchJobService batchJobService) {
        this.taskMapper = taskMapper;
        this.autoShipService = autoShipService;
        this.batchJobService = batchJobService;
    }

    /** 执行一次补发批次。由 RedeliveryTask 定时调用，也可管理端手动触发。 */
    public Long runBatch(String triggerSource) {
        List<VirtualShipTask> candidates = collectCandidates();
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        int success = 0, failed = 0, skipped = 0, exhausted = 0;
        for (VirtualShipTask task : candidates) {
            long t0 = System.currentTimeMillis();
            try {
                RenewOutcome outcome = retryOne(task);
                switch (outcome) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(task.getOrderId()),
                                "order#" + task.getOrderId(), "SUCCESS",
                                System.currentTimeMillis() - t0, null, null);
                    }
                    case RESCHEDULED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(task.getOrderId()),
                                "order#" + task.getOrderId(), "SKIPPED",
                                System.currentTimeMillis() - t0,
                                "rescheduled: " + Optional.ofNullable(task.getErrorMessage()).orElse(""), null);
                    }
                    case EXHAUSTED -> {
                        exhausted++;
                        batchJobService.recordItem(job.getId(), String.valueOf(task.getOrderId()),
                                "order#" + task.getOrderId(), "FAILED",
                                System.currentTimeMillis() - t0,
                                "retry exhausted", null);
                    }
                }
            } catch (Exception e) {
                failed++;
                batchJobService.recordItem(job.getId(), String.valueOf(task.getOrderId()),
                        "order#" + task.getOrderId(), "FAILED",
                        System.currentTimeMillis() - t0, e.getMessage(), null);
            }
        }
        boolean partial = failed > 0 || skipped > 0 || exhausted > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d exhausted=%d",
                        candidates.size(), success, failed, skipped, exhausted));
        return job.getId();
    }

    /** 对单个 task 执行补发重试。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewOutcome retryOne(VirtualShipTask task) {
        int retry = Optional.ofNullable(task.getRetryCount()).orElse(0);
        int maxRetry = Optional.ofNullable(task.getMaxRetry()).orElse(DEFAULT_MAX_RETRY);
        if (retry >= maxRetry) {
            task.setStatus("RETRY_EXHAUSTED");
            task.setErrorMessage("重试耗尽（" + retry + "/" + maxRetry + "），转人工介入");
            task.setProcessedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            log.warn("[A9] order {} retry exhausted ({}/{})", task.getOrderId(), retry, maxRetry);
            return RenewOutcome.EXHAUSTED;
        }
        // retryCount++ 后重跑主链路
        task.setRetryCount(retry + 1);
        task.setStatus("PENDING");
        taskMapper.updateById(task);
        try {
            autoShipService.processShipTask(task);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            taskMapper.updateById(task);
        }
        String st = Optional.ofNullable(task.getStatus()).orElse("FAILED");
        if ("SUCCESS".equals(st)) {
            task.setErrorMessage(null);
            taskMapper.updateById(task);
            return RenewOutcome.SUCCESS;
        }
        // 仍失败：按指数退避排下次重试（未超上限）
        if (retry + 1 < maxRetry) {
            int backoffIdx = Math.min(retry, BACKOFF_SECONDS.length - 1);
            LocalDateTime next = LocalDateTime.now().plusSeconds(BACKOFF_SECONDS[backoffIdx]);
            task.setExecuteAt(next);
            task.setStatus("FAILED");
            taskMapper.updateById(task);
            return RenewOutcome.RESCHEDULED;
        }
        // 超上限
        task.setStatus("RETRY_EXHAUSTED");
        task.setErrorMessage("重试耗尽（" + (retry + 1) + "/" + maxRetry + "），转人工介入");
        task.setProcessedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return RenewOutcome.EXHAUSTED;
    }

    /** 收集待补发 task：FAILED/SKIPPED 且 retryCount < maxRetry 且 executeAt 已到。 */
    private List<VirtualShipTask> collectCandidates() {
        return taskMapper.selectList(new LambdaQueryWrapper<VirtualShipTask>()
                .in(VirtualShipTask::getStatus, "FAILED", "SKIPPED")
                .and(w -> w.isNull(VirtualShipTask::getExecuteAt)
                        .or().le(VirtualShipTask::getExecuteAt, LocalDateTime.now())));
    }

    public enum RenewOutcome { SUCCESS, RESCHEDULED, EXHAUSTED }
}
