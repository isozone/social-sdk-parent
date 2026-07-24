package cn.net.rjnetwork.xianyu.manager.message.mapper;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<XianyuMessage> {

    // 按 MAX(message_time) 聚合排序，保证最新活跃会话排最前。
    // 旧版 SELECT DISTINCT ... ORDER BY message_time 在 MySQL 下行为未定义（同秒多条乱序），
    // 且对历史脏数据 direction 全 INCOMING 的情况也无法正确兜底。
    @Select("SELECT session_id FROM xianyu_message WHERE account_id = #{accountId} AND deleted = 0 " +
            "GROUP BY session_id ORDER BY MAX(message_time) DESC")
    List<String> selectDistinctSessions(@Param("accountId") Long accountId);

    // 取「该会话最新一条」消息用于会话列表展示 lastContent / lastTime。
    // 关键：ORDER BY message_time DESC, id DESC —— 当多条消息同一秒时，id 更大（后入库）的才是真正最新，
    // 仅按 message_time DESC 取 LIMIT 1 可能取到中间一条，导致会话列表显示错误摘要。
    @Select("SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} " +
            "AND deleted = 0 ORDER BY message_time DESC, id DESC LIMIT 1")
    XianyuMessage selectLatestBySession(@Param("accountId") Long accountId, @Param("sessionId") String sessionId);

    // 历史拉取：内层取 limit 条最新，外层按时间正序返回（最老在前、最新在后），与闲鱼客户端顺序一致。
    // 同样需要 ORDER BY message_time DESC, id DESC 保证同一秒内按入库顺序倒排。
    @Select("SELECT * FROM (SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} " +
            "AND deleted = 0 ORDER BY message_time DESC, id DESC LIMIT #{limit}) t " +
            "ORDER BY t.message_time ASC, t.id ASC")
    List<XianyuMessage> selectBySession(@Param("accountId") Long accountId, @Param("sessionId") String sessionId, @Param("limit") int limit);

    // selectLatestIncoming 已废弃：依赖 direction='INCOMING' 字段，但历史数据 direction 全是 INCOMING，
    // 会导致把「自己发的消息」误判为对方。统一改用 selectLatestPeerMessage（按 senderId 排除自己）。
    @Deprecated
    @Select("SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} AND deleted = 0 AND direction = 'INCOMING' ORDER BY message_time DESC LIMIT 1")
    XianyuMessage selectLatestIncoming(@Param("accountId") Long accountId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} AND deleted = 0 AND sender_id != #{selfBare} AND sender_id != #{selfFull} ORDER BY message_time DESC LIMIT 1")
    XianyuMessage selectLatestPeerMessage(@Param("accountId") Long accountId,
                                          @Param("sessionId") String sessionId,
                                          @Param("selfBare") String selfBare,
                                          @Param("selfFull") String selfFull);
}
