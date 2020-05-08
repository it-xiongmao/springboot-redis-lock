package com.bruceliu.service;

import com.bruceliu.pojo.Goods;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.service
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:27
 * @Description: TODO
 */
public interface SkillService {

    public Integer seckill(Long goodsId,Long goodsStock);
}
