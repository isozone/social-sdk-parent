package cn.net.rjnetwork.xianyu.manager.order.rate.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.RedFlowerConfigMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRedFlowerLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.RedFlowerConfig;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.ScheduledRedFlowerLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 求小红花服务 —— B3。
 * <p>定时给买家送红花提信誉，对标参考项目 red_flower_service.py。
 * 闲鱼侧送红花走 {@link XianyuApiFacade#sendRedFlower}（mtop.taobao.idlemessage.red.flower）。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫账号已成交订单（COMPLETED）的买家，拉 {@link RedFlowerConfig} 配置；</li>
 *   <li>检买家白名单 + 今日送花上限（防风控盯上）；</li>
 *   <li>调 sendRedFlower(buyerId, targetType) 送花；</li>
 *   <li>写批次日志（scheduled_red_flower_log）+ 明细（B9 batch_job_item, job_type=red_flower）+ 熔断记 success/failure。</li>
 * </ol>
 */
@Service
public class RedFlowerService {

    private static final Logger log = LoggerFactory.getLogger(RedFlowerService.class);
    private static final String JOB_TYPE = "red_flower";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final RedFlowerConfigMapper configMapper;
    private final ScheduledRedFlowerLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;
    private final XianyuApiFacade apiFacade;

    public RedFlowerService(OrderMapper orderMapper,
                           AccountMapper accountMapper,
                           RedFlowerConfigMapper configMapper,
                           ScheduledRedFlowerLogMapper logMapper,
                           CircuitBreakerService circuitBreaker,
                           BatchJobService batchJobService,
                           XianyuApiFacade apiFacade) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.configMapper = configMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
        this.apiFacade = apiFacade;
    }

    /** 执行一次求红花批次。由 RedFlowerTask 定时调用，也可管理端手动触发。 */
    public Long runBatch(String triggerSource) {
        // 扫已成交订单买家（COMPLETED），按 accountId 分组送花
        List<XianyuOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getStatus, "COMPLETED")
                .isNotNull(XianyuOrder::getBuyerId));
        ScheduledRedFlowerLog batch = startLog(triggerSource, orders.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, orders.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (XianyuOrder order : orders) {
            long t0 = System.currentTimeMillis();
            try {
                RateResult r = sendOne(order);
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
                                .append(": send flower failed");
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "FAILED", System.currentTimeMillis() - t0, "send flower failed", null);
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
                String.format("total=%d success=%d failed=%d skipped=%d", orders.size(), success, failed, skipped));
        return batch.getId();
    }

    /** 对单订单买家送红花。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateResult sendOne(XianyuOrder order) {
        if (!circuitBreaker.allowRequest(order.getAccountId(), "RED_FLOWER")) {
            return RateResult.SKIPPED;
        }
        RedFlowerConfig config = configMapper.selectEffectiveForAccount(order.getAccountId());
        if (config == null || config.getEnabled() == null || config.getEnabled() == 0) {
            return RateResult.SKIPPED;
        }
        // 今日计数 reset（跨日清零）
        LocalDate today = LocalDate.now();
        if (config.getTodayDate() == null
                || !config.getTodayDate().toLocalDate().equals(today)) {
            config.setTodaySentCount(0);
            config.setTodayDate(LocalDateTime.now());
        }
        int limit = Optional.ofNullable(config.getDailyLimit()).orElse(20);
        if (Optional.ofNullable(config.getTodaySentCount()).orElse(0) >= limit) {
            return RateResult.SKIPPED;
        }
        // 买家白名单过滤
        if (!isBuyerAllowed(config, order.getBuyerId())) return RateResult.SKIPPED;

        XianyuAccount account = accountMapper.selectById(order.getAccountId());
        if (account == null || "DISABLED".equals(account.getStatus())) {
            return RateResult.SKIPPED;
        }
        String targetType = Optional.ofNullable(config.getTargetType()).orElse("buyer");
        try {
            // facade 通过构造器注入 cookie（无 setter），im cookie 暂不传入（送红花走 IM 镜像链路自有 cookie 吸收逻辑）
            XianyuApiFacade facade = new XianyuApiFacade(account.getCookieHeader());
            JsonNode resp = facade.sendRedFlower(order.getBuyerId(), targetType);
            String ret = resp != null ? resp.path("ret").toString() : "";
            if (ret.contains("FAIL")) {
                circuitBreaker.recordFailure(order.getAccountId(), "RED_FLOWER", "sendRedFlower ret=" + ret);
                return RateResult.FAILED;
            }
            // 累今日计数 + 更新 config.lastRunAt
            config.setTodaySentCount(Optional.ofNullable(config.getTodaySentCount()).orElse(0) + 1);
            config.setLastRunAt(LocalDateTime.now());
            configMapper.updateById(config);
            circuitBreaker.recordSuccess(order.getAccountId(), "RED_FLOWER");
            return RateResult.SUCCESS;
        } catch (Exception e) {
            circuitBreaker.recordFailure(order.getAccountId(), "RED_FLOWER",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[B3] order {} send flower failed: {}", order.getOrderId(), e.getMessage());
            return RateResult.FAILED;
        }
    }

    private boolean isBuyerAllowed(RedFlowerConfig config, String buyerId) {
        if (config.getBuyerWhitelist() == null || config.getBuyerWhitelist().isBlank()) return true;
        if (buyerId == null) return false;
        try {
            JsonNode arr = JSON.readTree(config.getBuyerWhitelist());
            for (JsonNode n : arr) if (buyerId.equals(n.asText())) return true;
            return false;
        } catch (Exception e) { return true; }
    }

    private ScheduledRedFlowerLog startLog(String triggerSource, int total) {
        ScheduledRedFlowerLog row = new ScheduledRedFlowerLog();
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

    private void endLog(ScheduledRedFlowerLog batch, int success, int failed, int skipped, String failureSummary) {
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
