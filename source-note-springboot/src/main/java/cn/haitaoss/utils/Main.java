package cn.haitaoss.utils;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;

import java.util.Properties;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-14 15:52
 *
 */
public class Main {
    public static void main(String[] args) {
        System.setProperty("management.endpoints.web.basePath", "/haitao");
        Binder binder = Binder.get(new StandardEnvironment());

        // name 就是前缀
        Class<WebEndpointProperties> clazz = WebEndpointProperties.class;
        String name = clazz.getDeclaredAnnotation(ConfigurationProperties.class).prefix();
        WebEndpointProperties webEndpointProperties = binder
                .bind(name, clazz)
                .get();
        System.out.println(webEndpointProperties.getBasePath());
    }
}
