package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    /** 自动发货任务 mapper（订单详情拉自动发货记录用，构造器已注入 shipTaskMapper） */
    private final VirtualShipTaskMapper shipTaskMapper;
    /** 商品 mapper（订单详情反查关联本地商品用） */
    private final ProductMapper productMapper;

    public OrderService(OrderMapper orderMapper, VirtualShipTaskMapper shipTaskMapper, ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.shipTaskMapper = shipTaskMapper;
        this.productMapper = productMapper;
    }

    public Page<XianyuOrder> listPage(int pageNum, int pageSize, Long accountId, String type) {
        Page<XianyuOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuOrder::getAccountId, accountId);
        }
        if ("SOLD".equals(type)) {
            wrapper.eq(XianyuOrder::getType, "SOLD");
        } else if ("BOUGHT".equals(type)) {
            wrapper.eq(XianyuOrder::getType, "BOUGHT");
        }
        wrapper.orderByDesc(XianyuOrder::getUpdatedAt);
        orderMapper.selectPage(page, wrapper);
        page.getRecords().forEach(this::fillVirtualShipTask);
        return page;
    }

    public XianyuOrder getById(Long id) {
        XianyuOrder order = orderMapper.selectById(id);
        fillVirtualShipTask(order);
        return order;
    }

    public XianyuOrder delivery(Long orderId, String trackingNo) {
        XianyuOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        order.setTrackingNo(trackingNo);
        order.setStatus("SHIPPED");
        orderMapper.updateById(order);
        return order;
    }

    public void saveOrder(XianyuOrder order) {
        order.setCreatedAt(java.time.LocalDateTime.now());
        order.setUpdatedAt(java.time.LocalDateTime.now());
        orderMapper.insert(order);
    }

    private void fillVirtualShipTask(XianyuOrder order) {
        if (order == null) return;
        VirtualShipTask task = shipTaskMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipTask>()
                        .eq(VirtualShipTask::getOrderId, order.getId())
                        .last("LIMIT 1"));
        if (task == null) return;
        order.setVirtualShipTaskStatus(task.getStatus());
        order.setVirtualShipTaskError(task.getErrorMessage());
        order.setVirtualShipExecuteAt(task.getExecuteAt());
    }

    /**
     * 拉订单详情：聚合订单本体 + 商品信息 + 自动发货记录，给前端详情抽屉用。
     * 物流轨迹详情接口不在本次范围，前端展示订单存的 trackingNo 即可。
     * @param orderId 本地订单主键 id
     * @return Map 含 order（订单本体）/ product（商品信息，可 null）/ shipTasks（自动发货记录列表）/ shipTrack（预留 null）
     */
    public Map<String, Object> getOrderDetail(Long orderId) {
        XianyuOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);

        // 商品信息：优先按 order.getProductId()（本地关联商品 id）反查；没有再按 itemId 兜底
        XianyuProduct product = null;
        try {
            if (order.getProductId() != null) {
                product = productMapper.selectById(order.getProductId());
            } else if (order.getItemId() != null) {
                product = productMapper.selectOne(
                        new LambdaQueryWrapper<XianyuProduct>()
                                .eq(XianyuProduct::getItemId, order.getItemId())
                                .last("LIMIT 1"));
            }
        } catch (Exception ignored) {}
        detail.put("product", product);

        // 自动发货记录：VirtualShipTask.orderId 是本地订单主键（Long），按 order.getId() 反查全部记录
        List<VirtualShipTask> shipTasks = new ArrayList<>();
        try {
            LambdaQueryWrapper<VirtualShipTask> w = new LambdaQueryWrapper<>();
            w.eq(VirtualShipTask::getOrderId, order.getId());
            w.orderByDesc(VirtualShipTask::getId);
            shipTasks = shipTaskMapper.selectList(w);
        } catch (Exception ignored) {}
        detail.put("shipTasks", shipTasks);

        // 物流轨迹详情接口不在本次范围，前端展示订单存的 trackingNo 即可
        detail.put("shipTrack", null);

        return detail;
    }
}
