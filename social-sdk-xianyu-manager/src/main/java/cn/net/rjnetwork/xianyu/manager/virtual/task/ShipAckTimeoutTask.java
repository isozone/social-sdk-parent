package cn.net.rjnetwork.xianyu.manager.virtual.task;

import cn.net.rjnetwork.xianyu.manager.virtual.service.AutoShipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 发货送达回执超时兜底定时任务入口 —— A8 配套。
 * <p>由 ScheduledTasks 统一调度（cron 每 2 分钟一次），调 {@link AutoShipService#markStaleSentPendingAckAsFailed}
 * 扫「status=SENT_PENDING_ACK 且 sentAt 已过 staleSeconds」的 task 转 FAILED + 推 VIRTUAL_SHIP_FAILED 通知。</p>
 *
 * <p>意义：闲鱼 IM 是异步帧（sendFrameAsync 写帧即返回合成 200，不等服务端 ack），
 * 本任务把「发出但超时未收送达回执」的发货任务从乐观 SENT_PENDING_ACK 转为确定 FAILED，
 * 避免任务永久滞留 SENT_PENDING_ACK 状态、也避免「显示已发货但买家没收」的假成功。</p>
 *
 * <p>转 FAILED 后会被 {@link cn.net.rjnetwork.xianyu.manager.virtual.service.RedeliveryService#collectCandidates}
 * 扫到（已纳入 SENT_PENDING_ACK）按指数退避重发；超重试上限则转人工。</p>
 */
@Component
public class ShipAckTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(ShipAckTimeoutTask.class);

    private final AutoShipService autoShipService;

    /** 发出后多少秒未收服务端送达 ack 即判超时。默认 5 分钟（闲鱼 IM 回执通常秒级，5 分钟兜底足够）。 */
    @org.springframework.beans.factory.annotation.Value("${xianyu.virtual-ship.ack-stale-seconds:300}")
    private int staleSeconds;

    public ShipAckTimeoutTask(AutoShipService autoShipService) {
        this.autoShipService = autoShipService;
    }

    /** 定时执行超时兜底扫描。由 ScheduledTasks.runShipAckTimeout 调用。 */
    public void runScheduled() {
        try {
            int staled = autoShipService.markStaleSentPendingAckAsFailed(staleSeconds);
            if (staled > 0) {
                log.warn("[A8-ACK] {} ship tasks timed out waiting for server ack (staleSeconds={})",
                        staled, staleSeconds);
            }
        } catch (Exception e) {
            log.warn("[A8-ACK] ship ack timeout scan failed: {}", e.getMessage());
        }
    }
}
