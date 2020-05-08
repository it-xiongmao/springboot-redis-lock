package com.bruceliu.test;

import com.bruceliu.App;
import com.bruceliu.service.SkillService;
import com.bruceliu.service.impl.SkillServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.test
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:45
 * @Description: TODO
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class)
public class TestSkill {

    @Test
    public void testSkill(){
        SkillService service = new SkillServiceImpl();
        for (int i = 0; i < 50; i++) {
            ThreadA threadA = new ThreadA(service);
            threadA.setName("ThreadNameA->"+i);
            threadA.start();
        }

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
        }
    }

}

class ThreadA extends Thread {

    private SkillService skillService;

    public ThreadA(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public void run() {
        skillService.seckill(1L,5L);
    }
}
