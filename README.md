* 环境依赖：
    * elasticsearch >= 6.x
    * pom中对不同elasticsearch版本的依赖，最终插件只能应用在对应版本的elasticsearch上
    * 写好plugun.xml
            <?xml version="1.0"?>
            <assembly>
                <id>plugin</id>
            
                <formats>
                    <format>zip</format>
                </formats>
            
                <includeBaseDirectory>false</includeBaseDirectory>
            
                <fileSets>
                    <fileSet>
                        <directory>${project.basedir}/src/main/resources</directory>
                        <outputDirectory>/houseHatch</outputDirectory>
                    </fileSet>
                </fileSets>
            
                <dependencySets>
                    <dependencySet>
                        <outputDirectory>/houseHatch</outputDirectory>
                        <useProjectArtifact>true</useProjectArtifact>
                        <useTransitiveFiltering>true</useTransitiveFiltering>
                        <excludes>
                            <exclude>org.elasticsearch:elasticsearch</exclude>
                            <exclude>org.apache.logging.log4j:log4j-api</exclude>
                        </excludes>
                    </dependencySet>
                </dependencySets>
            
            
            </assembly>

    * pom配置好打包文件
      <plugin>
                    <artifactId>maven-assembly-plugin</artifactId>
                    <version>2.3</version>
                    <configuration>
                        <appendAssemblyId>false</appendAssemblyId>
                        <outputDirectory>${project.build.directory}/releases/</outputDirectory>
                        <descriptors>
                            <descriptor>${basedir}/src/assembly/plugin.xml</descriptor>
                        </descriptors>
                    </configuration>
                    <executions>
                        <execution>
                            <phase>package</phase>
                            <goals>
                                <goal>single</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
                
* 编译安装：
    * 编译：mvn clean package -Dmaven.test.skip=true
    * 安装方式: 
        * 直接将target/release/zip文件解压到${elasticsearch-home}/plugins/下面（其中的jar只需要保留主jar即可）
       
* 测试：
    *需要在查询参数上加上   "boost_mode": "replace"  这样才会将新的score替换原来的old_score
    造数据：
    http://172.16.3.228:9200/znky_fh_property/logs/3/PUT
     {
    "fh_id": "3",
    "lp_id": "23455",
    "lp_name": "安吉尔大厦",
    "city_id": "000002",
    "city_name": "深圳",
    "yx_fitment": "电视",
    "fh_star": "5",
    "is_hf": "1"
    }
    
  查询数据：
  {
    "query": {
      "function_score": {
        "query": {
          "match": {
            "city_name": "深圳"
          }
        },
        "functions": [
          {
            "script_score": {
              "script": {
                "source": "houseMatch_df",
                "lang": "houseMatch",
                "params": {
                  "yx_fitment": "电视"
                }
              }
            }
          }
        ],
        "boost_mode": "replace"
      }
    }
  }

function_score 简介可参考：https://blog.csdn.net/weixin_40341116/article/details/80913045