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

import java.io.IOException;
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

    private static class  HouseMatchScriptEngine implements ScriptEngine {
        private final static org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(HouseMatchScriptEngine.class);
        @Override
        public String getType() {
            return "houseMatch";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource,
                             ScriptContext<T> context, Map<String, String> params) {
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
            public ScoreScript newInstance(LeafReaderContext context) throws IOException {

                return new ScoreScript(params, lookup, context) {

                    double num = 0.0;//客户勾选非必要条件的个数
                    double w = 2.0;//匹配度fu的权重分
                    double rank_score = 0.0;//业主诚意度
                    double match_score_top = 0.0;//匹配度Fx的得分
                    double match_score_buttom = 0.0;//匹配度中的Fu得分
                    double match_score = 0.0;//匹配得分

                    @Override
                    public double execute() {
                        /*lookup.source().get("is_hf")) 这个不能用Integer来接收*/

                        //==================================业主诚意度=========================
                        try {
                            /*是否五星好房*/
                            String fh_star = (String) lookup.source().get("fh_star");
                            if(fh_star  != null && fh_star.equals("5")){
                                rank_score += 0.5*1;
                            }
                            LOGGER.info("fh_star_info: "+fh_star);
                            LOGGER.info(" fhstart_rank_score:"+rank_score);
                        }catch (Exception e){
                            LOGGER.error("解析五星好房失败");
                        }

                        try {
                            /*是否必卖好房*/
                            String is_hf = (String) lookup.source().get("is_hf");
                            if(is_hf  != null && is_hf.equals("1")){
                                rank_score += 0.5*1;
                            }
                            LOGGER.info("is_hf_info: "+is_hf);
                            LOGGER.info("ishf_ rank_score:"+rank_score);
                        }catch (Exception e){
                            LOGGER.error("解析必卖好房失败");
                        }

                        //============================房源匹配度===================================

                        /* 意向小区*/
                        List p_lp_ids=null;
                        try {
                            String lp_id = (String) lookup.source().get("lp_id");
                            p_lp_ids = (List)params.get("lp_id");

                            LOGGER.info("lp_id: "+lp_id);
                            LOGGER.info("p_lp_ids: "+p_lp_ids);

                            if(p_lp_ids!=null){
                                num+=1;
                                if(p_lp_ids.size()>0){
                                    for (Object p_lp_id : p_lp_ids) {
                                        if(p_lp_id.toString().equals(lp_id)){
                                            match_score_top+=1;
                                            break;
                                        }
                                    }
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析意向小区失败: "+e.toString());
                        }

                        /* 意向面积*/
                        try {
                            String house_area = (String) lookup.source().get("house_area");
                            String p_house_area = (String)params.get("house_area");

                            LOGGER.info("house_area: "+house_area);
                            LOGGER.info("p_house_area: "+p_house_area);

                            if(p_house_area != null ){
                                num+=1;
                                if(house_area != null && p_house_area.length()>0){
                                    String[] split = p_house_area.split("-");
                                    if (split.length > 1){
                                        Integer min = Integer.parseInt(split[0]);
                                        Integer max = Integer.parseInt(split[1]);
                                        double AREA =  Integer.parseInt(house_area);
                                        if (AREA >= min && AREA <= max) {
                                            match_score_top+=1;
                                        }
                                    }else {
                                        double AREA =  Integer.parseInt(house_area);
                                        if (p_house_area.endsWith("以下")){
                                            String p = p_house_area.replace("以下", "");
                                            int lt = Integer.parseInt(p);
                                            if (AREA <= lt) {
                                                match_score_top+=1;
                                            }
                                        } else if (p_house_area.endsWith("以上")){
                                            String p = p_house_area.replace("以上", "");
                                            int gt = Integer.parseInt(p);
                                            if (AREA >= gt) {
                                                match_score_top+=1;
                                            }
                                        }
                                    }
                                }

                            }
                        }catch (Exception e){
                            LOGGER.error("解析意向面积失败："+e.toString());
                        }

                        /* 意向装修*/
                        try {
                            String house_decoration = (String) lookup.source().get("house_decoration");
                            String p_house_decoration = (String)params.get("house_decoration");

                            LOGGER.info("house_decoration: "+house_decoration);
                            LOGGER.info("p_house_decoration: "+p_house_decoration);

                            if(p_house_decoration != null){
                                num+=1;
                                if(p_house_decoration.equals(house_decoration)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析意向装修失败："+e.toString());
                        }

                        /* 家私家电*/
                        try {
                            String house_furniture = (String) lookup.source().get("house_furniture");
                            String p_house_furniture = (String)params.get("house_furniture");

                            LOGGER.info("house_furniture: "+house_furniture);
                            LOGGER.info("p_house_furniture: "+p_house_furniture);

                            if(p_house_furniture!=null){
                                num+=1;
                                if(p_house_furniture.equals(house_furniture)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析家私家电失败："+e.toString());
                        }

                        /* 意向户型*/
                        try {
                            String house_type = (String) lookup.source().get("house_type");
                            String p_house_type = (String)params.get("house_type");

                            LOGGER.info("house_type: "+house_type);
                            LOGGER.info("p_house_type: "+p_house_type);

                            if(p_house_type!=null){
                                num+=1;
                                if( p_house_type.equals(house_type)){

                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析意向户型失败："+e.toString());
                        }

                        /* 意向楼层*/
                        try {
                            String house_floor = (String) lookup.source().get("house_floor");
                            String p_house_floor = (String)params.get("house_floor");

                            LOGGER.info("house_floor: "+house_floor);
                            LOGGER.info("p_house_floor: "+p_house_floor);

                            if(p_house_floor!=null){
                                num+=1;
                                if(p_house_floor.equals(house_floor)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析意向楼层失败");
                        }

                        /* 学位类型*/
                        try {
                            String school_type = (String) lookup.source().get("school_type");
                            String p_school_type = (String)params.get("school_type");

                            LOGGER.info("school_type: "+school_type);
                            LOGGER.info("p_school_type: "+p_school_type);

                            if(p_school_type!=null){
                                num+=1;
                                if(p_school_type.equals(school_type)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析学位类型失败："+e.toString());
                        }

                        /* 学校等级*/
                        try {
                            String purpose_school = (String) lookup.source().get("purpose_school");
                            String p_purpose_school= (String)params.get("purpose_school");

                            LOGGER.info("purpose_school: "+purpose_school);
                            LOGGER.info("p_purpose_school: "+p_purpose_school);

                            if(p_purpose_school!=null){
                                num+=1;
                                if(p_purpose_school.equals(purpose_school)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析学位类型失败："+e.toString());
                        }

                        /* 意向学校 ID*/
                        String p_purpose_school_id =null;
                        try {
                            String purpose_school_id = (String) lookup.source().get("purpose_school_id");
                            p_purpose_school_id = (String)params.get("purpose_school_id");

                            LOGGER.info("purpose_school_id: "+purpose_school_id);
                            LOGGER.info("p_purpose_school_id: "+p_purpose_school_id);

                            if(p_purpose_school_id!=null){
                                num+=1;
                                if(p_purpose_school_id.equals(purpose_school_id)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析学位类型失败："+e.toString());
                        }

                        /* 租售方式*/
                        try {
                            String rs_type = (String) lookup.source().get("rs_type");
                            String p_rs_type = (String)params.get("rs_type");

                            LOGGER.info("rs_type: "+rs_type);
                            LOGGER.info("p_rs_type: "+p_rs_type);

                            if(p_rs_type!=null){
                                num+=1;
                                if( p_rs_type.equals(rs_type)){
                                    match_score_top+=1;
                                }
                            }
                        }catch (Exception e){
                            LOGGER.error("解析租售方式失败："+e.toString());
                        }

                        LOGGER.info("end_rank_score: "+rank_score);
                        LOGGER.info("num: "+num);
                        LOGGER.info("match_score_top: "+  match_score_top);

                        if(p_lp_ids==null && p_purpose_school_id ==null){
                            match_score_buttom = num -1.0 +1.0;
                        }else{
                            match_score_buttom = num -1.0 +w;
                        }

                        LOGGER.info("match_score_buttom: "+match_score_buttom);

                        if(match_score_buttom==0){
                            match_score=0.0;
                        }else{
                            match_score = match_score_top/match_score_buttom;
                        }

                        double final_score = rank_score * (1+match_score);

                        LOGGER.info("match_score: "+  match_score);
                        LOGGER.info("final_score: "+  final_score+"\n");

                        return final_score;
                    }
                };
            }
        }
    }
}