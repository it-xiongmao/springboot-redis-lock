package com.bruceliu.test;

import com.bruceliu.App;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.test
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 12:57
 * @Description: TODO
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class)
public class TestRedis {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 添加一个字符串
     */
    @Test
    public void testSet(){
        this.redisTemplate.opsForValue().set("key", "bruceliu...");
    }

    /**
     * 获取一个字符串
     */
    @Test
    public void testGet(){
        String value = (String)this.redisTemplate.opsForValue().get("key");
        System.out.println(value);
    }

    /**
     * 查询指定的值
     */
    @Test
    public void testGetValue() {
        Long count = (Long) redisTemplate.boundHashOps("goods_info").get(1L);
        System.out.println(count);
    }

}
