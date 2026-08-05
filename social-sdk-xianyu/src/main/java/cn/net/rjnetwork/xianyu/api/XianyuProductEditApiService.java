package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼商品编辑与完整上下架 API 服务
 * 封装商品编辑、完整上下架、批量操作、价格调整等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 *
 * <p>改价/改库存采用「获取原商品信息 → 改字段 → 发布新商品 → 下架原商品」完整流程，
 * 因为闲鱼 PC 无独立改价/改库存接口。</p>
 */
public class XianyuProductEditApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;
    private final XianyuProductApiService productApiService;
    private final XianyuPublishApiService publishApiService;

    public XianyuProductEditApiService(XianyuMtopApiClient apiClient,
                                       XianyuProductApiService productApiService,
                                       XianyuPublishApiService publishApiService) {
        this.apiClient = apiClient;
        this.productApiService = productApiService;
        this.publishApiService = publishApiService;
    }

    /**
     * 兼容旧构造函数（不依赖 publish/product 服务，但 updatePrice/updateStock 会抛异常）
     * @deprecated 推荐用三参数构造函数
     */
    @Deprecated
    public XianyuProductEditApiService(XianyuMtopApiClient apiClient) {
        this(apiClient, null, null);
    }

    // ==================== 商品编辑 ====================

    /** 编辑商品基本信息 — mtop.taobao.idlehome.item.edit */
    public JsonNode editProduct(String itemId, String title, String description,
                                String price, String originalPrice,
                                String categoryId, String location) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        data.put("price", price != null ? price : "");
        data.put("originalPrice", originalPrice != null ? originalPrice : "");
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("location", location != null ? location : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.edit", toJson(data));
    }

    /**
     * 编辑商品详情图 — 命名规律候选 mtop.taobao.idlemanage.item.detail.edit
     * <p>未真抓验证（闲鱼 PC/H5 详情页未暴露编辑按钮入口，走内部 SPA 域）。
     * 已真验同域接口：com.taobao.idle.item.delete v1.1（删除），
     * 推测编辑类走 mtop.taobao.idlemanage.* 域，待后续真抓微调。</p>
     */
    public JsonNode editProductDetails(String itemId, String images) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("images", images != null ? images : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.detail.edit", toJson(data));
    }

    // ==================== 完整上下架 ====================

    /**
     * 商品上架 — 命名规律候选 mtop.taobao.idlemanage.item.upshelf
     * <p>未真抓验证。已真验下架走 mtop.taobao.idle.item.downshelf v2.0（不是 idlemanage 域），
     * 上架是下架的姊妹接口，命名规律候选 upshelf，待后续真抓微调。</p>
     */
    public JsonNode shelfOn(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        // 上架是下架的姊妹接口，按真实下架接口同域命名：mtop.taobao.idle.item.upshelf v2.0
        return apiClient.callMtop("mtop.taobao.idle.item.upshelf", "2.0", toJson(data));
    }

    /**
     * 商品下架 — 真实接口 mtop.taobao.idle.item.downshelf v2.0
     * <p>真实抓包验证（2026-07-19 CDP 抓详情页「下架」按钮 React onClick handler 源代码）。
     * 与 XianyuProductApiService.updateProductStatus(offsale) 同接口，保留这个方法为兼容旧 facade 调用。</p>
     */
    public JsonNode shelfOff(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.downshelf", "2.0", toJson(data));
    }

    /** 批量上架商品 — 命名规律候选 mtop.taobao.idle.item.batch.upshelf v2.0（未真抓） */
    public JsonNode batchShelfOn(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.upshelf", "2.0", toJson(data));
    }

    /** 批量下架商品 — 命名规律候选 mtop.taobao.idle.item.batch.downshelf v2.0（未真抓） */
    public JsonNode batchShelfOff(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.downshelf", "2.0", toJson(data));
    }

    // ==================== 价格调整 ====================

    /**
     * 调整商品价格 — 完整流程：获取原商品信息 → 改价格 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改价接口，走「新建替代」路径：不传 itemId 给 publishItem（pcMainPublish），
     * 生成新商品后，将原来的商品下架，实现改价效果。</p>
     *
     * @param itemId    要改价的原商品 id
     * @param price     新价格（元，如 "99.00"），内部转分后传给发布接口
     * @return 新商品的发布结果，data 含新 itemId
     */
    /**
     * 发布新商品（直传字段版）— 绕开 getProductDetail，直接用调用方传入的本地 DB 字段发布。
     * <p><b>背景：</b>改价/改库存链路原本要先调 mtop.taobao.idle.pc.detail 拉原商品详情再发新商品，
     * 三个问题：① 闲鱼风控易拦详情接口（RGV587_ERROR::SM::哎哟喂,被挤爆啦）；
     * ② 详情响应字段路径不稳导致库存/价格提取错（兜底成 1/0.02）；③ 多一次接口调用增加风控触发概率。
     * 本地 DB 在发布时已存了正确字段，直接用即可。</p>
     *
     * <p><b>类目兜底：</b>本地 DB 只存 categoryId（部分账号连这都没），缺 channelCatId/leafId/tbCatId/catName，
     * 用 DEFAULT fallback 类目 50023914（与 ProductService.buildItemCatDTO 同源）兜底，发布能通；
     * 类目若重要可在前端让用户显式选。</p>
     *
     * @param title           标题（本地 DB）
     * @param description     描述（本地 DB，可空）
     * @param priceCent       售价（分，本地 DB 元 × 100）
     * @param origPriceCent   原价（分，本地 DB 元 × 100，可空传 "0"）
     * @param stock           库存（本地 DB）
     * @param imageUrls       图片 URL 列表（本地 DB，至少 1 张，闲鱼端必填）
     * @param catId           类目 ID（本地 DB，可空 → 用默认 50023914）
     * @param goodsType       商品类型 PHYSICAL/VIRTUAL（本地 DB，可空 → 默认 PHYSICAL）
     * @param deliverType     发货方式 CARD/ACCOUNT/LINK/FILE（本地 DB，虚拟商品用，可空）
     * @param deliverContentTemplate 发货内容模板（本地 DB，虚拟商品用，可空）
     * @param shippingMode    运费偏好 NONE/FREE/DISTANCE（可空 → 默认 NONE 无需邮寄）
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode republishWithLocalFields(
            String title, String description,
            String priceCent, String origPriceCent, String stock,
            List<String> imageUrls,
            String catId, String goodsType, String deliverType, String deliverContentTemplate,
            String shippingMode) {
        if (publishApiService == null) {
            throw new IllegalStateException("republishWithLocalFields 需要 XianyuPublishApiService");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("改价/改库存：本地 DB 标题为空，无法发布");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            // 闲鱼端必报 FAIL_BIZ_ITEM_NO_PICS，本地没图就显式抛错让用户知道
            throw new IllegalStateException("改价/改库存：本地 DB 无图片，无法发布（闲鱼端必填至少 1 张图）");
        }

        // 1. 图片 URL → publishItem 需要的 imageInfoList（含 url/height/width）
        List<Map<String, Object>> images = new ArrayList<>();
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) continue;
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("url", url);
            img.put("height", 800);
            img.put("width", 800);
            images.add(img);
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("改价/改库存：本地 DB 图片 URL 全空，无法发布");
        }

        // 2. 类目 DTO：本地 categoryId 优先，缺则用默认兜底类目 50023914
        String effectiveCatId = (catId != null && !catId.isBlank()) ? catId : "50023914";
        Map<String, String> catDTO = new LinkedHashMap<>();
        catDTO.put("catId", effectiveCatId);
        catDTO.put("catName", "其他");
        catDTO.put("channelCatId", effectiveCatId);
        catDTO.put("leafId", effectiveCatId);
        catDTO.put("tbCatId", effectiveCatId);

        // 3. 标签/地址/运费：用默认值（本地 DB 没存这些字段，发布时也是用默认值）
        List<Map<String, Object>> labelExtList = new ArrayList<>();  // 无标签
        Map<String, Object> addrDTO = new LinkedHashMap<>();        // 默认地址（闲鱼端用账号默认收货地址）
        // 运费：由调用方透传 shippingMode 决定（NONE/FREE/DISTANCE），默认 NONE=无需邮寄
        Map<String, Object> deliverySettings = new LinkedHashMap<>();
        String mode = shippingMode != null ? shippingMode : "NONE";
        boolean supportFreight = !"NONE".equalsIgnoreCase(mode);
        boolean canFreeShipping = !"DISTANCE".equalsIgnoreCase(mode);
        deliverySettings.put("supportFreight", supportFreight);
        deliverySettings.put("canFreeShipping", canFreeShipping);
        deliverySettings.put("onlyTakeSelf", false);
        if (supportFreight) {
            deliverySettings.put("templateId", "-100");  // 按距离计费模板
        }

        // 4. 调 publishItem 发新商品（itemId=null → pcMainPublish 场景，真抓验证闲鱼 PC 端无编辑重发）
        JsonNode publishResult = publishApiService.publishItem(
            null, title, description, priceCent, origPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 5. 发布成功才算成功（下架原商品由调用方负责，本方法只负责发布）
        if (!isPublishSuccess(publishResult)) {
            throw new IllegalStateException("改价/改库存：" + describePublishFailure(publishResult));
        }
        return publishResult;
    }

    public JsonNode updatePrice(String itemId, String price) {
        return updatePrice(itemId, price, null, null);
    }

    /**
     * 改价（值覆盖版）— 与 {@link #updatePrice(String, String)} 相同流程，但允许调用方显式传
     * 本地 DB 已存的 stock / 原价（分），SDK 优先用传入值而非从闲鱼详情重新提取。
     * <p>背景：闲鱼详情响应里库存/原价字段路径不可靠（真抓多次验证位置不固定），
     * 本地 DB 存的是正确值（发布时写入），改价时直接透传，避免库存被兜底成 1、原价变 0.1。</p>
     *
     * @param itemId           要改价的原商品 id
     * @param price            新价格（元，如 "99.00"），内部转分后传给发布接口
     * @param stockOverride    原商品库存（本地 DB 值，非空则覆盖从详情提取）
     * @param origPriceCentOverride 原价（分，本地 DB 值，非空则覆盖从详情提取）
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode updatePrice(String itemId, String price, String stockOverride, String origPriceCentOverride) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updatePrice 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        // 1. 获取原商品详情（完整字段）
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        // 2. 提取原商品字段，构造发布参数
        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        // 原价/库存：优先用调用方传入的本地 DB 值（可靠），否则才从详情提取（兜底）
        String origPriceCent = (origPriceCentOverride != null && !origPriceCentOverride.isEmpty())
                ? origPriceCentOverride : extractOriginalPrice(itemDO);
        String stock = (stockOverride != null && !stockOverride.isEmpty())
                ? stockOverride : extractStock(itemDO, detail);
        List<Map<String, Object>> images = extractImages(itemDO, detail);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        // 3. 价格：元 → 分
        String newPriceCent = priceToCent(price);

        // 4. 发新商品版（itemId=null → pcMainPublish 场景）
        //    真抓验证（2026-08-04 调闲鱼 PC 发布页 bundle p_publish-index.js，139 万字）：
        //    闲鱼 PC 端 publishScene 只有 "mainPublish"/"pcMainPublish"，**无编辑重发场景**。
        //    传 itemId + scene=pcEdit 是瞎猜的，闲鱼端不认 pcEdit，按发新商品处理 → 改价后多出一个商品。
        //    所以闲鱼 PC 端改价只能走"发新商品 + 下架旧商品"路径，没有编辑重发这条路。
        JsonNode publishResult = publishApiService.publishItem(
            null, title, description, newPriceCent, origPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 5. 发布成功才下架原商品；失败抛异常保留原商品，避免丢失在售
        if (!isPublishSuccess(publishResult)) {
            throw new IllegalStateException("改价：" + describePublishFailure(publishResult));
        }
        shelfOff(itemId);

        return publishResult;
    }

    /**
     * 调整商品库存 — 完整流程：获取原商品信息 → 改库存 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改库存接口，走「新建替代」路径：不传 itemId 给 publishItem（pcMainPublish），
     * 生成新商品后，将原来的商品下架，实现改库存效果。</p>
     *
     * @param itemId  要改库存的原商品 id
     * @param stock   新库存数量（如 "50"），直接传给发布接口的 quantity
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode updateStock(String itemId, String stock) {
        return updateStock(itemId, stock, null, null);
    }

    /**
     * 改库存（值覆盖版）— 与 {@link #updateStock(String, String)} 相同流程，但允许调用方显式传
     * 本地 DB 已存的售价（分）/ 原价（分），SDK 优先用传入值而非从闲鱼详情重新提取。
     * <p>背景：闲鱼详情响应里价格字段路径不可靠（真抓多次验证位置不固定），
     * 本地 DB 存的是正确值（发布时写入），改库存时直接透传，避免价格被兜底成 0.02、原价变 0.1。</p>
     *
     * @param itemId          要改库存的原商品 id
     * @param stock           新库存数量（如 "50"），直接传给发布接口的 quantity
     * @param priceCentOverride    售价（分，本地 DB 值，非空则覆盖从详情提取）
     * @param origPriceCentOverride 原价（分，本地 DB 值，非空则覆盖从详情提取）
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode updateStock(String itemId, String stock, String priceCentOverride, String origPriceCentOverride) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updateStock 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        // 1. 获取原商品详情（完整字段）
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        // 2. 提取原商品字段，构造发布参数
        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        // 售价/原价：优先用调用方传入的本地 DB 值（可靠），否则才从详情提取（兜底）
        String priceCent = (priceCentOverride != null && !priceCentOverride.isEmpty())
                ? priceCentOverride : extractPrice(itemDO);
        String origPriceCent = (origPriceCentOverride != null && !origPriceCentOverride.isEmpty())
                ? origPriceCentOverride : extractOriginalPrice(itemDO);
        List<Map<String, Object>> images = extractImages(itemDO, detail);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        // 3. 发布新商品（itemId=null → pcMainPublish 场景，生成新商品）
        JsonNode publishResult = publishApiService.publishItem(
            null, title, description, priceCent, origPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 4. 发布成功才下架原商品；失败抛异常保留原商品，避免丢失在售
        if (!isPublishSuccess(publishResult)) {
            throw new IllegalStateException("改库存：" + describePublishFailure(publishResult));
        }
        shelfOff(itemId);

        return publishResult;
    }

    /** 调整商品原价 — 同 updatePrice 路径，一并修改 */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updateOriginalPrice 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        String priceCent = extractPrice(itemDO);                // 售价不变
        String stock = extractStock(itemDO);                    // 库存不变
        List<Map<String, Object>> images = extractImages(itemDO, detail);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        String newOrigPriceCent = priceToCent(originalPrice);

        JsonNode publishResult = publishApiService.publishItem(
            null, title, description, priceCent, newOrigPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 发布成功才下架原商品；失败抛异常保留原商品，避免丢失在售
        if (!isPublishSuccess(publishResult)) {
            throw new IllegalStateException("改原价：" + describePublishFailure(publishResult));
        }
        shelfOff(itemId);
        return publishResult;
    }

    /**
     * 判断发布结果是否成功：ret[0] 为空或含 SUCCESS 视为成功；FAIL_ 前缀视为失败。
     */
    private boolean isPublishSuccess(JsonNode resp) {
        if (resp == null) return false;
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            String r0 = ret.get(0).asText("");
            return r0.isEmpty() || r0.contains("SUCCESS");
        }
        return true;
    }

    /**
     * 从闲鱼响应 ret 数组拿第一个错误码，识别风控拦截返清晰错误提示。
     * 风控码特征：含 RGV587_ERROR / FAIL_SYS_USER_VALIDATE / punish / captcha，
     * 闲鱼会返验证码挑战页（data.url 含 punish?action=captcha）。
     */
    private String describePublishFailure(JsonNode resp) {
        if (resp == null) return "闲鱼返回空响应";
        JsonNode ret = resp.path("ret");
        if (!ret.isArray() || ret.size() == 0) return "闲鱼响应无 ret";
        String r0 = ret.get(0).asText("");
        // 风控拦截：返清晰提示让用户知道是闲鱼验拦不是 bug
        if (r0.contains("RGV587_ERROR") || r0.contains("FAIL_SYS_USER_VALIDATE")
                || r0.contains("punish") || r0.contains("captcha")) {
            return "闲鱼风控拦截（验证码挑战），请稍后重试或在闲鱼 PC 端完成验证后重试";
        }
        // 令牌过期：提示重新登录
        if (r0.contains("FAIL_SYS_TOKEN_EXOIRED") || r0.contains("TOKEN_EXPIRED")) {
            return "闲鱼令牌过期，请重新登录账号后再试";
        }
        // 其他业务错误：透传原始码
        return "闲鱼端返回错误: " + r0;
    }

    // ==================== 字段提取方法 ====================

    /** 提取标题 */
    private String extractTitle(JsonNode itemDO) {
        return itemDO.path("title").asText("");
    }

    /** 提取描述 */
    private String extractDescription(JsonNode itemDO) {
        return itemDO.path("desc").asText(itemDO.path("description").asText(""));
    }

    /**
     * 提取售价（分）— 真抓验证（2026-08-04 p_publish-index.js bundle）：闲鱼价格在
     * itemPriceDTO.priceInCent（嵌套对象，单位分）。旧版 priceInfo.price / soldPrice / price
     * 三个字段在真实响应里取不到 → 返回 "0" → 改库存后价格变 0.02 元的 bug。
     */
    private String extractPrice(JsonNode itemDO) {
        // 1. 真抓主路径：itemDO.itemPriceDTO.priceInCent（分）
        JsonNode priceInCent = itemDO.path("itemPriceDTO").path("priceInCent");
        if (!priceInCent.isMissingNode() && !priceInCent.isNull() && priceInCent.asLong() > 0) {
            return String.valueOf(priceInCent.asLong());
        }
        // 2. 兜底：priceInfo.price（分）
        JsonNode priceNode = itemDO.path("priceInfo").path("price");
        if (!priceNode.isMissingNode() && !priceNode.isNull() && priceNode.asLong() > 0) {
            return String.valueOf(priceNode.asLong());
        }
        // 3. 兜底：soldPrice 字段（分）
        JsonNode soldPrice = itemDO.path("soldPrice");
        if (!soldPrice.isMissingNode() && !soldPrice.isNull() && soldPrice.asLong() > 0) {
            return String.valueOf(soldPrice.asLong());
        }
        // 4. 再兜底：price 字段（可能带小数点，是元）
        JsonNode price = itemDO.path("price");
        if (!price.isMissingNode() && !price.isNull() && price.asDouble() > 0) {
            return priceToCent(price.asText());
        }
        return "0";
    }

    /**
     * 提取原价（分）— 真抓：itemDO.itemPriceDTO.origPriceInCent（嵌套对象，分）
     */
    private String extractOriginalPrice(JsonNode itemDO) {
        // 1. 真抓主路径：itemDO.itemPriceDTO.origPriceInCent（分）
        JsonNode origInCent = itemDO.path("itemPriceDTO").path("origPriceInCent");
        if (!origInCent.isMissingNode() && !origInCent.isNull() && origInCent.asLong() > 0) {
            return String.valueOf(origInCent.asLong());
        }
        // 2. 兜底：originalPrice（分）
        JsonNode origPrice = itemDO.path("originalPrice");
        if (!origPrice.isMissingNode() && !origPrice.isNull() && origPrice.asLong() > 0) {
            return String.valueOf(origPrice.asLong());
        }
        // 无原价时用售价兜底
        return extractPrice(itemDO);
    }

    /**
     * 提取库存 — 真抓验证闲鱼 bundle 里字段名是 quantity，但详情响应里位置可能在
     * itemDO / b2cItemDO / data 根，且 spuQuantity（SPU 总库存）兜底。
     * 旧版只走 itemDO.quantity，取不到直接返回 "1" → 改价后库存变 1 的 bug。
     */
    private String extractStock(JsonNode itemDO) {
        return extractStock(itemDO, null);
    }

    /** 重载版：detail 传入以兜底 b2cItemDO.quantity / data.quantity / spuQuantity */
    private String extractStock(JsonNode itemDO, JsonNode detail) {
        // 1. itemDO.quantity（主路径）
        JsonNode q = itemDO.path("quantity");
        if (!q.isMissingNode() && !q.isNull() && q.asInt() > 0) {
            return String.valueOf(q.asInt());
        }
        // 2. itemDO.spuQuantity（SPU 总库存兜底）
        q = itemDO.path("spuQuantity");
        if (!q.isMissingNode() && !q.isNull() && q.asInt() > 0) {
            return String.valueOf(q.asInt());
        }
        // 3. detail.data.b2cItemDO.quantity（b2c 模式）
        if (detail != null) {
            JsonNode b2c = detail.path("data").path("b2cItemDO").path("quantity");
            if (!b2c.isMissingNode() && !b2c.isNull() && b2c.asInt() > 0) {
                return String.valueOf(b2c.asInt());
            }
            // 4. detail.data.quantity（根兜底）
            JsonNode dq = detail.path("data").path("quantity");
            if (!dq.isMissingNode() && !dq.isNull() && dq.asInt() > 0) {
                return String.valueOf(dq.asInt());
            }
        }
        return "1"; // 全兜底也取不到，保留默认 1（极少数情况）
    }

    /**
     * 提取图片列表 → publishItem 需要的 imageInfoList（含 url/height/width）
     * <p>真实抓包验证（2026-08-04 调 mtop.taobao.idle.pc.detail）：图片在 {@code itemDO.imageInfos[]}，
     * 每项含 url / major / photoSearchUrl 等；主图兜底在 {@code trackParams.mainPic}（在 detail 根不在 itemDO）。
     * 旧版 picInfo / picPath / picDetailDO 字段在真实响应里不存在，仅保留兼容兜底。</p>
     */
    private List<Map<String, Object>> extractImages(JsonNode itemDO) {
        return extractImages(itemDO, null);
    }

    /** 重载版：detail 传入以兜底 trackParams.mainPic（imageInfos 也空时用） */
    private List<Map<String, Object>> extractImages(JsonNode itemDO, JsonNode detail) {
        List<Map<String, Object>> images = new ArrayList<>();

        // 1. 真抓主路径：itemDO.imageInfos[]（每项含 url / major / 尺寸）
        JsonNode imageInfos = itemDO.path("imageInfos");
        if (imageInfos.isArray() && !imageInfos.isEmpty()) {
            for (JsonNode img : imageInfos) {
                // imageInfos 里 url 字段名可能是 url / picUrl / path
                String url = firstNonBlank(
                        img.path("url").asText(""), img.path("picUrl").asText(""), img.path("path").asText(""));
                if (url.isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("url", url);
                item.put("height", img.path("height").asInt(img.path("heightSize").asInt(800)));
                item.put("width", img.path("width").asInt(img.path("widthSize").asInt(800)));
                images.add(item);
            }
        }

        // 2. 兜底旧字段名（极个别账号响应结构差异）：picInfo / picPath / picDetailDO / imageList
        if (images.isEmpty()) {
            JsonNode picInfo = itemDO.path("picInfo");
            if (picInfo.isArray() && !picInfo.isEmpty()) {
                for (JsonNode pic : picInfo) addImageFromPicNode(images, pic);
            } else if (!itemDO.path("picPath").asText("").isEmpty()) {
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("url", itemDO.path("picPath").asText(""));
                img.put("height", itemDO.path("picHeight").asInt(800));
                img.put("width", itemDO.path("picWidth").asInt(800));
                images.add(img);
            }
            JsonNode picDetailDO = itemDO.path("picDetailDO");
            if (picDetailDO.isArray() && !picDetailDO.isEmpty()) {
                for (JsonNode pic : picDetailDO) addImageFromPicNode(images, pic);
            }
            JsonNode imageList = itemDO.path("imageList");
            if (imageList.isArray() && !imageList.isEmpty()) {
                for (JsonNode img : imageList) {
                    String url = img.path("url").asText(img.path("path").asText(""));
                    if (url.isEmpty()) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("url", url);
                    item.put("height", img.path("height").asInt(800));
                    item.put("width", img.path("width").asInt(800));
                    images.add(item);
                }
            }
        }

        // 3. 最终兜底：trackParams.mainPic（真抓验证，在 detail 根不在 itemDO）
        if (images.isEmpty() && detail != null) {
            String mainPic = detail.path("trackParams").path("mainPic").asText("");
            if (!mainPic.isEmpty()) {
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("url", mainPic);
                img.put("height", 800);
                img.put("width", 800);
                images.add(img);
            }
        }
        return images;
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isEmpty()) return v;
        return "";
    }

    private void addImageFromPicNode(List<Map<String, Object>> images, JsonNode pic) {
        String url = pic.path("url").asText(pic.path("path").asText(""));
        if (url.isEmpty()) return;
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("url", url);
        img.put("height", pic.path("height").asInt(pic.path("heightSize").asInt(800)));
        img.put("width", pic.path("width").asInt(pic.path("widthSize").asInt(800)));
        images.add(img);
    }

    /**
     * 提取分类信息 → catDTO {catId, catName, channelCatId, leafId, tbCatId}
     * <p>真抓验证（2026-08-04 调 mtop.taobao.idle.pc.detail）：类目在嵌套对象 {@code itemDO.itemCatDTO}，
     * 含 catId/channelCatId/leafId/tbCatId/rootChannelCatId/level2ChannelCatId/level3ChannelCatId。
     * 兜底在 {@code detail.data.trackParams}（categoryId/channelCatId/rootChannelCatId）。
     * 旧版 itemDO.categoryId / itemDO.channelCatId 扁平字段在真实响应里不存在。</p>
     */
    private Map<String, String> extractCatDTO(JsonNode itemDO, JsonNode detail) {
        Map<String, String> catDTO = new LinkedHashMap<>();

        // 1. 真抓主路径：itemDO.itemCatDTO（嵌套对象，含完整 5 字段）
        JsonNode itemCatDTO = itemDO.path("itemCatDTO");
        if (!itemCatDTO.isMissingNode() && !itemCatDTO.isNull()) {
            putIfNonBlank(catDTO, "catId", itemCatDTO.path("catId").asText(""));
            putIfNonBlank(catDTO, "catName", itemCatDTO.path("catName").asText(itemCatDTO.path("categoryName").asText("")));
            putIfNonBlank(catDTO, "channelCatId", itemCatDTO.path("channelCatId").asText(""));
            putIfNonBlank(catDTO, "leafId", itemCatDTO.path("leafId").asText(""));
            putIfNonBlank(catDTO, "tbCatId", itemCatDTO.path("tbCatId").asText(""));
        }

        // 2. 兜底：itemDO 扁平字段（categoryId 兜底 catId；channelCatId/leafId/tbCatId 旧路径兼容）
        if (catDTO.get("catId") == null) {
            putIfNonBlank(catDTO, "catId", itemDO.path("categoryId").asText(""));
        }
        if (catDTO.get("channelCatId") == null) {
            putIfNonBlank(catDTO, "channelCatId", itemDO.path("channelCatId").asText(""));
        }
        if (catDTO.get("leafId") == null) {
            putIfNonBlank(catDTO, "leafId", itemDO.path("leafId").asText(""));
        }
        if (catDTO.get("tbCatId") == null) {
            putIfNonBlank(catDTO, "tbCatId", itemDO.path("tbCatId").asText(""));
        }
        if (catDTO.get("catName") == null) {
            putIfNonBlank(catDTO, "catName", itemDO.path("categoryName").asText(""));
        }

        // 3. 最终兜底：detail.data.trackParams（categoryId/channelCatId/rootChannelCatId 都有）
        if (detail != null) {
            JsonNode trackParams = detail.path("data").path("trackParams");
            if (catDTO.get("catId") == null) {
                putIfNonBlank(catDTO, "catId", trackParams.path("categoryId").asText(""));
            }
            if (catDTO.get("channelCatId") == null) {
                // trackParams.channelCatId 是真值；rootChannelCatId 是兜底
                String cc = trackParams.path("channelCatId").asText("");
                if (cc.isEmpty()) cc = trackParams.path("rootChannelCatId").asText("");
                putIfNonBlank(catDTO, "channelCatId", cc);
            }
        }
        return catDTO;
    }

    private void putIfNonBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    /** 提取属性标签列表 */
    private List<Map<String, Object>> extractLabelExtList(JsonNode itemDO) {
        List<Map<String, Object>> labels = new ArrayList<>();
        JsonNode labelList = itemDO.path("itemLabelExtList");
        if (labelList.isArray()) {
            for (JsonNode label : labelList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("labelId", label.path("labelId").asText(""));
                item.put("labelName", label.path("labelName").asText(""));
                item.put("labelValue", label.path("labelValue").asText(""));
                labels.add(item);
            }
        }
        return labels;
    }

    /** 提取所在地 → addrDTO {area, city, divisionId, gps, poiId, poi} */
    private Map<String, Object> extractAddrDTO(JsonNode itemDO) {
        Map<String, Object> addr = new LinkedHashMap<>();
        JsonNode location = itemDO.path("location");
        if (!location.isMissingNode()) {
            addr.put("area", location.path("area").asText(""));
            addr.put("city", location.path("city").asText(""));
            addr.put("divisionId", location.path("divisionId").asText(""));
            addr.put("gps", location.path("gps").asText(""));
            addr.put("poiId", location.path("poiId").asText(""));
            addr.put("poi", location.path("poi").asText(""));
        } else {
            // 兜底：从 itemDO 直接拿
            addr.put("area", itemDO.path("area").asText(""));
            addr.put("city", itemDO.path("city").asText(""));
            addr.put("divisionId", itemDO.path("divisionId").asText(""));
        }
        return addr;
    }

    /** 提取运费设置 → deliverySettings {canFreeShipping, supportFreight, onlyTakeSelf, templateId, postPriceInCent} */
    private Map<String, Object> extractDeliverySettings(JsonNode itemDO) {
        Map<String, Object> delivery = new LinkedHashMap<>();
        JsonNode postFee = itemDO.path("postFeeDTO");
        if (postFee.isMissingNode()) {
            postFee = itemDO.path("postFee");
        }
        if (!postFee.isMissingNode()) {
            delivery.put("canFreeShipping", postFee.path("canFreeShipping").asBoolean(false));
            delivery.put("supportFreight", postFee.path("supportFreight").asBoolean(false));
            delivery.put("onlyTakeSelf", postFee.path("onlyTakeSelf").asBoolean(false));
            if (postFee.path("templateId").isValueNode()) {
                delivery.put("templateId", postFee.path("templateId").asText(""));
            }
            if (postFee.path("postPriceInCent").isValueNode()) {
                delivery.put("postPriceInCent", postFee.path("postPriceInCent").asText(""));
            }
        } else {
            // 默认包邮
            delivery.put("canFreeShipping", true);
            delivery.put("supportFreight", false);
            delivery.put("onlyTakeSelf", false);
        }
        return delivery;
    }

    /** 价格：元 → 分（如 "99.00" → "9900"） */
    private String priceToCent(String priceYuan) {
        if (priceYuan == null || priceYuan.isEmpty()) return "0";
        try {
            double p = Double.parseDouble(priceYuan);
            return String.valueOf(Math.round(p * 100));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    // ==================== 商品分类 ====================

    /** 获取可用分类列表 — mtop.taobao.idlecategory.list */
    public JsonNode getCategoryList(String parentId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parentId", parentId != null ? parentId : "0");
        return apiClient.callMtop("mtop.taobao.idlecategory.list", toJson(data));
    }

    /** AI 智能推荐分类 — mtop.taobao.idlecategory.recommend */
    public JsonNode recommendCategory(String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.taobao.idlecategory.recommend", toJson(data));
    }

    // ==================== 商品删除 ====================

    /**
     * 删除商品 — 真实接口 com.taobao.idle.item.delete v1.1
     * <p>真抓验证（2026-08-04 闲鱼 PC 商品详情页 bundle p_item-index.js）：
     * "删除" 按钮 onClick handler 源码直接调
     * {@code ev.G({api:"com.taobao.idle.item.delete", v:"1.1", data:{itemId: eM}})}。
     * 之前用的 mtop.alibaba.idle.seller.pc.item.delete 是参考项目验证的，本项目真抓确认不对，
     * 闲鱼 PC 端删除商品走 com.taobao.idle.item.delete v1.1。</p>
     */
    public JsonNode deleteProduct(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("com.taobao.idle.item.delete", "1.1", toJson(data));
    }

    /**
     * 商品擦亮（提升曝光排名）— 真实接口 mtop.taobao.idle.item.polish v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply scheduler.polish_task 已真验通）：
     * 闲鱼定时擦亮任务走 mtop.taobao.idle.item.polish，data={itemId}，
     * spm_cnt=a21ybx.item.0.0 / spm_pre=a21ybx.personal.feeds.1.42f86ac21eZ9zd</p>
     */
    public JsonNode polishItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.polish", "1.0", toJson(data));
    }

    /** 批量删除商品 — mtop.taobao.idlehome.item.batch.delete */
    public JsonNode batchDeleteProducts(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.batch.delete", toJson(data));
    }

    // ==================== 商品复制 ====================

    /** 复制商品（一键转卖）— mtop.taobao.idlehome.item.copy */
    public JsonNode copyProduct(String sourceItemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceItemId", sourceItemId != null ? sourceItemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.copy", toJson(data));
    }

    // ==================== 商品状态查询 ====================

    /** 获取商品完整状态信息 — mtop.taobao.idlehome.item.fullinfo.get */
    public JsonNode getProductFullInfo(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.fullinfo.get", toJson(data));
    }

    /** 获取商品浏览量统计 — mtop.taobao.idlehome.item.viewstats.get */
    public JsonNode getViewStats(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.viewstats.get", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
