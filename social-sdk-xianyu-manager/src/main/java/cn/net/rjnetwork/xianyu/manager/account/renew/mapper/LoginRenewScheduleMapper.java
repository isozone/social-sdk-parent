package cn.net.rjnetwork.xianyu.manager.account.renew.mapper;

import cn.net.rjnetwork.xianyu.manager.account.renew.model.LoginRenewSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoginRenewScheduleMapper extends BaseMapper<LoginRenewSchedule> {
    @Select("SELECT * FROM login_renew_schedule WHERE account_id = #{accountId} LIMIT 1")
    LoginRenewSchedule selectByAccountId(@Param("accountId") Long accountId);
}
