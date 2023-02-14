package cn.haitaoss;

import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-13 16:14
 *
 */
@SpringBootApplication
@Data
public class Main {
    private String name;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Main.class);

        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

        System.out.println(context.getBean(Main.class).getName());
    }
}

