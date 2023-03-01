package cn.haitaoss.EnableConfigurationProperties;

import cn.haitaoss.ConditionalOnSingleCandidate.ConditionalOnSingleCandidateTest;
import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-17 14:45
 *
 */
@EnableConfigurationProperties(A.class)
public class Main {
    @Bean
    @ConfigurationProperties(prefix = "obj") // 会遍历set方法，存在属性就注入
    public Object object() {
        return new Object();
    }

    public static void main(String[] args) {
        System.setProperty("name", "海涛");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.class);
        A bean = context.getBean(A.class);
        System.out.println("bean = " + bean);
    }
}

// @ConfigurationProperties(prefix = "haitao.cn")
@ConfigurationProperties
@ToString
class A {

    private String name;

    @Value("${x:default_name}")
    public void setName(String name) {
        System.out.println("---->" + name);
        this.name = name;
    }

    public A() {
    }

    // @ConstructorBinding
    A(String s) {
    }

    private A(String s, String s2) {
    }
}