package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportPreview;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportResult;
import cn.net.rjnetwork.xianyu.manager.product.service.LocalProductService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 本地商品批量导入 API：预览 + 确认写入 + 模板下载
 */
@RestController
@RequestMapping("/api/local-products/import")
public class LocalProductImportController {

    private final LocalProductService localProductService;

    public LocalProductImportController(LocalProductService localProductService) {
        this.localProductService = localProductService;
    }

    /** 预览导入（解析 CSV/Excel，返回前 10 条 + 错误明细，不写入 DB） */
    @PostMapping("/preview")
    public ApiResponse<LocalProductImportPreview> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deduplicate", required = false, defaultValue = "false") boolean deduplicate,
            @RequestParam(value = "overwriteDuplicate", required = false, defaultValue = "false") boolean overwriteDuplicate,
            @RequestParam(value = "defaultGoodsType", required = false, defaultValue = "PHYSICAL") String defaultGoodsType,
            @RequestParam(value = "imageStoragePath", required = false, defaultValue = "/uploads/local-products") String imageStoragePath,
            @RequestParam(value = "deliverContentSeparator", required = false, defaultValue = "\\|\\|\\|") String separator) {
        try {
            LocalProductImportRequest req = new LocalProductImportRequest();
            req.setFile(file);
            req.setDeduplicate(deduplicate);
            req.setOverwriteDuplicate(overwriteDuplicate);
            req.setDefaultGoodsType(defaultGoodsType);
            req.setImageStoragePath(imageStoragePath);
            req.setDeliverContentSeparator(separator);
            return ApiResponse.ok(localProductService.previewImport(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("PARSE_ERROR", "文件解析失败: " + e.getMessage());
        }
    }

    /** 确认导入（解析 CSV/Excel 写入 local_product 表，返回统计） */
    @PostMapping("/confirm")
    public ApiResponse<LocalProductImportResult> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deduplicate", required = false, defaultValue = "false") boolean deduplicate,
            @RequestParam(value = "overwriteDuplicate", required = false, defaultValue = "false") boolean overwriteDuplicate,
            @RequestParam(value = "defaultGoodsType", required = false, defaultValue = "PHYSICAL") String defaultGoodsType,
            @RequestParam(value = "imageStoragePath", required = false, defaultValue = "/uploads/local-products") String imageStoragePath,
            @RequestParam(value = "deliverContentSeparator", required = false, defaultValue = "\\|\\|\\|") String separator) {
        try {
            LocalProductImportRequest req = new LocalProductImportRequest();
            req.setFile(file);
            req.setDeduplicate(deduplicate);
            req.setOverwriteDuplicate(overwriteDuplicate);
            req.setDefaultGoodsType(defaultGoodsType);
            req.setImageStoragePath(imageStoragePath);
            req.setDeliverContentSeparator(separator);
            return ApiResponse.ok(localProductService.confirmImport(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("IMPORT_FAILED", "导入失败: " + e.getMessage());
        }
    }

    // ==================== 模板下载 ====================

    // 模板列定义（与 LocalProductService 列名常量严格对齐，10 列）
    private static final String[] TEMPLATE_HEADERS = {
            "account_name", "title", "price", "stock", "images",
            "goods_type", "deliver_type", "deliver_content_template",
            "description", "shipping_mode"
    };
    private static final String[][] TEMPLATE_SAMPLE_ROWS = {
            {"示例闲鱼账号", "001.聪明人的个人成长", "9.9", "100",
                    "https://cdn.example.com/img1.jpg;https://cdn.example.com/img2.jpg",
                    "PHYSICAL", "", "", "成体系的心智成长工具包", "NONE"},
            {"测试账号A", "002.如何阅读一本书", "19.9", "50",
                    "https://cdn.example.com/a.jpg",
                    "PHYSICAL", "", "", "经典阅读方法指南", "FREE"},
            {"测试账号B", "003.某虚拟卡密商品", "5.0", "999",
                    "https://cdn.example.com/v.jpg",
                    "VIRTUAL", "CARD", "card001|pwd001|||card002|pwd002|||card003|pwd003",
                    "虚拟卡密自动发货", "DISTANCE"}
    };
    private static final String TEMPLATE_HELP_TEXT =
            "shipping_mode: NONE=无需邮寄 / FREE=包邮 / DISTANCE=按距离计费；"
            + "images: 多图用逗号/分号/换行分隔；"
            + "deliver_content_template: 卡密/账号用 ||| 分隔每条；"
            + "goods_type: PHYSICAL=实物 / VIRTUAL=虚拟";

    /** 下载 CSV 模板（与 Excel 模板列完全一致，仅格式不同） */
    @GetMapping("/template.csv")
    public void downloadCsvTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=local-product-import.csv");
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", TEMPLATE_HEADERS)).append("\n");
        for (String[] row : TEMPLATE_SAMPLE_ROWS) {
            // CSV 转义：含逗号的字段用双引号包裹，内部双引号转义为""
            for (int i = 0; i < row.length; i++) {
                String cell = row[i];
                if (cell.contains(",") || cell.contains("\"")) {
                    cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                }
                if (i > 0) sb.append(",");
                sb.append(cell);
            }
            sb.append("\n");
        }
        // Excel 打开 CSV 默认按 ANSI 解码中文乱码，加 UTF-8 BOM 让 Excel 正确识别
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        try (OutputStream os = response.getOutputStream()) {
            os.write(bom);
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 下载 Excel 模板（.xlsx，由 Apache POI 动态生成，含表头 + 示例行 + 列说明 sheet） */
    @GetMapping("/template.xlsx")
    public void downloadExcelTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=local-product-import.xlsx");
        try (OutputStream os = response.getOutputStream()) {
            localProductService.writeExcelTemplate(os, TEMPLATE_HEADERS, TEMPLATE_SAMPLE_ROWS, TEMPLATE_HELP_TEXT);
        }
    }
}
