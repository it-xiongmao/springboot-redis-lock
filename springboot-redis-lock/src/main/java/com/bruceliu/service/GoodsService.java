package com.bruceliu.service;

import com.bruceliu.pojo.Goods;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.service
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:08
 * @Description: TODO
 */
public interface GoodsService {


    /**
     * 01-更新商品库存
     * @param goods
     * @return
     */
    Integer updateGoodsStock(Goods goods);

    /**
     * 02-加载商品信息
     * @return
     */
    List<Goods> findGoods();
}
