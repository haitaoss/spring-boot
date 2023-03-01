package cn.haitaoss;

import lombok.Data;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentUpdateListener;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-13 16:14
 *
 */
// @SpringBootApplication(exclude = WebMvcAutoConfiguration.class)
@SpringBootApplication
@Data
// @EnableConfigurationProperties(value = {A.class})
@ConfigurationProperties("haitao.cn")
public class Main {
    private String name;

    public static void main(String[] args) throws Exception {
        // ConfigurableApplicationContext context = SpringApplication.run(Main.class, new String[]{"haitao"});
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);


        /*SpringApplication springApplication = new SpringApplicationBuilder()
                .sources(Main.class)
                .build(args);
        springApplication.setApplicationContextFactory(type -> new AnnotationConfigApplicationContext());
        ConfigurableApplicationContext context = springApplication.run(args);*/

        Object bean = context.getBean(AutoConfigurationPackages.class.getName());
        System.out.println(bean);
        System.out.println("==============");
    }

    private static void test_function() {
        System.out.println("这种语法这奇怪");
        BiFunction<A, String, Integer> biFunction = A::method;
        biFunction.apply(new A() {
            @Override
            public Integer method(String o) {
                System.out.println(o);
                return 9527;
            }
        }, "hello"); // 其实就是回调第一个参数的函数方法，需要的参数就是第二个参数
    }
}

interface A {
    Integer method(String o);
}
