package cn.haitaoss;

import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-13 16:14
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
                .web(WebApplicationType.NONE)
                .build(args);
        //springApplication.setApplicationContextFactory(type -> new AnnotationConfigApplicationContext());
        ConfigurableApplicationContext context = springApplication.run(args);*/
    }

}