package cn.net.rjnetwork.xianyu.manager.order.rate.service;

import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.CommentTemplateMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.CommentTemplate;
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
 * 评价模板服务 —— BOT-B1。
 *
 * <p>不同于 {@link AutoRateService}（A11 自动评价执行，单条配置），本服务是 <b>多条模板管理</b>：
 * 买家确认收货后随机选一条评价文本发送，避免重复评价被风控。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>CRUD comment_templates 表（按账号/分类维度配置）；</li>
 *   <li>支持占位符替换：{商品名} {买家昵称} {订单号} 等；</li>
 *   <li>随机选模板：enabled=1 + category 匹配 + priority 升序过滤后随机；</li>
 *   <li>选中后写 useCount++，供管理端统计。</li>
 * </ol>
 */
@Service
public class CommentTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CommentTemplateService.class);

    private final CommentTemplateMapper templateMapper;

    public CommentTemplateService(CommentTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /** 分页查询评价模板（可按 accountId/category 过滤）。 */
    public Page<CommentTemplate> page(long page, long size, Long accountId, String category) {
        Page<CommentTemplate> p = new Page<>(page, size);
        LambdaQueryWrapper<CommentTemplate> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(CommentTemplate::getAccountId, accountId);
        if (category != null && !category.isBlank()) w.eq(CommentTemplate::getCategory, category);
        w.orderByAsc(CommentTemplate::getPriority).orderByDesc(CommentTemplate::getCreatedAt);
        return templateMapper.selectPage(p, w);
    }

    /** 列表查询（不分页）。 */
    public List<CommentTemplate> list(Long accountId, String category) {
        LambdaQueryWrapper<CommentTemplate> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(CommentTemplate::getAccountId, accountId);
        if (category != null && !category.isBlank()) w.eq(CommentTemplate::getCategory, category);
        w.orderByAsc(CommentTemplate::getPriority).orderByDesc(CommentTemplate::getCreatedAt);
        return templateMapper.selectList(w);
    }

    @Transactional
    public CommentTemplate create(CommentTemplate tpl) {
        if (tpl.getCategory() == null || tpl.getCategory().isBlank()) tpl.setCategory("POSITIVE");
        if (tpl.getEnabled() == null) tpl.setEnabled(1);
        if (tpl.getPriority() == null) tpl.setPriority(100);
        if (tpl.getUseCount() == null) tpl.setUseCount(0);
        tpl.setCreatedAt(LocalDateTime.now());
        tpl.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(tpl);
        return tpl;
    }

    @Transactional
    public CommentTemplate update(Long id, CommentTemplate patch) {
        CommentTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new IllegalArgumentException("模板不存在 id=" + id);
        if (patch.getAccountId() != null) existing.setAccountId(patch.getAccountId());
        if (patch.getCategory() != null) existing.setCategory(patch.getCategory());
        if (patch.getContent() != null) existing.setContent(patch.getContent());
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());
        if (patch.getPriority() != null) existing.setPriority(patch.getPriority());
        if (patch.getRemark() != null) existing.setRemark(patch.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }

    /** 启用/停用模板（账号级开关）。 */
    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        CommentTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) throw new IllegalArgumentException("模板不存在 id=" + id);
        tpl.setEnabled(enabled ? 1 : 0);
        tpl.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(tpl);
    }

    /**
     * 随机选一条激活模板并替换占位符。
     *
     * @param accountId 账号 ID（先匹配账号专属，再匹配全局 accountId=null）
     * @param category  模板分类（POSITIVE/NEUTRAL/REPLY）
     * @param productName 商品名（替换 {商品名}）
     * @param buyerNick   买家昵称（替换 {买家昵称}）
     * @return 选中的模板文本；无可用模板返回 null
     */
    @Transactional
    public String pickAndRender(Long accountId, String category, String productName, String buyerNick) {
        LambdaQueryWrapper<CommentTemplate> w = new LambdaQueryWrapper<>();
        w.eq(CommentTemplate::getEnabled, 1);
        w.eq(CommentTemplate::getCategory, category == null ? "POSITIVE" : category);
        // 账号维度：先匹配 accountId 指定的，再匹配 accountId=null 的全局模板
        w.and(ww -> ww.eq(CommentTemplate::getAccountId, accountId).or().isNull(CommentTemplate::getAccountId));
        w.orderByAsc(CommentTemplate::getPriority);
        List<CommentTemplate> candidates = templateMapper.selectList(w);
        if (candidates.isEmpty()) {
            log.warn("[BOT-B1] 账号 {} 分类 {} 无可用评价模板", accountId, category);
            return null;
        }
        // 优先选账号专属（accountId 精确匹配），同优先级下随机
        List<CommentTemplate> accountSpecific = candidates.stream()
                .filter(t -> accountId != null && accountId.equals(t.getAccountId()))
                .toList();
        List<CommentTemplate> pool = !accountSpecific.isEmpty() ? accountSpecific : candidates;
        CommentTemplate chosen = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        // 写 useCount++
        chosen.setUseCount(Optional.ofNullable(chosen.getUseCount()).orElse(0) + 1);
        try { templateMapper.updateById(chosen); } catch (Exception e) {
            log.warn("[BOT-B1] 模板 id={} 使用次数更新失败（非致命）: {}", chosen.getId(), e.getMessage());
        }
        return render(chosen.getContent(), productName, buyerNick);
    }

    /** 替换占位符：{商品名} {买家昵称} 等。 */
    private String render(String content, String productName, String buyerNick) {
        if (content == null) return "";
        String r = content;
        if (productName != null) r = r.replace("{商品名}", productName);
        if (buyerNick != null) r = r.replace("{买家昵称}", buyerNick);
        return r;
    }
}
