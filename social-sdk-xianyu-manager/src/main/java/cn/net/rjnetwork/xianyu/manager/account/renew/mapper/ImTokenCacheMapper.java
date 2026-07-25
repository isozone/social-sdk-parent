package cn.net.rjnetwork.xianyu.manager.account.renew.mapper;

import cn.net.rjnetwork.xianyu.manager.account.renew.model.ImTokenCache;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImTokenCacheMapper extends BaseMapper<ImTokenCache> {
    @Select("SELECT * FROM im_token_cache WHERE account_id = #{accountId} LIMIT 1")
    ImTokenCache selectByAccountId(@Param("accountId") Long accountId);
}
