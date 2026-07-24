package cn.net.rjnetwork.xianyu.manager.batch.service;

import cn.net.rjnetwork.xianyu.manager.batch.mapper.BatchJobItemMapper;
import cn.net.rjnetwork.xianyu.manager.batch.mapper.BatchJobMapper;
import cn.net.rjnetwork.xianyu.manager.batch.model.BatchJob;
import cn.net.rjnetwork.xianyu.manager.batch.model.BatchJobItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批次日志通用服务 —— B9 基础设施。
 * <p>所有定时任务的「一次执行」都通过 {@link #startBatch} 开启批次、
 * {@link #recordItem} 记录明细、{@link #endBatch} 收尾。避免每个任务重复造表/Service。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>BatchJob/BatchJobItem 写库走 Propagation.REQUIRES_NEW，保证批次日志不随业务事务回滚丢失</li>
 *   <li>失败原因聚合存到 BatchJob.failureSummary，便于管理端一眼定位</li>
 *   <li>分页查询支持 jobType/status 过滤，前端批次列表通用</li>
 * </ul>
 */
@Service
public class BatchJobService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobService.class);

    private final BatchJobMapper batchMapper;
    private final BatchJobItemMapper itemMapper;

    public BatchJobService(BatchJobMapper batchMapper, BatchJobItemMapper itemMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
    }

    /** 开启一个新批次并返回主键 id，后续 recordItem 用它关联。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchJob startBatch(String jobType, String jobCode, String triggerSource, int totalCount) {
        BatchJob job = new BatchJob();
        job.setJobType(jobType);
        job.setJobCode(jobCode);
        job.setTriggerSource(triggerSource);
        job.setStatus("RUNNING");
        job.setTotalCount(totalCount);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setSkippedCount(0);
        job.setStartedAt(LocalDateTime.now());
        batchMapper.insert(job);
        return job;
    }

    /** 记录一条明细，并累加到父批次的计数。failureReason 非空时聚合到 failureSummary（最多 2000 字）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordItem(Long batchId, String itemKey, String itemLabel,
                           String status, Long durationMs, String failureReason, String detail) {
        BatchJobItem item = new BatchJobItem();
        item.setBatchId(batchId);
        item.setItemKey(itemKey);
        item.setItemLabel(itemLabel);
        item.setStatus(status);
        item.setDurationMs(durationMs);
        item.setFailureReason(failureReason);
        item.setDetail(detail);
        item.setStartedAt(LocalDateTime.now());
        item.setEndedAt(LocalDateTime.now());
        itemMapper.insert(item);

        // 增量更新父批次计数，避免最后一次性刷新（中途崩溃也能看到进度）
        BatchJob parent = batchMapper.selectById(batchId);
        if (parent == null) return;
        switch (status) {
            case "SUCCESS" -> parent.setSuccessCount(Optional.ofNullable(parent.getSuccessCount()).orElse(0) + 1);
            case "FAILED" -> {
                parent.setFailedCount(Optional.ofNullable(parent.getFailedCount()).orElse(0) + 1);
                if (failureReason != null && !failureReason.isBlank()) {
                    String existing = Optional.ofNullable(parent.getFailureSummary()).orElse("");
                    String append = (existing.isEmpty() ? "" : existing + "; ") + failureReason;
                    parent.setFailureSummary(append.length() > 2000 ? append.substring(0, 2000) : append);
                }
            }
            case "SKIPPED", "RETRYING" -> parent.setSkippedCount(Optional.ofNullable(parent.getSkippedCount()).orElse(0) + 1);
            default -> {}
        }
        batchMapper.updateById(parent);
    }

    /** 收尾批次：写 endedAt、summary、终态。partial=true 表示有部分失败/跳过。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void endBatch(Long batchId, boolean partial, boolean failed, String summary) {
        BatchJob parent = batchMapper.selectById(batchId);
        if (parent == null) return;
        parent.setEndedAt(LocalDateTime.now());
        parent.setSummary(summary);
        if (failed) {
            parent.setStatus("FAILED");
        } else if (partial) {
            parent.setStatus("PARTIAL");
        } else {
            parent.setStatus("SUCCESS");
        }
        batchMapper.updateById(parent);
    }

    /** 便捷封装：跑完一个 Runnable 并自动记录条目耗时与异常。 */
    public void runItem(Long batchId, String itemKey, String itemLabel, Runnable action) {
        long t0 = System.currentTimeMillis();
        try {
            action.run();
            recordItem(batchId, itemKey, itemLabel, "SUCCESS", System.currentTimeMillis() - t0, null, null);
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            recordItem(batchId, itemKey, itemLabel, "FAILED", System.currentTimeMillis() - t0, reason, null);
            log.warn("[BatchJob {}] item {} failed: {}", batchId, itemKey, reason);
        }
    }

    /** 分页查询批次列表，可选 jobType/status 过滤。 */
    public Page<BatchJob> listBatches(int page, int size, String jobType, String status) {
        Page<BatchJob> p = new Page<>(page, size);
        LambdaQueryWrapper<BatchJob> wrapper = new LambdaQueryWrapper<>();
        if (jobType != null && !jobType.isBlank()) wrapper.eq(BatchJob::getJobType, jobType);
        if (status != null && !status.isBlank()) wrapper.eq(BatchJob::getStatus, status);
        wrapper.orderByDesc(BatchJob::getStartedAt);
        return batchMapper.selectPage(p, wrapper);
    }

    /** 查单批次的明细列表（按时间升序，与执行顺序一致）。 */
    public List<BatchJobItem> listItems(Long batchId) {
        LambdaQueryWrapper<BatchJobItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BatchJobItem::getBatchId, batchId).orderByAsc(BatchJobItem::getStartedAt);
        return itemMapper.selectList(wrapper);
    }

    /** 批次摘要统计，给管理端总览用。 */
    public Map<String, Object> summaryByJobType() {
        List<BatchJob> recent = batchMapper.selectList(new LambdaQueryWrapper<BatchJob>()
                .ge(BatchJob::getStartedAt, LocalDateTime.now().minusDays(7)));
        Map<String, AtomicInteger> byType = new LinkedHashMap<>();
        Map<String, AtomicInteger> success = new LinkedHashMap<>();
        Map<String, AtomicInteger> failed = new LinkedHashMap<>();
        for (BatchJob j : recent) {
            byType.computeIfAbsent(j.getJobType(), k -> new AtomicInteger()).incrementAndGet();
            if ("SUCCESS".equals(j.getStatus())) success.computeIfAbsent(j.getJobType(), k -> new AtomicInteger()).incrementAndGet();
            if ("FAILED".equals(j.getStatus())) failed.computeIfAbsent(j.getJobType(), k -> new AtomicInteger()).incrementAndGet();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", recent.size());
        result.put("byType", byType);
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }
}
