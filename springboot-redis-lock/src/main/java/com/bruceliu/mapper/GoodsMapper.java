package com.bruceliu.mapper;

import com.bruceliu.pojo.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.mapper
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 13:43
 * @Description: TODO
 */
@Mapper
public interface GoodsMapper {

    /**
     * 01-更新商品库存
     * @param goods
     * @return
     */
    @Update("update t_goods set goods_stock=#{goods_stock} where goods_id=#{goods_id}")
    Integer updateGoodsStock(Goods goods);

    /**
     * 02-加载商品信息
     * @return
     */
    @Select("select * from t_goods")
    List<Goods> findGoods();

    /**
     * 03-根据ID查询
     * @param goodsId
     * @return
     */
    @Select("select * from t_goods where goods_id=#{goods_id}")
    Goods findGoodsById(Long goodsId);
}
