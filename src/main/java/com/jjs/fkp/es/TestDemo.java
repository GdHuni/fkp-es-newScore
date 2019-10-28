package com.jjs.fkp.es;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang3.StringUtils;

/**
 * @功能描述: 调用es Demo
 * @项目版本: 1.2.0
 * @项目名称:
 * @相对路径: com.jjs.fkp.es
 * @创建作者: <a href="mailto:zhouh@leyoujia.com">周虎</a>
 * @创建日期: 2019/10/19 14:38
 */
public class TestDemo {
    public static void main(String[] args) {
        //查询es的参数
        String params = "{\"query\": {\"function_score\": {\"query\": {\"bool\": {\"must\": [{\"bool\": {\"must\": [{\"bool\": {\"should\": []}}]}},{\"bool\": {\"must\": [{\"term\": {\"rs_type\": \"2\"}},{\"range\": {\"total_price\": {\"gt\": \"96.00\"}}},{\"term\": {\"city_id\": \"000002\"}}]}},{\"bool\": {\"must_not\": []}}]}},\"functions\": [{\"script_score\": {\"script\": {\"source\": \"houseMatch_df\",\"lang\": \"houseMatch\",\"params\": {\"yx_place\": [{\"areaCode\":\"0\",\"cityCode\":\"000002\",\"placeCode\":\"0\"}],\"house_type\": [\"两室\"]}}}}],\"boost_mode\": \"replace\"}},\"from\": \"0\",\"size\": \"5\",\"sort\": [],\"aggs\": {}}";
        JSONObject jsonObject = post(params);
    }

    public static JSONObject post(String param) {
        //es的链接 znky_fh_property：索引 pretty:数据以json返回
        String url = "http://172.16.3.228:9200/znky_fh_property/_search?pretty";
        JSONObject result = null;
        try {
            HttpClient httpClient = new HttpClient();
            PostMethod method = new PostMethod(url);
            method.setRequestEntity(new StringRequestEntity(param,"application/json","UTF-8"));
            httpClient.executeMethod(method);
            httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
            httpClient.getHttpConnectionManager().getParams().setSoTimeout(30000);
            String body = method.getResponseBodyAsString();
            if(StringUtils.isNotEmpty(body)){
                result = JSONObject.parseObject(body);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
