package com.bruceliu.test;

import com.bruceliu.App;
import com.bruceliu.mapper.GoodsMapper;
import com.bruceliu.pojo.Goods;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.test
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 13:55
 * @Description: TODO
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class)
public class TestMyBatis {

    @Resource
    GoodsMapper goodsMapper;

    @Test
    public void testUpdateStock(){
        Goods goods=new Goods();
        goods.setGoods_id(1L);
        goods.setGoods_stock(2L);
        Integer count = goodsMapper.updateGoodsStock(goods);
        System.out.println(count>0?"更新成功":"更新失败");
    }

    @Test
    public void testFindGoods(){
        List<Goods> goodsList = goodsMapper.findGoods();
        for (Goods goods : goodsList) {
            System.out.println(goods);
        }
    }
}
