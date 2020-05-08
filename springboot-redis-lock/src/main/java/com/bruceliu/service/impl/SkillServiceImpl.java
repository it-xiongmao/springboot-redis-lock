package com.bruceliu.service.impl;

import com.bruceliu.lock.DistributedLock;
import com.bruceliu.mapper.GoodsMapper;
import com.bruceliu.pojo.Goods;
import com.bruceliu.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.Resource;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.service.impl
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:27
 * @Description: TODO
 */
@Service
public class SkillServiceImpl implements SkillService {

    private static JedisPool pool = null;
    private DistributedLock lock = new DistributedLock(pool);

    @Resource
    GoodsMapper goodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        // 设置最大连接数
        config.setMaxTotal(200);
        // 设置最大空闲数
        config.setMaxIdle(8);
        // 设置最大等待时间
        config.setMaxWaitMillis(1000 * 100);
        // 在borrow一个jedis实例时，是否需要验证，若为true，则所有jedis实例均是可用的
        config.setTestOnBorrow(true);
        pool = new JedisPool(config, "127.0.0.1", 6379, 3000);
    }

    @Override
    public Integer seckill(Long goodsId, Long goodsStock) {
        // 返回锁的value值，供释放锁时候进行判断
        String identifier = lock.lockWithTimeout("resource", 5000, 1000);
        //System.out.println(Thread.currentThread().getName() + "--------------->获得了锁");

        Long goods_stock = (Long) redisTemplate.boundHashOps("goods_info").get(goodsId);
        System.out.println(goodsId + "商品在Redis中库存:" + goods_stock);

        if (goods_stock > 0) {
            //1.查询数据库对象
            Goods goods = goodsMapper.findGoodsById(goodsId);
            //2.更新数据库中库存数量
            goods.setGoods_stock(goods.getGoods_stock() - goodsStock);
            Integer count = goodsMapper.updateGoodsStock(goods);
            System.out.println("更新数据库库存:" + count);
            //3.同步Redis中商品库存
            redisTemplate.boundHashOps("goods_info").put(goods.getGoods_id(), goods.getGoods_stock());
            lock.releaseLock("resource", identifier);
            System.out.println(Thread.currentThread().getName() + "--------------->释放了锁");
        } else {
            return -1;
        }
        return 1;
    }

}
