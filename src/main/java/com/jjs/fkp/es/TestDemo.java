package com.jjs.fkp.es;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @功能描述:
 * @项目版本: 1.2.0
 * @项目名称:
 * @相对路径: com.jjs.fkp.es
 * @创建作者: <a href="mailto:zhouh@leyoujia.com">周虎</a>
 * @创建日期: 2019/10/19 14:38
 */
public class TestDemo {
    public static void main(String[] args) {
        Map map = new HashMap<>();
        map.put("1","1");
       List s =  (List) map.get("1");
        System.out.println(s);
    }
}
