package cn.haitaoss.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-06-09 08:17
 *
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class Main {
    /**
     * {@link org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext#createWebServer()
     * 		SpringBoot的嵌入式和非嵌入式的Web容器都会找到IOC容器中类型是 ServletRegistrationBean、FilterRegistrationBean、ServletListenerRegistrationBean、
    Servlet、Filter、EventListener 的bean
     * 		注册到 ServletContext 中。
     *
     * 		扩展：@ServletComponentScan 的作用是将标注了 @WebServlet、@WebFilter、@WebListener 的类映射成 ServletRegistrationBean、FilterRegistrationBean、ServletListenerRegistrationBean 类型的bean注册到容器中。
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(Main.class, args);
    }
}
