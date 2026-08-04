package cn.net.rjnetwork.xianyu.manager.order.mapper;

import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<XianyuOrder> {

    /**
     * 虚拟发货后只更发货相关字段，<b>不碰 raw_data 大字段</b>。
     * <p>BaseMapper.updateById 会 UPDATE 全列含 raw_data（闲鱼订单原始 JSON，大字段），
     * SQLite 写大字段慢 + 占着写锁 → 别的请求等不到连接 → 全线 30s 超时（现网症结）。
     * 本方法只更发货真正要改的 4 个字段，写得快、释放快。</p>
     */
    @Update("UPDATE xianyu_order SET status=#{status}, deliver_content=#{deliverContent}, "
            + "virtual_shipped_at=#{virtualShippedAt}, updated_at=#{updatedAt} "
            + "WHERE id=#{id} AND deleted=0")
    int updateShipFields(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("deliverContent") String deliverContent,
                         @Param("virtualShippedAt") LocalDateTime virtualShippedAt,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
