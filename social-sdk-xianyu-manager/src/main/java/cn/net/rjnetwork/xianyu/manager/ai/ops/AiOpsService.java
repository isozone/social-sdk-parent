package cn.net.rjnetwork.xianyu.manager.ai.ops;

import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiModelMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsBatchCreateRequest;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsBatchCreateResult;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsMultiSyncRequest;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsWeeklyReport;
import cn.net.rjnetwork.xianyu.manager.ops.mapper.AiOpsKnowledgeMapper;
import cn.net.rjnetwork.xianyu.manager.ops.mapper.AiOpsSuggestionMapper;
import cn.net.rjnetwork.xianyu.manager.ops.mapper.AiOpsTaskMapper;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsKnowledge;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsSuggestion;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsTask;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import cn.net.rjnetwork.xianyu.manager.reply.mapper.AutoReplyLogMapper;
import cn.net.rjnetwork.xianyu.manager.reply.model.XianyuAutoReplyLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 运营服务（批量上品、多账号同步、运营周报）
 */
@Service
public class AiOpsService {

    private static final Logger log = LoggerFactory.getLogger(AiOpsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiOpsTaskMapper taskMapper;
    private final AiOpsSuggestionMapper suggestionMapper;
    private final AiOpsKnowledgeMapper knowledgeMapper;
    private final AiChatService chatService;
    private final ProductService productService;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final AiModelMapper aiModelMapper;
    private final AutoReplyLogMapper autoReplyLogMapper;

    /** 任务进度缓存 */
    private final Map<Long, OpsBatchCreateResult> progressCache = new ConcurrentHashMap<>();

    public AiOpsService(AiOpsTaskMapper taskMapper,
                        AiOpsSuggestionMapper suggestionMapper,
                        AiOpsKnowledgeMapper knowledgeMapper,
                        AiChatService chatService,
                        ProductService productService,
                        ProductMapper productMapper,
                        OrderMapper orderMapper,
                        AiModelMapper aiModelMapper,
                        AutoReplyLogMapper autoReplyLogMapper) {
        this.taskMapper = taskMapper;
        this.suggestionMapper = suggestionMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.chatService = chatService;
        this.productService = productService;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.aiModelMapper = aiModelMapper;
        this.autoReplyLogMapper = autoReplyLogMapper;
    }

    // ======================================================================
    // 1. 批量上品（AI 生成商品信息，批量创建）
    // ======================================================================

    @Transactional
    public AiOpsTask startBatchCreate(OpsBatchCreateRequest request, Long modelId) {
        AiOpsTask task = new AiOpsTask();
        task.setAccountId(request.getAccountId());
        task.setTaskType("BATCH_CREATE");
        task.setStatus("PENDING");
        task.setPayload(toJson(request));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        try {
            doBatchCreate(task, request, modelId);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            taskMapper.updateById(task);
        }
        return task;
    }

    private void doBatchCreate(AiOpsTask task, OpsBatchCreateRequest request, Long modelId) {
        task.setStatus("RUNNING");
        taskMapper.updateById(task);

        OpsBatchCreateResult result = new OpsBatchCreateResult();
        progressCache.put(task.getId(), result);

        List<OpsBatchCreateRequest.ProductSeed> seeds = request.getProducts();
        result.setTotal(seeds != null ? seeds.size() : 0);
        if (seeds == null) {
            task.setStatus("COMPLETED");
            task.setResultSummary("无商品");
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return;
        }

        for (OpsBatchCreateRequest.ProductSeed seed : seeds) {
            try {
                GeneratedListing listing = generateListing(seed, request.getCategory(), modelId);
                ProductCreateRequest pr = new ProductCreateRequest();
                pr.setAccountId(request.getAccountId());
                pr.setTitle(listing.title);
                pr.setDescription(listing.description);
                pr.setPrice(seed.getSuggestedPrice() != null ? seed.getSuggestedPrice() : listing.suggestedPrice);
                pr.setOriginalPrice(listing.originalPrice);
                pr.setCategoryId(request.getCategory());
                pr.setImages(listing.images);
                pr.setVideos(Collections.emptyList());
                XianyuProduct saved = productService.create(pr);
                result.setSuccess(result.getSuccess() + 1);
                result.getCreatedProductIds().add(saved.getId());
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add(seed.getSource() + ": " + e.getMessage());
            }
        }

        task.setStatus("COMPLETED");
        task.setResultSummary(String.format("成功 %d / %d", result.getSuccess(), result.getTotal()));
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        progressCache.remove(task.getId());
    }

    private GeneratedListing generateListing(OpsBatchCreateRequest.ProductSeed seed, String category, Long modelId) {
        GeneratedListing listing = new GeneratedListing();
        listing.images = seed.getImageUrls() != null ? seed.getImageUrls() : Collections.emptyList();

        if (modelId != null) {
            String systemPrompt = """
                    你是一个闲鱼商品文案专家。请根据商品核心信息，生成：
                    1. 标题（30字以内，含核心关键词）
                    2. 描述（150-200字，突出卖点，含售后说明）
                    3. 建议售价
                    4. 原价
                    只返回 JSON：{"title":"...","description":"...","suggestedPrice":99.0,"originalPrice":199.0}
                    """;
            String userMessage = "品类：%s\n商品信息：%s\n成色：%s".formatted(category,
                    seed.getKeywords() != null ? String.join("、", seed.getKeywords()) : seed.getSource(),
                    seed.getCondition() != null ? seed.getCondition() : "九成新");
            try {
                String raw = chatService.chat(modelId, systemPrompt, userMessage);
                JsonNode node = MAPPER.readTree(stripCodeBlock(raw));
                listing.title = node.path("title").asText(seed.getSource());
                listing.description = node.path("description").asText();
                listing.suggestedPrice = BigDecimal.valueOf(node.path("suggestedPrice").asDouble(0));
                listing.originalPrice = BigDecimal.valueOf(node.path("originalPrice").asDouble(0));
                return listing;
            } catch (Exception e) {
                System.err.println("[AiOpsService] generateListing failed: " + e.getMessage());
            }
        }

        listing.title = seed.getSource();
        listing.description = String.format("优质%s，%s，支持验机，包邮。", seed.getSource(),
                seed.getCondition() != null ? seed.getCondition() : "九成新");
        listing.suggestedPrice = BigDecimal.ZERO;
        listing.originalPrice = BigDecimal.ZERO;
        return listing;
    }

    private String stripCodeBlock(String raw) {
        if (raw == null) return "{}";
        int a = raw.indexOf('{');
        int b = raw.lastIndexOf('}');
        return (a >= 0 && b > a) ? raw.substring(a, b + 1) : raw;
    }

    public OpsBatchCreateResult getBatchProgress(Long taskId) {
        return progressCache.getOrDefault(taskId, new OpsBatchCreateResult());
    }

    // ======================================================================
    // 2. 多账号同步（AI 改写到多个闲鱼账号）
    // ======================================================================

    @Transactional
    public AiOpsTask startMultiAccountSync(OpsMultiSyncRequest request, Long modelId) {
        AiOpsTask task = new AiOpsTask();
        task.setAccountId(request.getSourceAccountId());
        task.setTaskType("MULTI_ACCOUNT_SYNC");
        task.setStatus("PENDING");
        task.setPayload(toJson(request));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        try {
            doMultiAccountSync(task, request, modelId);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            taskMapper.updateById(task);
        }
        return task;
    }

    private void doMultiAccountSync(AiOpsTask task, OpsMultiSyncRequest request, Long modelId) {
        task.setStatus("RUNNING");
        taskMapper.updateById(task);

        XianyuProduct sourceProduct = productMapper.selectById(request.getProductId());
        if (sourceProduct == null) throw new IllegalArgumentException("源商品不存在: " + request.getProductId());

        List<Long> targetAccountIds = request.getTargetAccountIds();
        int successCount = 0;
        int delayIndex = 0;
        for (Long targetAccountId : targetAccountIds) {
            try {
                RewrittenListing rewritten = rewriteListing(sourceProduct, modelId);
                List<String> shuffledImages = shuffleImages(sourceProduct.getImages());
                ProductCreateRequest pr = new ProductCreateRequest();
                pr.setAccountId(targetAccountId);
                pr.setTitle(rewritten.title);
                pr.setDescription(rewritten.description);
                pr.setPrice(rewritePrice(sourceProduct.getPrice(), delayIndex));
                pr.setOriginalPrice(sourceProduct.getOriginalPrice());
                pr.setCategoryId(sourceProduct.getCategoryId());
                pr.setImages(shuffledImages);
                pr.setVideos(Collections.emptyList());
                productService.create(pr);
                successCount++;
                if (request.getDelayMinutesPerAccount() != null && request.getDelayMinutesPerAccount() > 0) {
                    Thread.sleep(request.getDelayMinutesPerAccount() * 60_000L);
                }
                delayIndex++;
            } catch (Exception e) {
                System.err.println("[AiOpsService] sync to account failed: " + e.getMessage());
            }
        }

        task.setStatus("COMPLETED");
        task.setResultSummary(String.format("同步 %d / %d 个账号", successCount, targetAccountIds.size()));
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private RewrittenListing rewriteListing(XianyuProduct source, Long modelId) {
        RewrittenListing r = new RewrittenListing();
        if (modelId != null) {
            String systemPrompt = """
                    你是一个闲鱼多账号运营专家。给定一个商品信息，请在不改变语义前提下改写：
                    1. 标题换序、同义词替换（避免平台判重）
                    2. 描述段落重组
                    3. 保留核心关键词
                    只返回 JSON：{"title":"...","description":"..."}
                    """;
            String userMessage = "原标题：%s\n原描述：%s".formatted(source.getTitle(), source.getDescription());
            try {
                String raw = chatService.chat(modelId, systemPrompt, userMessage);
                JsonNode node = MAPPER.readTree(stripCodeBlock(raw));
                r.title = node.path("title").asText(source.getTitle());
                r.description = node.path("description").asText(source.getDescription());
                return r;
            } catch (Exception e) {
                // fallback
            }
        }
        r.title = "【自营】" + source.getTitle();
        r.description = source.getDescription();
        return r;
    }

    private List<String> shuffleImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return Collections.emptyList();
        try {
            List<String> list = MAPPER.readValue(imagesJson,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            Collections.shuffle(list);
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private BigDecimal rewritePrice(BigDecimal price, int delayIndex) {
        if (price == null) return BigDecimal.ZERO;
        BigDecimal variation = BigDecimal.valueOf((delayIndex * 0.02) - 0.01).setScale(2, RoundingMode.HALF_UP);
        return price.multiply(BigDecimal.ONE.add(variation)).setScale(0, RoundingMode.HALF_UP);
    }

    // ======================================================================
    // 3. AI 运营周报
    // ======================================================================

    public OpsWeeklyReport generateWeeklyReport(Long accountId, Long modelId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(7);
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = today.atTime(23, 59);

        List<XianyuProduct> products = productMapper.selectList(
                new LambdaQueryWrapper<XianyuProduct>().eq(XianyuProduct::getAccountId, accountId));
        int totalProducts = products.size();
        long onSale = products.stream().filter(p -> "ON_SALE".equals(p.getStatus())).count();
        long offSale = products.stream().filter(p -> "OFF_SALE".equals(p.getStatus())).count();
        long draft = products.stream().filter(p -> "DRAFT".equals(p.getStatus())).count();
        int totalViews = products.stream().mapToInt(p -> p.getViewCount() != null ? p.getViewCount() : 0).sum();
        int totalFavorites = products.stream().mapToInt(p -> p.getFavoriteCount() != null ? p.getFavoriteCount() : 0).sum();

        List<XianyuOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getAccountId, accountId)
                        .ge(XianyuOrder::getOrderTime, startDt)
                        .le(XianyuOrder::getOrderTime, endDt)
        );
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .map(XianyuOrder::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long completedOrders = orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
        long pendingOrders = orders.stream().filter(o -> "PENDING".equals(o.getStatus())).count();

        List<String> suggestions;
        if (modelId != null && !products.isEmpty()) {
            suggestions = generateAiSuggestions(products, orders, totalViews, totalRevenue, modelId);
        } else {
            suggestions = buildHeuristicSuggestions(onSale, offSale, draft, totalViews);
        }

        OpsWeeklyReport report = new OpsWeeklyReport();
        report.setAccountId(accountId);
        report.setWeekStart(start);
        report.setWeekEnd(today);
        report.setTotalProducts(totalProducts);
        report.setOnSaleProducts((int) onSale);
        report.setOffSaleProducts((int) offSale);
        report.setDraftProducts((int) draft);
        report.setTotalViews(totalViews);
        report.setTotalFavorites(totalFavorites);
        report.setCompletedOrders((int) completedOrders);
        report.setPendingOrders((int) pendingOrders);
        report.setTotalRevenue(totalRevenue);
        report.setSuggestions(suggestions);
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }

    private List<String> generateAiSuggestions(List<XianyuProduct> products, List<XianyuOrder> orders,
                                               int totalViews, BigDecimal revenue, Long modelId) {
        List<XianyuProduct> topViewed = products.stream()
                .sorted(Comparator.comparingInt(
                        (XianyuProduct p) -> p.getViewCount() != null ? p.getViewCount() : 0).reversed())
                .limit(5)
                .toList();

        String systemPrompt = """
                你是一个闲鱼运营专家。基于用户提供的商品数据，给出 3 条最优先的运营建议，
                每条不超过 50 字，以JSON数组返回：["建议1","建议2","建议3"]
                """;
        String userMessage = """
                商品数：%d（上架 %d / 下架 %d / 草稿 %d）
                周浏览量：%d
                周成交：%d 单 / 待发货 %d 单
                周收入：%s 元
                浏览 Top5 商品：%s
                """.formatted(
                products.size(),
                products.stream().filter(p -> "ON_SALE".equals(p.getStatus())).count(),
                products.stream().filter(p -> "OFF_SALE".equals(p.getStatus())).count(),
                products.stream().filter(p -> "DRAFT".equals(p.getStatus())).count(),
                totalViews,
                orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count(),
                orders.stream().filter(o -> "PENDING".equals(o.getStatus())).count(),
                revenue,
                topViewed.stream().map(XianyuProduct::getTitle).collect(Collectors.joining("、"))
        );

        try {
            String raw = chatService.chat(modelId, systemPrompt, userMessage);
            JsonNode node = MAPPER.readTree(stripCodeBlock(raw));
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                node.forEach(n -> result.add(n.asText()));
                return result;
            }
        } catch (Exception e) {
            System.err.println("[AiOpsService] AI suggestions failed: " + e.getMessage());
        }
        return buildHeuristicSuggestions(
                products.stream().filter(p -> "ON_SALE".equals(p.getStatus())).count(),
                products.stream().filter(p -> "OFF_SALE".equals(p.getStatus())).count(),
                products.stream().filter(p -> "DRAFT".equals(p.getStatus())).count(),
                totalViews
        );
    }

    private List<String> buildHeuristicSuggestions(long onSale, long offSale, long draft, int totalViews) {
        List<String> list = new ArrayList<>();
        if (draft > 0) list.add("当前有 %d 款草稿未上架，建议尽快编辑后发布".formatted(draft));
        if (offSale > onSale) list.add("下架商品数多于上架数，建议整理翻新后再上架");
        if (totalViews < 100) list.add("本周浏览量偏低，建议优化标题关键词吸引搜索流量");
        if (list.isEmpty()) list.add("运营状态良好，保持当前节奏");
        return list;
    }

    // ======================================================================
    // 辅助
    // ======================================================================

    public List<AiOpsTask> listTasks(Long accountId, String status, int page, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiOpsTask> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        LambdaQueryWrapper<AiOpsTask> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) wrapper.eq(AiOpsTask::getAccountId, accountId);
        if (status != null && !status.isBlank()) wrapper.eq(AiOpsTask::getStatus, status);
        wrapper.orderByDesc(AiOpsTask::getCreatedAt);
        return taskMapper.selectPage(p, wrapper).getRecords();
    }

    @Scheduled(cron = "0 30 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void archiveDailyToKnowledge() {
        try {
            LocalDate y = LocalDate.now().minusDays(1);
            LocalDateTime start = y.atStartOfDay();
            LocalDateTime end = y.plusDays(1).atStartOfDay();

            // 昨日订单（成交趋势）
            List<XianyuOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<XianyuOrder>().between(XianyuOrder::getOrderTime, start, end));
            int sold = (int) orders.stream().filter(o -> "SOLD".equals(o.getType())).count();
            int bought = (int) orders.stream().filter(o -> "BOUGHT".equals(o.getType())).count();

            // 商品按 categoryId 聚合
            List<XianyuProduct> products = productMapper.selectList(null);
            Map<String, CategoryStat> byCat = new LinkedHashMap<>();
            for (XianyuProduct p : products) {
                String cat = p.getCategoryId() != null ? p.getCategoryId() : "未分类";
                CategoryStat s = byCat.computeIfAbsent(cat, k -> new CategoryStat());
                s.productCount++;
                s.views += p.getViewCount() != null ? p.getViewCount() : 0;
                s.favs += p.getFavoriteCount() != null ? p.getFavoriteCount() : 0;
            }

            // 热门命中关键词（自动回复日志，近 7 天）
            LocalDateTime kwSince = LocalDate.now().atStartOfDay().minusDays(6);
            List<XianyuAutoReplyLog> logs = autoReplyLogMapper.selectList(
                    new LambdaQueryWrapper<XianyuAutoReplyLog>().ge(XianyuAutoReplyLog::getCreatedAt, kwSince)
                            .eq(XianyuAutoReplyLog::getMatched, true));
            Map<String, Integer> kwHit = new LinkedHashMap<>();
            for (XianyuAutoReplyLog l : logs) {
                if (l.getKeyword() == null || l.getKeyword().isBlank()) continue;
                kwHit.merge(l.getKeyword(), 1, Integer::sum);
            }

            Long modelId = resolveDefaultModelId();
            if (modelId == null) {
                log.warn("[AiOps] 无可用 AI 模型，跳过知识库 AI 生成（仅保留聚合统计）");
            }

            for (Map.Entry<String, CategoryStat> e : byCat.entrySet()) {
                String cat = e.getKey();
                CategoryStat s = e.getValue();
                generateAndSaveKnowledge(modelId, cat, "PRICING",
                        String.format("你是闲鱼运营专家。品类=%s，昨日成交=%d，买入=%d，在售商品=%d，总浏览=%d，总收藏=%d。请给出该品类的定价策略要点（3-5 条，简洁）。",
                                cat, sold, bought, s.productCount, s.views, s.favs));
                generateAndSaveKnowledge(modelId, cat, "KEYWORD",
                        String.format("你是闲鱼运营专家。品类=%s。近 7 天命中高频关键词及次数：%s。请给出该品类标题/文案应重点覆盖的搜索关键词（5-8 个，逗号分隔）。",
                                cat, topKeywords(kwHit, 10)));
                generateAndSaveKnowledge(modelId, cat, "POSTING_TIME",
                        String.format("你是闲鱼运营专家。品类=%s，在售=%d，昨日成交=%d。请给出生源该品类商品的最佳发布/擦亮时段建议（简洁）。",
                                cat, s.productCount, sold));
            }
            log.info("[AiOps] 知识库沉淀完成：品类数={}", byCat.size());
        } catch (Exception ex) {
            log.error("[AiOps] 知识库沉淀失败: {}", ex.getMessage(), ex);
        }
    }

    /** AI 生成并 upsert 知识（category+knowledgeType+source=AI_GENERATED 先删后插，避免重复堆积） */
    private void generateAndSaveKnowledge(Long modelId, String category, String knowledgeType, String prompt) {
        if (modelId == null) return;
        try {
            String content = chatService.chat(modelId, "你是一名闲鱼二手交易运营顾问，回答简洁、可执行。", prompt);
            if (content == null || content.isBlank()) return;
            knowledgeMapper.delete(new LambdaQueryWrapper<AiOpsKnowledge>()
                    .eq(AiOpsKnowledge::getCategory, category)
                    .eq(AiOpsKnowledge::getKnowledgeType, knowledgeType)
                    .eq(AiOpsKnowledge::getSource, "AI_GENERATED"));
            AiOpsKnowledge k = new AiOpsKnowledge();
            k.setCategory(category);
            k.setKnowledgeType(knowledgeType);
            k.setContent(content.trim());
            k.setSource("AI_GENERATED");
            k.setUpdatedAt(LocalDateTime.now());
            knowledgeMapper.insert(k);
        } catch (Exception ex) {
            log.warn("[AiOps] 生成知识失败 category={} type={}: {}", category, knowledgeType, ex.getMessage());
        }
    }

    /** 解析一个可用的默认 AI 模型（优先启用项） */
    private Long resolveDefaultModelId() {
        try {
            AiModel m = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                    .eq(AiModel::getEnabled, true).last("LIMIT 1"));
            return m != null ? m.getId() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String topKeywords(Map<String, Integer> kwHit, int n) {
        return kwHit.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n).map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining("、"));
    }

    /** 聚合中间结构 */
    private static class CategoryStat {
        int productCount = 0;
        int views = 0;
        int favs = 0;
    }

    // ======================================================================
    // 2. 知识库 / 建议 管理（供前端 AiOps 知识库页面）
    // ======================================================================

    public List<AiOpsKnowledge> listKnowledge(String category, String knowledgeType, int page, int size) {
        LambdaQueryWrapper<AiOpsKnowledge> w = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) w.eq(AiOpsKnowledge::getCategory, category);
        if (knowledgeType != null && !knowledgeType.isBlank()) w.eq(AiOpsKnowledge::getKnowledgeType, knowledgeType);
        w.orderByDesc(AiOpsKnowledge::getUpdatedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiOpsKnowledge> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        return knowledgeMapper.selectPage(p, w).getRecords();
    }

    public AiOpsKnowledge getKnowledge(Long id) {
        return knowledgeMapper.selectById(id);
    }

    public void deleteKnowledge(Long id) {
        knowledgeMapper.deleteById(id);
    }

    public AiOpsKnowledge createKnowledge(AiOpsKnowledge knowledge) {
        if (knowledge.getSource() == null) knowledge.setSource("MANUAL");
        if (knowledge.getUpdatedAt() == null) knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    public List<AiOpsSuggestion> listSuggestions(Long accountId, Boolean adopted, int page, int size) {
        LambdaQueryWrapper<AiOpsSuggestion> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(AiOpsSuggestion::getAccountId, accountId);
        if (adopted != null) w.eq(AiOpsSuggestion::getAdopted, adopted);
        w.orderByDesc(AiOpsSuggestion::getCreatedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiOpsSuggestion> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        return suggestionMapper.selectPage(p, w).getRecords();
    }

    @Transactional(rollbackFor = Exception.class)
    public AiOpsSuggestion adoptSuggestion(Long id) {
        AiOpsSuggestion s = suggestionMapper.selectById(id);
        if (s == null) return null;
        s.setAdopted(true);
        s.setAdoptedAt(LocalDateTime.now());
        suggestionMapper.updateById(s);
        return s;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiOpsSuggestion ignoreSuggestion(Long id) {
        AiOpsSuggestion s = suggestionMapper.selectById(id);
        if (s == null) return null;
        s.setAdopted(false);
        s.setAdoptedAt(LocalDateTime.now());
        suggestionMapper.updateById(s);
        return s;
    }

    private String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private static class GeneratedListing {
        String title;
        String description;
        BigDecimal suggestedPrice;
        BigDecimal originalPrice;
        List<String> images;
    }

    private static class RewrittenListing {
        String title;
        String description;
    }

    private void description(String text) { /* placeholder */ }
}
