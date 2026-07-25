package cn.net.rjnetwork.xianyu.manager.order.rate.mapper;

import cn.net.rjnetwork.xianyu.manager.order.rate.model.AutoRateConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AutoRateConfigMapper extends BaseMapper<AutoRateConfig> {
    @Select("SELECT * FROM auto_rate_config WHERE (account_id = #{accountId} OR account_id IS NULL) AND enabled = 1 AND deleted = 0 ORDER BY account_id DESC LIMIT 1")
    AutoRateConfig selectEffectiveForAccount(@Param("accountId") Long accountId);
}
