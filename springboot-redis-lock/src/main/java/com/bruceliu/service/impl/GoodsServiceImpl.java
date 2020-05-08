package com.bruceliu.service.impl;

import com.bruceliu.mapper.GoodsMapper;
import com.bruceliu.pojo.Goods;
import com.bruceliu.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.service.impl
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:09
 * @Description: TODO
 */
@Service
public class GoodsServiceImpl implements GoodsService {

    @Resource
    GoodsMapper goodsMapper;

    @Override
    public Integer updateGoodsStock(Goods goods) {
        return goodsMapper.updateGoodsStock(goods);
    }

    @Override
    public List<Goods> findGoods() {
        return goodsMapper.findGoods();
    }
}
