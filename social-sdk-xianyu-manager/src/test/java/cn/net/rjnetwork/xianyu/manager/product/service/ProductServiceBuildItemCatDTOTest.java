package cn.net.rjnetwork.xianyu.manager.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ProductService.buildItemCatDTO 构造 itemCatDTO 的 5 字段完整性。
 * 根因：闲鱼 PC 发布页提交 itemCatDTO 时缺 channelCatId/leafId，
 *       闲鱼端做"渠道类目路径查询"必报 FAIL_BIZ_CHANNEL_CAT_ID_PATH_QUERY_ERROR。
 * 真接口来源：闲鱼 PC 发布页 p_publish-index.js 真抓验证，
 *           mtop.taobao.idle.kgraph.property.recommend v2.0，
 *           响应路径 data.categoryPredictResult。
 */
class ProductServiceBuildItemCatDTOTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 用反射构造 ProductService（不走 Spring），绕开 mapper/account 依赖 */
    private ProductService newProductService() throws Exception {
        // ProductMapper / AccountService / SyncProgressService 都用 null
        // buildItemCatDTO 不依赖它们，只用 pickText
        var ctor = ProductService.class.getDeclaredConstructor(
                cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper.class,
                cn.net.rjnetwork.xianyu.manager.account.service.AccountService.class,
                cn.net.rjnetwork.xianyu.manager.product.service.SyncProgressService.class
        );
        ctor.setAccessible(true);
        return ctor.newInstance(null, null, null);
    }

    private JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    void buildItemCatDTO_normalResponse_fillsAll5Fields() throws Exception {
        ProductService svc = newProductService();
        // 真抓响应结构（2026-08-04 验证）：data.cardList[].cardData.valuesList[]，取 isClicked=1
        String resp = "{\"data\":{\"cardList\":[{\"cardData\":{\"valuesList\":[" +
                "{\"catId\":\"50025386\",\"catName\":\"手机\"," +
                "\"channelCatId\":\"126862528\",\"leafId\":\"1377\",\"tbCatId\":\"1512\"," +
                "\"isClicked\":\"1\",\"score\":\"0.998\"}," +
                "{\"catId\":\"50025399\",\"catName\":\"手机回收\"," +
                "\"channelCatId\":\"201450518\",\"leafId\":\"\",\"tbCatId\":\"50600011\"," +
                "\"isClicked\":\"0\",\"score\":\"0.0004\"}" +
                "]}}]}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("50025386", catDTO.get("catId"));
        assertEquals("手机", catDTO.get("catName"));
        assertEquals("126862528", catDTO.get("channelCatId"));
        assertEquals("1377", catDTO.get("leafId"));
        assertEquals("1512", catDTO.get("tbCatId"));
    }

    @Test
    void buildItemCatDTO_noClicked_picksHighestScore() throws Exception {
        ProductService svc = newProductService();
        // 没有 isClicked=1 时，取 score 最高的兜底
        String resp = "{\"data\":{\"cardList\":[{\"cardData\":{\"valuesList\":[" +
                "{\"catId\":\"low\",\"channelCatId\":\"low_cc\",\"score\":\"0.1\"}," +
                "{\"catId\":\"high\",\"channelCatId\":\"high_cc\",\"score\":\"0.9\"}" +
                "]}}]}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("high", catDTO.get("catId"));
        assertEquals("high_cc", catDTO.get("channelCatId"));
    }

    @Test
    void buildItemCatDTO_userCategoryIdOverridesCatId() throws Exception {
        ProductService svc = newProductService();
        String resp = "{\"data\":{\"cardList\":[{\"cardData\":{\"valuesList\":[" +
                "{\"catId\":\"AI_CAT\",\"channelCatId\":\"CC\",\"isClicked\":\"1\"}" +
                "]}}]}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), "USER_CAT");
        assertEquals("USER_CAT", catDTO.get("catId"));
        assertEquals("CC", catDTO.get("channelCatId"), "channelCatId 仍取 AI 推荐结果");
    }

    @Test
    void buildItemCatDTO_emptyResponse_fallsBackToDefault() throws Exception {
        ProductService svc = newProductService();
        // 空响应不再抛错，用 DEFAULT_FALLBACK_CAT_ID 兜底让发布先通
        Map<String, String> catDTO = svc.buildItemCatDTO(null, null);
        assertNotNull(catDTO);
        assertEquals("50023914", catDTO.get("catId"));
        assertEquals("50023914", catDTO.get("channelCatId"));
    }

    @Test
    void buildItemCatDTO_missingChannelCatId_fallsBackToDefault() throws Exception {
        ProductService svc = newProductService();
        // AI 推荐返回但缺 channelCatId（模拟接口名对但响应字段不全）→ 兜底默认类目
        String resp = "{\"data\":{\"cardList\":[{\"cardData\":{\"valuesList\":[" +
                "{\"catId\":\"c1\",\"catName\":\"手机\",\"isClicked\":\"1\"}" +  // 缺 channelCatId
                "]}}]}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("c1", catDTO.get("catId"));
        assertEquals("50023914", catDTO.get("channelCatId"), "channelCatId 缺失时用默认值兜底");
    }

    @Test
    void buildItemCatDTO_legacyCategoryPredictResultPath_stillWorks() throws Exception {
        // 兜底：极个别账号响应不带 cardList，走旧字段名 categoryPredictResult 也能取到
        ProductService svc = newProductService();
        String resp = "{\"data\":{\"categoryPredictResult\":{" +
                "\"catId\":\"c1\",\"channelCatId\":\"cc1\"}}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("c1", catDTO.get("catId"));
        assertEquals("cc1", catDTO.get("channelCatId"));
    }
}
