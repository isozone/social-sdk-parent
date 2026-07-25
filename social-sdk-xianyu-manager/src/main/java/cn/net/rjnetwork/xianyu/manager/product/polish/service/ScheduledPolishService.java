package cn.net.rjnetwork.xianyu.manager.product.polish.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.polish.mapper.ScheduledPolishLogMapper;
import cn.net.rjnetwork.xianyu.manager.product.polish.model.ScheduledPolishLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时擦亮服务 —— B4。
 * <p>定时扫账号在售商品，调 {@link XianyuApiFacade#getMyProducts} 触发服务端排序刷新，
 * 提升商品在闲鱼搜索排序。对标参考项目 polish_service.py。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫账号在售商品（status=ON_SALE），按账号分批；</li>
 *   <li>熔断器检查（账号风控冷却中则跳过）；</li>
 *   <li>调 facade.getMyProducts 触发服务端重排（闲鱼侧 SDK 无专门 polish API，
 *       走商品列表拉取链路触发排序刷新，与参考项目一致）；</li>
 *   <li>写批次日志（scheduled_polish_log）+ 明细（B9 batch_job_item, job_type=polish）+ 熔断记 success/failure。</li>
 * </ol>
 */
@Service
public class ScheduledPolishService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPolishService.class);
    private static final String JOB_TYPE = "polish";

    private final ProductMapper productMapper;
    private final AccountMapper accountMapper;
    private final ScheduledPolishLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;

    public ScheduledPolishService(ProductMapper productMapper,
                        AccountMapper accountMapper,
                        ScheduledPolishLogMapper logMapper,
                        CircuitBreakerService circuitBreaker,
                        BatchJobService batchJobService) {
        this.productMapper = productMapper;
        this.accountMapper = accountMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
    }

    /** 执行一次擦亮批次。由 PolishTask 定时调用，也可管理端手动触发。 */
    public Long runBatch(String triggerSource) {
        // 扫账号在售商品（ON_SALE）
        List<XianyuProduct> products = productMapper.selectList(new LambdaQueryWrapper<XianyuProduct>()
                .eq(XianyuProduct::getStatus, "ON_SALE"));
        ScheduledPolishLog batch = startLog(triggerSource, products.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, products.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (XianyuProduct product : products) {
            long t0 = System.currentTimeMillis();
            try {
                PolishResult r = polishOne(product);
                switch (r) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(product.getId()),
                                Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()),
                                "SUCCESS", System.currentTimeMillis() - t0, null, null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(product.getId()),
                                Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()),
                                "SKIPPED", System.currentTimeMillis() - t0, "skip: " + r.name(), null);
                    }
                    case FAILED -> {
                        failed++;
                        if (failureSummary.length() > 0) failureSummary.append("; ");
                        failureSummary.append(Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()))
                                .append(": polish failed");
                        batchJobService.recordItem(job.getId(), String.valueOf(product.getId()),
                                Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()),
                                "FAILED", System.currentTimeMillis() - t0, "polish failed", null);
                    }
                }
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append(Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()))
                        .append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(product.getId()),
                        Optional.ofNullable(product.getTitle()).orElse("product#" + product.getItemId()),
                        "FAILED", System.currentTimeMillis() - t0, reason, null);
            }
        }

        endLog(batch, success, failed, skipped, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", products.size(), success, failed, skipped));
        return batch.getId();
    }

    /** 对单商品执行擦亮。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PolishResult polishOne(XianyuProduct product) {
        Long accountId = product.getAccountId();
        if (accountId == null || !circuitBreaker.allowRequest(accountId, "POLISH")) {
            return PolishResult.SKIPPED;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null || "DISABLED".equals(account.getStatus())) {
            return PolishResult.SKIPPED;
        }
        try {
            // facade 通过构造器注入 cookie（无 setter），擦亮走 polishItem 逐商品触发服务端重排
            XianyuApiFacade facade = new XianyuApiFacade(account.getCookieHeader());
            String itemId = String.valueOf(product.getItemId());
            JsonNode resp = facade.polishItem(itemId);
            String ret = resp != null ? resp.path("ret").toString() : "";
            if (ret.contains("FAIL")) {
                circuitBreaker.recordFailure(accountId, "POLISH", "polishItem ret=" + ret);
                return PolishResult.FAILED;
            }
            // 标最近擦亮时间（便于诊断 + 防短期内重复擦亮）
            product.setUpdatedAt(LocalDateTime.now());
            productMapper.updateById(product);
            circuitBreaker.recordSuccess(accountId, "POLISH");
            return PolishResult.SUCCESS;
        } catch (Exception e) {
            circuitBreaker.recordFailure(accountId, "POLISH",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[B4] product {} polish failed: {}", product.getId(), e.getMessage());
            return PolishResult.FAILED;
        }
    }

    private ScheduledPolishLog startLog(String triggerSource, int total) {
        ScheduledPolishLog row = new ScheduledPolishLog();
        row.setTriggerSource(triggerSource);
        row.setTotalCount(total);
        row.setSuccessCount(0);
        row.setFailedCount(0);
        row.setSkippedCount(0);
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        logMapper.insert(row);
        return row;
    }

    private void endLog(ScheduledPolishLog batch, int success, int failed, int skipped, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        if (failureSummary != null && failureSummary.length() > 2000) failureSummary = failureSummary.substring(0, 2000);
        batch.setFailureSummary(failureSummary);
        logMapper.updateById(batch);
    }

    private enum PolishResult { SUCCESS, SKIPPED, FAILED }
}
