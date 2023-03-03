package cn.haitaoss;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-02 14:35
 *
 */
@SpringBootTest(
        // 配置类
        classes = Main.class,
        // 指定环境。因为默认是不会使用 WebApplicationContext
        // webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        // 配置属性
        properties = "spring.profiles.active=haitao"
)
// @WebMvcTest
public class Main {
    @Autowired
    ApplicationContext applicationContext;

    @Test
    public void test() {
        System.out.println(applicationContext.getClass());
        System.out.println(Arrays.toString(applicationContext.getBeanDefinitionNames()));
    }
}
