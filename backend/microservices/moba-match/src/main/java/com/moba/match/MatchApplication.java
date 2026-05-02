package com.moba.match;

import com.moba.common.config.NacosConfigLoader;
import com.moba.match.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Map;
import java.util.Properties;

@Slf4j
public class MatchApplication {

    public static void main(String[] args) {
        try {
            Map<String, Object> mergedConfig = NacosConfigLoader.load("application.yml");

            Properties configProperties = NacosConfigLoader.flattenToProperties(mergedConfig);

            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.getEnvironment().getPropertySources()
                    .addFirst(new PropertiesPropertySource("mergedConfig", configProperties));

            context.register(AppConfig.class);
            context.refresh();
            context.registerShutdownHook();

            log.info("MOBA匹配服务启动成功 (纯Netty + Spring框架)");
        } catch (Exception e) {
            log.error("MOBA匹配服务启动失败", e);
            System.exit(1);
        }
    }
}
