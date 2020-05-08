package com.bruceliu.controller;

import com.bruceliu.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.controller
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 15:14
 * @Description: TODO
 */
@RestController
@Scope("prototype")
public class SkillController {

    @Autowired
    SkillService skillService;

    @RequestMapping("/skill")
    public String skill(){
        Integer count = skillService.seckill(1L, 1L);
        if(count>0){
            return "下单成功"+count;
        }else{
            return "下单失败"+count;
        }
    }
}
