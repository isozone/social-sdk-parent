package cn.net.rjnetwork.xianyu.manager.task.mapper;

import cn.net.rjnetwork.xianyu.manager.task.model.ScheduledTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {
    @Select("SELECT * FROM scheduled_task WHERE task_key = #{taskKey} LIMIT 1")
    ScheduledTask selectByTaskKey(@Param("taskKey") String taskKey);
}
