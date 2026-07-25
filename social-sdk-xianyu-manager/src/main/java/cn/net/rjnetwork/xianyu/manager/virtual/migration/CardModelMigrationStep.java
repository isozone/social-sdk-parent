package cn.net.rjnetwork.xianyu.manager.virtual.migration;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import cn.net.rjnetwork.xianyu.manager.config.migration.MigrationStep;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A6 卡券模型迁移 —— 把老 {@code virtual_card_pool} 数据迁移到新 {@code ship_card}（cardType=CARD）
 * + {@code card_item_relation}（product_id 关联）。
 *
 * <p>迁移策略（幂等）：</p>
 * <ol>
 *   <li>若 ship_card 表不存在 → 按 dialect 建表（schema*.sql 已建，兜底仅针对升级场景）；</li>
 *   <li>若 ship_card 已有数据（迁移过）→ 直接返回，避免重复迁移；</li>
 *   <li>否则 SELECT * FROM virtual_card_pool，逐条 INSERT 到 ship_card（cardType=CARD，
 *       card_code/card_password 直映），再 INSERT card_item_relation（productId/cardId/priority=0）；</li>
 *   <li>老表 virtual_card_pool 不删（保留审计），新链路改读 ship_card。</li>
 * </ol>
 */
@Component
public class CardModelMigrationStep implements MigrationStep {

    private static final Logger log = LoggerFactory.getLogger(CardModelMigrationStep.class);
    private final CardItemRelationMapper relationMapper;
    private final JdbcTemplate jdbc;

    public CardModelMigrationStep(CardItemRelationMapper relationMapper, JdbcTemplate jdbc) {
        this.relationMapper = relationMapper;
        this.jdbc = jdbc;
    }

    @Override public String namespace() { return "card"; }
    @Override public String version() { return "20260725_01"; }
    @Override public String description() { return "migrate virtual_card_pool to ship_card (4 types) + card_item_relation"; }

    @Override
    public void migrate(DataSource dataSource) throws Exception {
        // 1. 幂等：ship_card 已有数据则跳过
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM ship_card", Long.class);
        if (existing != null && existing > 0) {
            log.info("[A6] ship_card 已有 {} 条，跳过迁移", existing);
            return;
        }
        // 2. 老 virtual_card_pool 不存在或为空 → 跳过
        boolean oldExists = true;
        try {
            Long oldCount = jdbc.queryForObject("SELECT COUNT(*) FROM virtual_card_pool", Long.class);
            if (oldCount == null || oldCount == 0) {
                log.info("[A6] virtual_card_pool 为空，跳过迁移");
                return;
            }
        } catch (Exception e) {
            oldExists = false;
            log.info("[A6] virtual_card_pool 表不存在，跳过迁移");
            return;
        }
        // 3. 逐条迁移
        List<CardRow> oldRows = jdbc.query(
                "SELECT id, product_id, card_code, card_password, status, used_order_id, used_at FROM virtual_card_pool WHERE deleted = 0",
                (rs, rowNum) -> {
                    CardRow r = new CardRow();
                    r.id = rs.getLong("id");
                    r.productId = rs.getLong("product_id");
                    r.cardCode = rs.getString("card_code");
                    r.cardPassword = rs.getString("card_password");
                    r.status = rs.getString("status");
                    r.usedOrderId = rs.getObject("used_order_id", Long.class);
                    r.usedAt = rs.getObject("used_at", LocalDateTime.class);
                    return r;
                });
        int migrated = 0;
        for (CardRow old : oldRows) {
            ShipCard card = new ShipCard();
            card.setCardType("CARD");
            card.setCardCode(old.cardCode);
            card.setCardPassword(old.cardPassword);
            card.setStatus(Optional.ofNullable(old.status).orElse("AVAILABLE"));
            card.setUsedOrderId(old.usedOrderId);
            card.setUsedAt(old.usedAt);
            // 用 jdbc 直接插，避免 ShipCardMapper 还没被 Spring 完整初始化
            jdbc.update("INSERT INTO ship_card (card_type, card_code, card_password, status, used_order_id, used_at, created_at, updated_at, deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    card.getCardType(), card.getCardCode(), card.getCardPassword(),
                    card.getStatus(), card.getUsedOrderId(), card.getUsedAt(),
                    LocalDateTime.now(), LocalDateTime.now());
            // 拿新插的 ship_card.id（按 card_code 唯一索引查回）
            Long newCardId = jdbc.queryForObject(
                    "SELECT id FROM ship_card WHERE card_code = ? ORDER BY id DESC LIMIT 1",
                    Long.class, old.cardCode);
            if (newCardId != null) {
                CardItemRelation rel = new CardItemRelation();
                rel.setProductId(old.productId);
                rel.setCardId(newCardId);
                rel.setPriority(0);
                rel.setEnabled(1);
                relationMapper.insert(rel);
            }
            migrated++;
        }
        log.info("[A6] 迁移完成：{} 条 virtual_card_pool → ship_card（CARD 类型）+ card_item_relation", migrated);
    }

    private static class CardRow {
        Long id, productId, usedOrderId;
        String cardCode, cardPassword, status;
        LocalDateTime usedAt;
    }
}
