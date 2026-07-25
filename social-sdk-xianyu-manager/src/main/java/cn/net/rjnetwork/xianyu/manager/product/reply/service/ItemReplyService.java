package cn.net.rjnetwork.xianyu.manager.product.reply.service;

import cn.net.rjnetwork.xianyu.manager.product.reply.mapper.ItemReplyMapper;
import cn.net.rjnetwork.xianyu.manager.product.reply.model.ItemReply;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品专属回复服务 —— BOT-D1。
 *
 * <p>不同于通用关键词回复（A10），本服务是 <b>商品级专属回复</b>：
 * 指定商品在指定触发场景下回复固定内容，优先级高于通用关键词回复。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>CRUD item_reply 表（按账号/商品/场景维度配置）；</li>
 *   <li>支持占位符替换：{商品名} {买家昵称} {订单号} 等；</li>
 *   <li>匹配时按 accountId+itemId+triggerScene 精匹配，enabled=1 过滤，priority 升序后随机选；</li>
 *   <li>选中后写 useCount++，供管理端统计。</li>
 * </ol>
 */
@Service
public class ItemReplyService {

    private static final Logger log = LoggerFactory.getLogger(ItemReplyService.class);

    public static final String SCENE_FIRST_INQUIRY = "FIRST_INQUIRY";
    public static final String SCENE_ORDER_PLACED = "ORDER_PLACED";
    public static final String SCENE_ORDER_PAID = "ORDER_PAID";

    private final ItemReplyMapper replyMapper;

    public ItemReplyService(ItemReplyMapper replyMapper) {
        this.replyMapper = replyMapper;
    }

    /** 分页查询商品专属回复（可按 accountId/itemId/triggerScene 过滤）。 */
    public Page<ItemReply> page(long page, long size, Long accountId, String itemId, String triggerScene) {
        Page<ItemReply> p = new Page<>(page, size);
        LambdaQueryWrapper<ItemReply> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(ItemReply::getAccountId, accountId);
        if (itemId != null && !itemId.isBlank()) w.eq(ItemReply::getItemId, itemId);
        if (triggerScene != null && !triggerScene.isBlank()) w.eq(ItemReply::getTriggerScene, triggerScene);
        w.orderByAsc(ItemReply::getPriority).orderByDesc(ItemReply::getCreatedAt);
        return replyMapper.selectPage(p, w);
    }

    /** 列表查询（不分页）。 */
    public List<ItemReply> list(Long accountId, String itemId, String triggerScene) {
        LambdaQueryWrapper<ItemReply> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(ItemReply::getAccountId, accountId);
        if (itemId != null && !itemId.isBlank()) w.eq(ItemReply::getItemId, itemId);
        if (triggerScene != null && !triggerScene.isBlank()) w.eq(ItemReply::getTriggerScene, triggerScene);
        w.orderByAsc(ItemReply::getPriority).orderByDesc(ItemReply::getCreatedAt);
        return replyMapper.selectList(w);
    }

    @Transactional
    public ItemReply create(ItemReply reply) {
        if (reply.getTriggerScene() == null || reply.getTriggerScene().isBlank())
            reply.setTriggerScene(SCENE_FIRST_INQUIRY);
        if (reply.getEnabled() == null) reply.setEnabled(1);
        if (reply.getPriority() == null) reply.setPriority(100);
        if (reply.getUseCount() == null) reply.setUseCount(0);
        reply.setCreatedAt(LocalDateTime.now());
        reply.setUpdatedAt(LocalDateTime.now());
        replyMapper.insert(reply);
        return reply;
    }

    @Transactional
    public ItemReply update(Long id, ItemReply patch) {
        ItemReply existing = replyMapper.selectById(id);
        if (existing == null) throw new IllegalArgumentException("商品专属回复不存在 id=" + id);
        if (patch.getAccountId() != null) existing.setAccountId(patch.getAccountId());
        if (patch.getItemId() != null) existing.setItemId(patch.getItemId());
        if (patch.getItemTitle() != null) existing.setItemTitle(patch.getItemTitle());
        if (patch.getTriggerScene() != null) existing.setTriggerScene(patch.getTriggerScene());
        if (patch.getReplyContent() != null) existing.setReplyContent(patch.getReplyContent());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());
        if (patch.getPriority() != null) existing.setPriority(patch.getPriority());
        if (patch.getRemark() != null) existing.setRemark(patch.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        replyMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        replyMapper.deleteById(id);
    }

    /** 启用/停用（账号级开关）。 */
    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        ItemReply r = replyMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("商品专属回复不存在 id=" + id);
        r.setEnabled(enabled ? 1 : 0);
        r.setUpdatedAt(LocalDateTime.now());
        replyMapper.updateById(r);
    }

    /**
     * 匹配商品专属回复并渲染占位符。
     *
     * @param accountId    账号 ID
     * @param itemId       商品 ID
     * @param triggerScene 触发场景（FIRST_INQUIRY/ORDER_PLACED/ORDER_PAID）
     * @param buyerNick    买家昵称（替换 {买家昵称}）
     * @return 渲染后的回复文本；无可用回复返回 null（调用方应回退到通用关键词回复）
     */
    @Transactional
    public String pickAndRender(Long accountId, String itemId, String triggerScene, String buyerNick) {
        if (itemId == null || itemId.isBlank()) return null;
        LambdaQueryWrapper<ItemReply> w = new LambdaQueryWrapper<>();
        w.eq(ItemReply::getEnabled, 1);
        w.eq(ItemReply::getAccountId, accountId);
        w.eq(ItemReply::getItemId, itemId);
        w.eq(ItemReply::getTriggerScene, triggerScene == null ? SCENE_FIRST_INQUIRY : triggerScene);
        w.orderByAsc(ItemReply::getPriority);
        List<ItemReply> candidates = replyMapper.selectList(w);
        if (candidates.isEmpty()) return null;
        ItemReply chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        chosen.setUseCount(Optional.ofNullable(chosen.getUseCount()).orElse(0) + 1);
        try { replyMapper.updateById(chosen); } catch (Exception e) {
            log.warn("[BOT-D1] item_reply id={} 使用次数更新失败（非致命）: {}", chosen.getId(), e.getMessage());
        }
        return render(chosen.getReplyContent(), chosen.getItemTitle(), buyerNick);
    }

    private String render(String content, String itemTitle, String buyerNick) {
        if (content == null) return "";
        String r = content;
        if (itemTitle != null) r = r.replace("{商品名}", itemTitle);
        if (buyerNick != null) r = r.replace("{买家昵称}", buyerNick);
        return r;
    }
}
