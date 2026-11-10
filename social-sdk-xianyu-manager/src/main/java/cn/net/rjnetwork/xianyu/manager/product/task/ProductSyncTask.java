package cn.net.rjnetwork.xianyu.manager.product.task;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import cn.net.rjnetwork.xianyu.manager.product.service.SyncProgressService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品定时同步（违规下架自动检测）。
 *
 * <p>闲鱼侧违规下架/删除的商品没有主动推送事件，只有同步对账才能发现。
 * 本任务定时为每个有 Cookie 的账号跑一次 {@link ProductService#syncFromXianyuAsync}：</p>
 * <ul>
 *   <li>列表内商品按平台状态刷新（违规/下架状态映射为 OFF_SALE）；</li>
 *   <li>列表外但本地仍在售的商品由对账逻辑标记 OFF_SALE，并推 PRODUCT_TAKE_DOWN 通知。</li>
 * </ul>
 *
 * <p>可通过 xianyu.product.auto-sync.enabled=false 关闭；
 * 周期由 xianyu.product.auto-sync.interval-minutes 控制（默认 120 分钟）。</p>
 */
@Component
public class ProductSyncTask {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncTask.class);

    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private ProductService productService;
    @Autowired
    private SyncProgressService syncProgressService;

    @Value("${xianyu.product.auto-sync.enabled:true}")
    private boolean enabled;

    @Scheduled(
            initialDelayString = "#{${xianyu.product.auto-sync.initial-delay-minutes:5} * 60000}",
            fixedDelayString = "#{${xianyu.product.auto-sync.interval-minutes:120} * 60000}")
    public void autoSyncAllAccounts() {
        if (!enabled) return;
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<>());
        for (XianyuAccount account : accounts) {
            if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) continue;
            try {
                // 已有在跑同步则跳过，与手动同步共用同一进度锁
                if (syncProgressService.getRunningSyncId(account.getId()) != null) continue;
                String syncId = syncProgressService.createNewSyncId(account.getId());
                productService.syncFromXianyuAsync(account.getId(), syncId);
                log.info("auto-sync: account {} triggered, syncId={}", account.getId(), syncId);
            } catch (Exception e) {
                log.warn("auto-sync: account {} failed: {}", account.getId(), e.getMessage());
            }
        }
    }
}
