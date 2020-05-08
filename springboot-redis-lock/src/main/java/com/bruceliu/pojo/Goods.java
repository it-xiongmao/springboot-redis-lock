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
