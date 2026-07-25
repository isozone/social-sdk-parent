package cn.net.rjnetwork.xianyu.manager.order.rate.mapper;

import cn.net.rjnetwork.xianyu.manager.order.rate.model.RedFlowerConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RedFlowerConfigMapper extends BaseMapper<RedFlowerConfig> {
    @Select("SELECT * FROM red_flower_config WHERE (account_id = #{accountId} OR account_id IS NULL) AND enabled = 1 AND deleted = 0 ORDER BY account_id DESC LIMIT 1")
    RedFlowerConfig selectEffectiveForAccount(@Param("accountId") Long accountId);
}
