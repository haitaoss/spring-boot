package cn.haitaoss.https.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-05-30 20:04
 */
@Component
public class MyWebServerFactoryCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
	@Override
	public void customize(TomcatServletWebServerFactory factory) {
		Connector connector = new Connector();
		connector.setPort(9090);
		// 添加新的 Connector。也就是多监听一个端口
		factory.addAdditionalTomcatConnectors(connector);
	}
}
