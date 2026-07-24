package cn.net.rjnetwork.xianyu.manager.account.renew.mapper;

import cn.net.rjnetwork.xianyu.manager.account.renew.model.CookieRefreshSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CookieRefreshScheduleMapper extends BaseMapper<CookieRefreshSchedule> {
    @Select("SELECT * FROM cookie_refresh_schedule WHERE account_id = #{accountId} LIMIT 1")
    CookieRefreshSchedule selectByAccountId(@Param("accountId") Long accountId);
}
