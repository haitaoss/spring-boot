package cn.haitaoss.actuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.web.servlet.WebMvcMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-08 11:46
 *
 */
@SpringBootApplication(exclude = WebMvcMetricsAutoConfiguration.class)
@Controller
public class Main {
    @RequestMapping({"/index", ""})
    public String index() {
        return "redirect:/actuator";
    }

    public static void main(String[] args) {
        System.setProperty("spring.config.location", "classpath:/config/application-endpoint.properties");
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
    }
}

