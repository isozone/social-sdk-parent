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
        String resp = "{\"data\":{\"categoryPredictResult\":{" +
                "\"catId\":\"50023914\"," +
                "\"catName\":\"手机\"," +
                "\"channelCatId\":\"4d8b31d719602249ac899d2620c5df2b\"," +
                "\"leafId\":\"leaf123\"," +
                "\"tbCatId\":\"50023914\"" +
                "}}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("50023914", catDTO.get("catId"));
        assertEquals("手机", catDTO.get("catName"));
        assertEquals("4d8b31d719602249ac899d2620c5df2b", catDTO.get("channelCatId"));
        assertEquals("leaf123", catDTO.get("leafId"));
        assertEquals("50023914", catDTO.get("tbCatId"));
    }

    @Test
    void buildItemCatDTO_fallbackResultPath_fillsFields() throws Exception {
        ProductService svc = newProductService();
        // data.categoryPredictResult 缺失 → 兜底 data.result.categoryPredictResult
        String resp = "{\"data\":{\"result\":{\"categoryPredictResult\":{" +
                "\"catId\":\"c1\",\"channelCatId\":\"cc1\"" +
                "}}}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("c1", catDTO.get("catId"));
        assertEquals("cc1", catDTO.get("channelCatId"));
    }

    @Test
    void buildItemCatDTO_userCategoryIdOverridesCatId() throws Exception {
        ProductService svc = newProductService();
        String resp = "{\"data\":{\"categoryPredictResult\":{" +
                "\"catId\":\"AI_CAT\",\"channelCatId\":\"CC\"" +
                "}}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), "USER_CAT");
        assertEquals("USER_CAT", catDTO.get("catId"));
        assertEquals("CC", catDTO.get("channelCatId"), "channelCatId 仍取 AI 推荐结果");
    }

    @Test
    void buildItemCatDTO_emptyResponse_returnsEmptyMap() throws Exception {
        ProductService svc = newProductService();
        Map<String, String> catDTO = svc.buildItemCatDTO(null, null);
        assertNotNull(catDTO);
        assertTrue(catDTO.isEmpty(), "空响应应返回空 map，由调用方校验拦截");
    }

    @Test
    void buildItemCatDTO_missingChannelCatId_returnsEmptyChannelCatId() throws Exception {
        ProductService svc = newProductService();
        // AI 推荐返回但缺 channelCatId（模拟接口名对但响应字段不全）
        String resp = "{\"data\":{\"categoryPredictResult\":{" +
                "\"catId\":\"c1\",\"catName\":\"手机\"" +
                "}}}";
        Map<String, String> catDTO = svc.buildItemCatDTO(json(resp), null);
        assertEquals("c1", catDTO.get("catId"));
        assertEquals("", catDTO.get("channelCatId"), "channelCatId 缺失，调用方校验应拦截");
    }
}
