### 1.业务场景引入
在进行代码实现之前，我们先来看一个业务场景：

```
系统A是一个电商系统，目前是一台机器部署，系统中有一个用户下订单的接口，但是用户下订单之前一定要去检查一下库存，确保库存足够了才会给用户下单。
由于系统有一定的并发，所以会预先将商品的库存保存在redis中，用户下单的时候会更新redis的库存。
```

此时系统架构如下：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507110227855.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
但是这样一来会产生一个问题：

```
假如某个时刻，redis里面的某个商品库存为1，此时两个请求同时到来，其中一个请求执行到上图的第3步，更新数据库的库存为0，但是第4步还没有执行。

而另外一个请求执行到了第2步，发现库存还是1，就继续执行第3步。

这样的结果，是导致卖出了2个商品，然而其实库存只有1个。

很明显不对啊！这就是典型的库存超卖问题

此时，我们很容易想到解决方案：用锁把2、3、4步锁住，让他们执行完之后，另一个线程才能进来执行第2步。
```
![在这里插入图片描述](https://img-blog.csdnimg.cn/2020050711030347.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
按照上面的图，在执行第2步时，使用Java提供的`synchronized`或者`ReentrantLock`来锁住，然后在第4步执行完之后才释放锁。

这样一来，2、3、4 这3个步骤就被“锁”住了，多个线程之间只能串行化执行。

但是好景不长，整个系统的并发飙升，一台机器扛不住了。现在要增加一台机器，如下图：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507110335147.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
增加机器之后，系统变成上图所示，我的天！

假设此时两个用户的请求同时到来，但是落在了不同的机器上，那么这两个请求是可以同时执行了，还是会出现库存超卖的问题。

为什么呢？因为上图中的两个A系统，运行在两个不同的JVM里面，他们加的锁只对属于自己JVM里面的线程有效，对于其他JVM的线程是无效的。

因此，这里的问题是：Java提供的原生锁机制在多机部署场景下失效了

这是因为两台机器加的锁不是同一个锁(两个锁在不同的JVM里面)。

那么，我们只要保证两台机器加的锁是同一个锁，问题不就解决了吗？

此时，就该分布式锁隆重登场了，分布式锁的思路是：

```
在整个系统提供一个全局、唯一的获取锁的“东西”，然后每个系统在需要加锁时，都去问这个“东西”拿到一把锁，这样不同的系统拿到的就可以认为是同一把锁。
至于这个“东西”，可以是Redis、Zookeeper，也可以是数据库。
```
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507110432627.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
通过上面的分析，我们知道了库存超卖场景在分布式部署系统的情况下使用Java原生的锁机制无法保证线程安全，所以我们需要用到分布式锁的方案。

那么，如何实现分布式锁呢？

### 2.基础环境准备
#### 2.1.准备测试环境
##### 2.1.1.准备库存数据库

```sql
-- ----------------------------
-- Table structure for t_goods
-- ----------------------------
DROP TABLE IF EXISTS `t_goods`;
CREATE TABLE `t_goods` (
  `goods_id` int(11) NOT NULL AUTO_INCREMENT,
  `goods_name` varchar(255) DEFAULT NULL,
  `goods_price` decimal(10,2) DEFAULT NULL,
  `goods_stock` int(11) DEFAULT NULL,
  `goods_img` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of t_goods
-- ----------------------------
INSERT INTO `t_goods` VALUES ('1', 'iphone8', '6999.00', '10000', 'img/iphone.jpg');
INSERT INTO `t_goods` VALUES ('2', '小米9', '3000.00', '1100', 'img/rongyao.jpg');
INSERT INTO `t_goods` VALUES ('3', '华为p30', '4000.00', '100000', 'img/rongyao.jpg');
```

##### 2.1.2.创建SpringBoot工程
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507124120158.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507124208825.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)

##### 2.1.3.导入依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bruceliu.springboot.redis.lock</groupId>
    <artifactId>springboot-redis-lock</artifactId>
    <version>1.0-SNAPSHOT</version>

    <!--导入SpringBoot的父工程  把系统中的版本号做了一些定义！ -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.1.3.RELEASE</version>
    </parent>

    <dependencies>
        <!--导入SpringBoot的Web场景启动器   Web相关的包导入！-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!--导入MyBatis的场景启动器-->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.0.0</version>
        </dependency>

        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>1.1.10</version>
        </dependency>

        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>5.1.28</version>
        </dependency>

        <!--SpringBoot和Junit整合-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>

        <!--导入Lombok依赖-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>


    </dependencies>

    <build>
        <!--编译的时候同时也把包下面的xml同时编译进去-->
        <resources>
            <resource>
                <directory>src/main/java</directory>
                <includes>
                    <include>**/*.xml</include>
                </includes>
            </resource>
        </resources>
    </build>

</project>
```
##### 2.1.4.application.properties

```properties
# SpringBoot有默认的配置，我们可以覆盖默认的配置
server.port=8888

# 配置数据的连接信息
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/brucedb?useUnicode=true&characterEncoding=utf-8
spring.datasource.username=root
spring.datasource.password=123456
spring.datasource.type=com.alibaba.druid.pool.DruidDataSource
```
##### 2.1.5.SpringBoot启动类

```java
package com.bruceliu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 12:48
 * @Description: TODO
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class,args);
    }
}
```
#### 2.2.SpringBoot整合Spring Data Redis
##### 2.2.1.导入依赖
```xml
<!--Spring Data Redis 的启动器 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>2.9.0</version>
</dependency>
```
##### 2.2.2.添加Redis相关配置

```properties
spring.redis.jedis.pool.max-idle=10
spring.redis.jedis.pool.min-idle=5
spring.redis.jedis.pool-total=20
spring.redis.hostName=122.51.50.249
spring.redis.port=6379
```
##### 2.2.3.添加Redis的配置类

```java
package com.bruceliu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.config
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 12:55
 * @Description: TODO
 */
@Configuration
public class RedisConfig {

    /**
     * 1.创建JedisPoolConfig对象。在该对象中完成一些链接池配置
     * @ConfigurationProperties:会将前缀相同的内容创建一个实体。
     */
    @Bean
    @ConfigurationProperties(prefix="spring.redis.jedis.pool")
    public JedisPoolConfig jedisPoolConfig(){
        JedisPoolConfig config = new JedisPoolConfig();
		/*//最大空闲数
		config.setMaxIdle(10);
		//最小空闲数
		config.setMinIdle(5);
		//最大链接数
		config.setMaxTotal(20);*/
        System.out.println("默认值："+config.getMaxIdle());
        System.out.println("默认值："+config.getMinIdle());
        System.out.println("默认值："+config.getMaxTotal());
        return config;
    }

    /**
     * 2.创建JedisConnectionFactory：配置redis链接信息
     */
    @Bean
    @ConfigurationProperties(prefix="spring.redis")
    public JedisConnectionFactory jedisConnectionFactory(JedisPoolConfig config){
        System.out.println("配置完毕："+config.getMaxIdle());
        System.out.println("配置完毕："+config.getMinIdle());
        System.out.println("配置完毕："+config.getMaxTotal());

        JedisConnectionFactory factory = new JedisConnectionFactory();
        //关联链接池的配置对象
        factory.setPoolConfig(config);
        //配置链接Redis的信息
        //主机地址
		/*factory.setHostName("192.168.70.128");
		//端口
		factory.setPort(6379);*/
        return factory;
    }

    /**
     * 3.创建RedisTemplate:用于执行Redis操作的方法
     */
    @Bean
    public RedisTemplate<String,Object> redisTemplate(JedisConnectionFactory factory){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        //关联
        template.setConnectionFactory(factory);

        //为key设置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        //为value设置序列化器
        template.setValueSerializer(new StringRedisSerializer());

        return template;
    }
}
```
##### 2.2.4.测试Redis

```java
package com.bruceliu.test;

import com.bruceliu.App;
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

}

```
#### 2.3.准备数据库操作业务方法
##### 2.3.1.pojo层

```java
package com.bruceliu.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.pojo
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 13:41
 * @Description: TODO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Goods {

    private Long goods_id;
    private String goods_name;
    private Double  goods_price;
    private Long goods_stock;
    private String goods_img;
}
```
##### 2.3.2.mapper层

```java
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

```
##### 2.3.3.测试MyBatis
```java
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

```
#### 2.4.SpringBoot监听Web启动事件

```java
package com.bruceliu.listener;

import com.bruceliu.pojo.Goods;
import com.bruceliu.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

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
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("Web项目启动");
        List<Goods> goodsList = goodsService.findGoods();
        for (Goods goods : goodsList) {
            System.out.println(goods);
        }
    }
}
```
#### 2.5.加载商品数据到Redis中

```java
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
```
### 3.Redis实现分布式锁
#### 3.1 分布式锁的实现类

```java
package com.bruceliu.lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.UUID;

/**
 * @BelongsProject: springboot-redis-lock
 * @BelongsPackage: com.bruceliu.lock
 * @Author: bruceliu
 * @QQ:1241488705
 * @CreateTime: 2020-05-07 14:50
 * @Description: TODO
 */
public class DistributedLock {

    private final JedisPool jedisPool;

    public DistributedLock(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * 加锁
     * @param lockName       锁的key
     * @param acquireTimeout 获取超时时间
     * @param timeout        锁的超时时间
     * @return 锁标识
     */
    public String lockWithTimeout(String lockName, long acquireTimeout, long timeout) {
        Jedis conn = null;
        String retIdentifier = null;
        try {
            // 获取连接
            conn = jedisPool.getResource();
            // 随机生成一个value
            String identifier = UUID.randomUUID().toString();
            // 锁名，即key值
            String lockKey = "lock:" + lockName;

            // 超时时间，上锁后超过此时间则自动释放锁
            int lockExpire = (int) (timeout / 1000);

            // 获取锁的超时时间，超过这个时间则放弃获取锁
            long end = System.currentTimeMillis() + acquireTimeout;
            while (System.currentTimeMillis() < end) {
                if (conn.setnx(lockKey, identifier) == 1) {
                    conn.expire(lockKey, lockExpire);
                    // 返回value值，用于释放锁时间确认
                    retIdentifier = identifier;
                    return retIdentifier;
                }
                // 返回-1代表key没有设置超时时间，为key设置一个超时时间
                if (conn.ttl(lockKey) == -1) {
                    conn.expire(lockKey, lockExpire);
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (JedisException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
        return retIdentifier;
    }


    /**
     * 释放锁
     * @param lockName   锁的key
     * @param identifier 释放锁的标识
     * @return
     */
    public boolean releaseLock(String lockName, String identifier) {
        Jedis conn = null;
        String lockKey = "lock:" + lockName;
        boolean retFlag = false;
        try {
            conn = jedisPool.getResource();
            while (true) {
                // 监视lock，准备开始事务
                conn.watch(lockKey);
                // 通过前面返回的value值判断是不是该锁，若是该锁，则删除，释放锁
                if (identifier.equals(conn.get(lockKey))) {
                    Transaction transaction = conn.multi();
                    transaction.del(lockKey);
                    List<Object> results = transaction.exec();
                    if (results == null) {
                        continue;
                    }
                    retFlag = true;
                }
                conn.unwatch();
                break;
            }
        } catch (JedisException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
        return retFlag;
    }
}


```

#### 3.2 分布式锁的业务代码
**service业务逻辑层**

```java
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
```

**service业务逻辑层实现层**

```java
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
```

```java
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

```
**controller层**

```java
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
```
### 4.分布式锁测试
把SpringBoot工程启动两台服务器，端口分别为8888、9999.然后使用jmeter进行并发测试，查看控制台输出：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507165953524.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507170013662.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507170102645.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
### 5.关于Redis的部署
除了要考虑客户端要怎么实现分布式锁之外，还需要考虑redis的部署问题。

**redis有3种部署方式：**

#### 5.1.单机模式
standaloan 是redis单机模式，及所有服务连接一台redis服务，该模式不适用生产。如果发生宕机，内存爆炸，就可能导致所有连接改redis的服务发生缓存失效引起雪崩。

使用redis做分布式锁的缺点在于：如果采用单机部署模式，会存在单点问题，只要redis故障了。加锁就不行了。
#### 5.2.master-slave + sentinel选举模式
redis-Sentinel(哨兵模式)是Redis官方推荐的高可用性(HA)解决方案，当用Redis做Master-slave的高可用方案时，假如master宕机了，Redis本身(包括它的很多客户端)都没有实现自动进行主备切换，而Redis-sentinel本身也是一个独立运行的进程，它能监控多个master-slave集群，发现master宕机后能进行切换.
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507171558937.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
采用master-slave模式，加锁的时候只对一个节点加锁，即便通过sentinel做了高可用，但是如果master节点故障了，发生主从切换，此时就会有可能出现锁丢失的问题。
#### 5.3.redis cluster模式
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507171717802.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
redis集群模式，同样可以实现redis高可用部署,Redis Sentinel集群模式中，随着业务量和数据量增，到性能达到redis单节点瓶颈，垂直扩容受机器限制，水平扩容涉及对应用的影响以及数据迁移中数据丢失风险。针对这些痛点Redis3.0推出cluster分布式集群方案，当遇到单节点内存，并发，流量瓶颈是，采用cluster方案实现负载均衡，cluster方案主要解决分片问题，即把整个数据按照规则分成多个子集存储在多个不同几点上，每个节点负责自己整个数据的一部分。

Redis Cluster采用哈希分区规则中的虚拟槽分区。虚拟槽分区巧妙地使用了哈希空间，使用分散度良好的哈希函数把所有的数据映射到一个固定范围内的整数集合，整数定义为槽（slot）。Redis Cluster槽的范围是0 ～ 16383。槽是集群内数据管理和迁移的基本单位。采用大范围的槽的主要目的是为了方便数据的拆分和集群的扩展，每个节点负责一定数量的槽。Redis Cluster采用虚拟槽分区，所有的键根据哈希函数映射到0 ～ 16383，计算公式：slot = CRC16(key)&16383。每一个实节点负责维护一部分槽以及槽所映射的键值数据。下图展现一个五个节点构成的集群，每个节点平均大约负责3276个槽，以及通过计算公式映射到对应节点的对应槽的过程。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507171836472.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)

基于以上的考虑，其实redis的作者也考虑到这个问题，他提出了一个RedLock的算法，这个算法的意思大概是这样的：

```
假设redis的部署模式是redis cluster，总共有5个master节点，通过以下步骤获取一把锁：
获取当前时间戳，单位是毫秒
轮流尝试在每个master节点上创建锁，过期时间设置较短，一般就几十毫秒
尝试在大多数节点上建立一个锁，比如5个节点就要求是3个节点（n / 2 +1）
客户端计算建立好锁的时间，如果建立锁的时间小于超时时间，就算建立成功了
要是锁建立失败了，那么就依次删除这个锁
只要别人建立了一把分布式锁，你就得不断轮询去尝试获取锁
```
但是这样的这种算法还是颇具争议的，可能还会存在不少的问题，无法保证加锁的过程一定正确。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200507171925468.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0JydWNlTGl1X2NvZGU=,size_16,color_FFFFFF,t_70)
在实际开发中，没有必要使用原生的redis clinet来实现，可以借助Redis的封装框实现：Redisson！




