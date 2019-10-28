package com.jjs.fkp.es;

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScoreScript.LeafFactory;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.search.lookup.SearchLookup;


import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @功能描述: 房客配es6.8.3自定义评分排序插件
 * @项目版本: 1.0
 * @项目名称:
 * @相对路径: com.jjs.fkp.es
 * @创建作者: <a href="mailto:zhouh@leyoujia.com">周虎</a>
 * @创建日期: 2019/10/18 10:08
 */
public class HouseMatchScriptPlugin extends Plugin implements ScriptPlugin {

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new HouseMatchScriptEngine();
    }

    private static class HouseMatchScriptEngine implements ScriptEngine {
        private final static org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(HouseMatchScriptEngine.class);

        @Override
        public String getType() {
            return "houseMatch";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
            if (context.equals(ScoreScript.CONTEXT) == false) {
                throw new IllegalArgumentException(getType()
                        + " scripts cannot be used for context ["
                        + context.name + "]");
            }
            // we use the script "source" as the script identifier
            if ("houseMatch_df".equals(scriptSource)) {
                ScoreScript.Factory factory = PureDfLeafFactory::new;
                return context.factoryClazz.cast(factory);
            }
            throw new IllegalArgumentException("Unknown script name "
                    + scriptSource);
        }

        @Override
        public void close() {
            // optionally close resources
        }

        private static class PureDfLeafFactory implements LeafFactory {
            private final Map<String, Object> params;
            private final SearchLookup lookup;

            private PureDfLeafFactory(Map<String, Object> params, SearchLookup lookup) {
                this.params = params;
                this.lookup = lookup;
            }

            @Override
            public boolean needs_score() {
                return false;  // Return true if the script needs the score
            }

            @Override
            public ScoreScript newInstance(LeafReaderContext context) {

                return new ScoreScript(params, lookup, context) {

                    @Override
                    public double execute() {

                        double num = 0.0;//客户勾选非必要条件的个数
                        double w = 2.0;//匹配度fu的权重分
                        double rank_score = 0.0;//业主诚意度
                        double match_score_top = 0.0;//匹配度Fx的得分
                        double match_score_buttom = 0.0;//匹配度中的Fu得分
                        double match_score = 0.0;//匹配得分

                        //==================================业主诚意度=========================
                        try {
                            /*是否五星好房*/
                            Integer star = (Integer) lookup.source().get("star");
                            if (star != null && star==5) {
                                rank_score += 0.5 * 1;
                            }
                           /* LOGGER.info("fh_star_info: "+fh_star);
                            LOGGER.info(" fhstart_rank_score:"+rank_score);*/
                        } catch (Exception e) {
                            LOGGER.error("解析五星好房失败"+e.toString());
                        }

                        try {
                            /*是否必卖好房*/
                            Integer is_must_sell = (Integer) lookup.source().get("is_must_sell");
                            if (is_must_sell != null && is_must_sell==1) {
                                rank_score += 0.5 * 1;
                            }
                      /*      LOGGER.info("is_must_sell_info: "+is_must_sell);
                            LOGGER.info("ishf_ rank_score:"+rank_score);*/
                        } catch (Exception e) {
                            LOGGER.error("解析必卖好房失败"+e.toString());
                        }

                        //============================房源匹配度===================================

                        /* 意向小区*/
                        List p_lp_ids = null;
                        try {
                            Integer lp_id = (Integer) lookup.source().get("lp_id");
                            p_lp_ids = (List) params.get("lp_id");

                     /*       LOGGER.info("lp_id: "+lp_id);
                            LOGGER.info("p_lp_ids: "+p_lp_ids);*/

                            if (p_lp_ids != null) {
                                num += 1;
                                if (p_lp_ids.size() > 0 & (lp_id) != null) {
                                    for (Object p_lp_id : p_lp_ids) {
                                        if (p_lp_id.toString().equals(lp_id.toString())) {
                                            match_score_top += 1;
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("解析意向小区失败: " + e.toString());
                        }

                        /* 意向面积*/
                        try {
                            Double build_area = (Double) lookup.source().get("build_area");
                            String p_build_area = (String) params.get("build_area");


                            if (p_build_area != null) {
                                num += 1;
                                if (build_area != null && p_build_area.length() > 0) {
                                    String[] split = p_build_area.split("-");
                                    if (split.length > 1) {
                                        double min = Double.parseDouble(split[0]);
                                        double max = Double.parseDouble(split[1]);
                                        if (build_area >= min && build_area <= max) {
                                            match_score_top += 1;
                                        }
                                    } else {
                                        if (p_build_area.endsWith("以下")) {
                                            String p = p_build_area.replace("以下", "");
                                            double lt = Double.parseDouble(p);
                                            if (build_area <= lt) {
                                                match_score_top += 1;
                                            }
                                        } else if (p_build_area.endsWith("以上")) {
                                            String p = p_build_area.replace("以上", "");
                                            double gt = Double.parseDouble(p);
                                            if (build_area >= gt) {
                                                match_score_top += 1;
                                            }
                                        }
                                    }
                                }

                            }
                        } catch (Exception e) {
                            LOGGER.error("解析意向面积失败：" + e.toString());
                        }

                        /* 意向装修*/
                        try {
                            String fitment_type = (String) lookup.source().get("fitment_type");
                            List p_fitment_types = (List) params.get("fitment_type");

                          /*  LOGGER.info("fitment_type: "+fitment_type);
                            LOGGER.info("p_fitment_type: "+p_fitment_types);*/

                            if (p_fitment_types != null) {
                                num += 1;
                                if (p_fitment_types.size() > 0 & (fitment_type) != null) {
                                    for (Object p_lp_id : p_fitment_types) {
                                        if (p_lp_id.toString().equals(fitment_type)) {
                                            match_score_top += 1;
                                            break;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析意向装修失败：" + e.toString());
                        }

                        /* 家私家电*/
                        try {
                            String furniture_name = (String) lookup.source().get("furniture_name");
                            String p_furniture_name = (String) params.get("furniture_name");

                          /*  LOGGER.info("furniture_name: "+furniture_name);
                            LOGGER.info("p_furniture_name: "+p_furniture_name);*/

                            if (p_furniture_name != null) {
                                num += 1;
                                if (furniture_name != null && p_furniture_name.equals(furniture_name)) {
                                    match_score_top += 1;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("解析家私家电失败：" + e.toString());
                        }

                        /* 意向户型*/
                        try {
                            String house_type = (String) lookup.source().get("house_type");
                            List p_house_types = (List) params.get("house_type");

                         /*   LOGGER.info("house_type: "+house_type);
                            LOGGER.info("p_house_types: "+p_house_types);*/

                            if (p_house_types != null) {
                                num += 1;
                                if (p_house_types.size() > 0 & (house_type) != null) {
                                    for (Object p_lp_id : p_house_types) {
                                        if (p_lp_id.toString().equals(house_type)) {
                                            match_score_top += 1;
                                            break;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析意向户型失败：" + e.toString());
                        }

                        /* 意向楼层*/
                        try {
                            Integer layer_num = (Integer) lookup.source().get("layer_num");
                            String p_layer_num = (String) params.get("layer_num");

                         /*   LOGGER.info("layer_num: "+layer_num);
                            LOGGER.info("p_layer_num: "+p_layer_num);*/
                            if (p_layer_num != null) {
                                num += 1;
                                if (layer_num != null && p_layer_num.length() > 0) {
                                    String[] split = p_layer_num.split("-");
                                    if (split.length > 1) {
                                        double min = Double.parseDouble(split[0]);
                                        double max = Double.parseDouble(split[1]);
                                        if (layer_num >= min && layer_num <= max) {
                                            match_score_top += 1;
                                        }
                                    } else {
                                        if (p_layer_num.endsWith("以下")) {
                                            String p = p_layer_num.replace("以下", "");
                                            double lt = Double.parseDouble(p);
                                            if (layer_num <= lt) {
                                                match_score_top += 1;
                                            }
                                        } else if (p_layer_num.endsWith("以上")) {
                                            String p = p_layer_num.replace("以上", "");
                                            double gt = Double.parseDouble(p);
                                            if (layer_num >= gt) {
                                                match_score_top += 1;
                                            }
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析意向楼层失败");
                        }

                        /* 学位类型*/
                        try {
                            String school_type = (String) lookup.source().get("school_type");
                            List p_school_types = (List) params.get("school_type");

                       /*     LOGGER.info("school_type: "+school_type);
                            LOGGER.info("p_school_types: "+p_school_types);*/

                            if (p_school_types != null) {
                                num += 1;
                                if (p_school_types.size() > 0 & school_type != null) {
                                    for (Object p_lp_id : p_school_types) {
                                        if (p_lp_id.toString().equals(school_type)) {
                                            match_score_top += 1;
                                            break;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析学位类型失败：" + e.toString());
                        }

                        /* 学校等级*/
                        try {
                            Integer level = (Integer) lookup.source().get("level");
                            //  LOGGER.info("level: "+level);

                            String p_level = (String) params.get("level");
                            //LOGGER.info("p_level: "+p_level);

                            if (p_level != null) {
                                num += 1;
                                if (level != null && p_level.equals(level.toString())) {
                                    match_score_top += 1;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("解析学校等级失败：" + e.toString());
                        }

                        /* 意向学校 ID*/
                        List p_school_ids = null;
                        try {
                            Integer school_id = (Integer) lookup.source().get("school_id");
                            p_school_ids = (List) params.get("school_id");

                          /*  LOGGER.info("school_id: "+school_id);
                            LOGGER.info("p_school_ids: "+p_school_ids);*/

                            if (p_school_ids != null) {
                                num += 1;
                                if (p_school_ids.size() > 0 & school_id != null) {
                                    for (Object p_lp_id : p_school_ids) {
                                        if (p_lp_id.toString().equals(school_id.toString())) {
                                            match_score_top += 1;
                                            break;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析意向学校失败：" + e.toString());
                        }

                        /* 租售方式*/
                        try {
                            Integer rs_type = (Integer) lookup.source().get("rs_type");
                            String p_rs_type = (String) params.get("rs_type");

                           /* LOGGER.info("rs_type: "+rs_type);
                            LOGGER.info("p_rs_type: "+p_rs_type);*/

                            if (p_rs_type != null) {
                                num += 1;
                                if (rs_type != null && p_rs_type.equals(rs_type.toString())) {
                                    match_score_top += 1;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("解析租售方式失败：" + e.toString());
                        }

                        /* 意向地段*/

                        try {
                            List<Map<String, String>> p_yx_places = (List) params.get("yx_place");
                            String city_code = (String) lookup.source().get("city_id");
                            String area_code = (String) lookup.source().get("area_id");
                            String place_code = (String) lookup.source().get("place_id");
                            if (p_yx_places != null && p_yx_places.size() > 0) {
                                for (Map<String, String> p_yx_placeMap : p_yx_places) {

                                    if (p_yx_placeMap.get("cityCode") != null && p_yx_placeMap.get("cityCode").equals(city_code)) {
                                        if (p_yx_placeMap.get("areaCode") != null) {
                                            //0表示不限
                                            if (p_yx_placeMap.get("areaCode").equals("0")) {
                                                match_score_top += 1;
                                                break;
                                            } else if (p_yx_placeMap.get("areaCode").equals(area_code)) {
                                                if (p_yx_placeMap.get("placeCode") != null && place_code != null) {
                                                    if (p_yx_placeMap.get("placeCode").equals(0)) {
                                                        match_score_top += 1;
                                                        break;
                                                    } else if (p_yx_placeMap.get("placeCode").equals(place_code)) {
                                                        match_score_top += 1;
                                                        break;
                                                    }
                                                }

                                            }

                                        }
                                    }

                                }
                            }

                        } catch (Exception e) {
                            LOGGER.error("解析意向地段失败：" + e.toString());
                        }


                     /*   LOGGER.info("end_rank_score: "+rank_score);
                        LOGGER.info("num: "+num);
                        LOGGER.info("match_score_top: "+  match_score_top);*/

                        if (p_lp_ids == null && p_school_ids == null) {
                            match_score_buttom = num - 1.0 + 1.0;
                        } else {
                            match_score_buttom = num - 1.0 + w;
                        }

                        /*  LOGGER.info("match_score_buttom: "+match_score_buttom);*/

                        if (match_score_buttom == 0) {
                            match_score = 0.0;
                        } else {
                            match_score = match_score_top / match_score_buttom;
                        }

                        double final_score = rank_score + match_score;


                        return final_score;
                    }

                };
            }
        }
    }
}