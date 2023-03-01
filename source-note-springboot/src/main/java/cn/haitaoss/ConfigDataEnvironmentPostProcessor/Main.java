package cn.haitaoss.ConfigDataEnvironmentPostProcessor;

import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-22 16:43
 *
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // System.setProperty("spring.profiles.active", "dev");
        System.setProperty("spring.profiles.include", "ext1,ext2");

        // 这种方式增加的属性文件是一定生效的
        System.setProperty("spring.config.import", "classpath:/config/1.properties");
        System.setProperty("spring.config.additional-location", "classpath:/ext/application-haitao.yml");
        System.setProperty("spring.config.location", String.join(",",
                        Arrays.asList(
                                "optional:classpath:/;optional:classpath:/config/",
                                "optional:file:./;optional:file:./config/;optional:file:./config/*/"
                                /*,"optional:classpath:/haitao.properties"*/) // 注：使用 "optional:" 表示允许文件不存在
                )
        );
        /**
         * 不能统配多个文件，只能统配到某个目录
         * System.setProperty("spring.config.additional-location", "classpath:/ext/`*`.yml"); // 不支持
         * System.setProperty("spring.config.additional-location", "classpath:/`*`/ext/");  // 支持，会找这个目录下名叫 application 的文件
         * */
        ConfigurableApplicationContext context = SpringApplication.run(cn.haitaoss.Main.class, new String[]{"haitao"});

        ConfigurableEnvironment environment = context.getEnvironment();
        System.out.println(environment.getProperty("dev"));
        System.out.println(environment.getProperty("bootstrap"));

    }
}
