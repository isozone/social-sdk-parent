package cn.net.rjnetwork.xianyu.manager.virtual.mapper;

import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CardItemRelationMapper extends BaseMapper<CardItemRelation> {
    @Select("SELECT * FROM card_item_relation WHERE product_id = #{productId} AND enabled = 1 AND deleted = 0 ORDER BY priority ASC")
    List<CardItemRelation> selectEnabledByProductId(@Param("productId") Long productId);
}
