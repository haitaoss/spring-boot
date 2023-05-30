package cn.haitaoss.https.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-05-30 19:54
 */
@Component
public class WebMvcConfig implements WebMvcConfigurer {
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// 设置允许的源
		registry
				.addMapping("/json")
//				.allowedOrigins("*")
//				.allowedOrigins(/*"http://localhost:8080",*/"https://localhost:8080")
				.allowedOrigins("https://192.168.3.121:8080")
				.allowCredentials(true)
				.allowedMethods("POST", "OPTIONS")
				.maxAge(3600);

		registry
				.addMapping("/json2")
				.allowedOrigins("*")
				.allowedMethods("POST", "OPTIONS")
				.maxAge(3600);
	}
}
