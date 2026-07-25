package cn.net.rjnetwork.xianyu.manager.order.rate.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuTradeAuxApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.AutoRateConfigMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRateLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.AutoRateConfig;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.ScheduledRateLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动评价服务 —— B2。
 * <p>定时扫「已收货 N 天（delayDays）且卖家未评」的订单，按 {@link AutoRateConfig} 配置调
 * {@link XianyuTradeAuxApiService#reviewOrder} 评好评（GOOD/NORMAL/BAD），话术模板支持占位符替换。
 * 对标参考项目 auto_rate_service.py。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫 XianyuOrder：status=COMPLETED 且收货已超 delayDays 且 deliverContent 含「已收货」标记（或按 autoReceiptAt 推算）；</li>
 *   <li>拉账号 AutoRateConfig（无则跳过），检商品白名单/买家黑名单；</li>
 *   <li>话术模板替换 {itemTitle}/{buyerNick}/{accountName} 占位符；</li>
 *   <li>调 reviewOrder(orderId, rateLevel, feedback) 评好评；</li>
 *   <li>写批次日志（scheduled_rate_log）+ 明细（B9 batch_job_item, job_type=auto_rate）+ 熔断记 success/failure。</li>
 * </ol>
 */
@Service
public class AutoRateService {

    private static final Logger log = LoggerFactory.getLogger(AutoRateService.class);
    private static final String JOB_TYPE = "auto_rate";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_FEEDBACK =
            "买家很爽快，沟通顺畅，{itemTitle} 已收到，感谢支持~";

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final AutoRateConfigMapper configMapper;
    private final ScheduledRateLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;

    public AutoRateService(OrderMapper orderMapper,
                           AccountMapper accountMapper,
                           AutoRateConfigMapper configMapper,
                           ScheduledRateLogMapper logMapper,
                           CircuitBreakerService circuitBreaker,
                           BatchJobService batchJobService) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.configMapper = configMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
    }

    /** 执行一次自动评价批次。由 AutoRateTask 定时调用，也可管理端手动触发。 */
    public Long runBatch(String triggerSource) {
        // 扫已收货（COMPLETED）且未评的订单
        List<XianyuOrder> candidates = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getStatus, "COMPLETED")
                .isNotNull(XianyuOrder::getAutoReceiptAt));
        ScheduledRateLog batch = startLog(triggerSource, candidates.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (XianyuOrder order : candidates) {
            long t0 = System.currentTimeMillis();
            try {
                RateResult r = rateOne(order);
                switch (r) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "SUCCESS", System.currentTimeMillis() - t0, null, null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "SKIPPED", System.currentTimeMillis() - t0, "skip: " + r.name(), null);
                    }
                    case FAILED -> {
                        failed++;
                        if (failureSummary.length() > 0) failureSummary.append("; ");
                        failureSummary.append(Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()))
                                .append(": rate failed");
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "FAILED", System.currentTimeMillis() - t0, "rate failed", null);
                    }
                }
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append(Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()))
                        .append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                        Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                        "FAILED", System.currentTimeMillis() - t0, reason, null);
            }
        }

        endLog(batch, success, failed, skipped, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", candidates.size(), success, failed, skipped));
        return batch.getId();
    }

    /** 对单订单执行评价。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateResult rateOne(XianyuOrder order) {
        if (!circuitBreaker.allowRequest(order.getAccountId(), "AUTO_RATE")) {
            return RateResult.SKIPPED;
        }
        AutoRateConfig config = configMapper.selectEffectiveForAccount(order.getAccountId());
        if (config == null || config.getEnabled() == null || config.getEnabled() == 0) {
            return RateResult.SKIPPED;
        }
        // 延后天数未到则跳过
        int delayDays = Optional.ofNullable(config.getDelayDays()).orElse(1);
        if (order.getAutoReceiptAt() != null
                && order.getAutoReceiptAt().isAfter(LocalDateTime.now().minusDays(delayDays))) {
            return RateResult.SKIPPED;
        }
        // 商品白名单/买家黑名单过滤
        if (!isProductAllowed(config, order.getProductId())) return RateResult.SKIPPED;
        if (isBuyerBlacklisted(config, order.getBuyerId())) return RateResult.SKIPPED;

        XianyuAccount account = accountMapper.selectById(order.getAccountId());
        if (account == null || "DISABLED".equals(account.getStatus())) {
            return RateResult.SKIPPED;
        }
        String rateLevel = Optional.ofNullable(config.getRateLevel()).orElse("GOOD");
        String feedback = renderFeedback(config, order, account);
        try {
            XianyuMtopApiClient apiClient = new XianyuMtopApiClient(account.getCookieHeader());
            if (account.getImCookieHeader() != null && !account.getImCookieHeader().isBlank()) {
                apiClient.setImCookieHeader(account.getImCookieHeader());
            }
            XianyuTradeAuxApiService tradeAux = new XianyuTradeAuxApiService(apiClient);
            JsonNode resp = tradeAux.reviewOrder(String.valueOf(order.getOrderId()), rateLevel, feedback);
            // 闲鱼侧 reviewOrder 成功通常返回 ret=["SUCCESS::调用成功"]
            String ret = resp != null ? resp.path("ret").toString() : "";
            if (ret.contains("FAIL")) {
                circuitBreaker.recordFailure(order.getAccountId(), "AUTO_RATE", "reviewOrder ret=" + ret);
                return RateResult.FAILED;
            }
            // 标订单已评（status 推 CLOSED 或留 COMPLETED 但清 autoReceiptAt 防重复评）
            order.setAutoReceiptAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            // 更新 config.lastRunAt
            config.setLastRunAt(LocalDateTime.now());
            configMapper.updateById(config);
            circuitBreaker.recordSuccess(order.getAccountId(), "AUTO_RATE");
            return RateResult.SUCCESS;
        } catch (Exception e) {
            circuitBreaker.recordFailure(order.getAccountId(), "AUTO_RATE",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[B2] order {} rate failed: {}", order.getOrderId(), e.getMessage());
            return RateResult.FAILED;
        }
    }

    /** 话术模板占位符替换。 */
    private String renderFeedback(AutoRateConfig config, XianyuOrder order, XianyuAccount account) {
        String tpl = (config.getFeedbackTemplate() == null || config.getFeedbackTemplate().isBlank())
                ? DEFAULT_FEEDBACK : config.getFeedbackTemplate();
        return tpl.replace("{itemTitle}", Optional.ofNullable(order.getItemTitle()).orElse(""))
                .replace("{buyerNick}", Optional.ofNullable(order.getCounterpartyName()).orElse(""))
                .replace("{accountName}", Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName()));
    }

    private boolean isProductAllowed(AutoRateConfig config, Long productId) {
        if (config.getProductWhitelist() == null || config.getProductWhitelist().isBlank()) return true;
        try {
            JsonNode arr = JSON.readTree(config.getProductWhitelist());
            String pid = String.valueOf(productId);
            for (JsonNode n : arr) if (pid.equals(n.asText())) return true;
            return false;
        } catch (Exception e) { return true; } // 解析失败兜底放行
    }

    private boolean isBuyerBlacklisted(AutoRateConfig config, String buyerId) {
        if (config.getBuyerBlacklist() == null || config.getBuyerBlacklist().isBlank()) return false;
        if (buyerId == null) return false;
        try {
            JsonNode arr = JSON.readTree(config.getBuyerBlacklist());
            for (JsonNode n : arr) if (buyerId.equals(n.asText())) return true;
            return false;
        } catch (Exception e) { return false; }
    }

    private ScheduledRateLog startLog(String triggerSource, int total) {
        ScheduledRateLog row = new ScheduledRateLog();
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

    private void endLog(ScheduledRateLog batch, int success, int failed, int skipped, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        if (failureSummary != null && failureSummary.length() > 2000) failureSummary = failureSummary.substring(0, 2000);
        batch.setFailureSummary(failureSummary);
        logMapper.updateById(batch);
    }

    private enum RateResult { SUCCESS, SKIPPED, FAILED }
}
