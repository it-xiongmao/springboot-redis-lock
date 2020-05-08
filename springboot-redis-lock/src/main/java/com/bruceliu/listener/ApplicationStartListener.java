package com.bruceliu.listener;

import com.bruceliu.pojo.Goods;
import com.bruceliu.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.listener
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:07
 * @Description: TODO
 */
@Configuration
public class ApplicationStartListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    GoodsService goodsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("Web项目启动");
        List<Goods> goodsList = goodsService.findGoods();
        for (Goods goods : goodsList) {
            redisTemplate.boundHashOps("goods_info").put(goods.getGoods_id(), goods.getGoods_stock());
            System.out.println(goods);
        }
    }
}
