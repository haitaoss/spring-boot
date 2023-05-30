package cn.haitaoss.https;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-05-30 19:53
 */
@SpringBootApplication
public class Main {
	public static void main(String[] args) {
		/**
		 * 用 jdk11 运行
		 *
		 * macos 生成证书的指令
		 * keytool -genkeypair -alias selfsigned_localhost_sslserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore java.p12 -validity 3650
		 * */
		System.setProperty("server.port", "8080");
		System.setProperty("server.ssl.enabled", "true");
		System.setProperty("server.ssl.key-store","classpath:ssl/java.p12");
		System.setProperty("server.ssl.key-store-password","123456");
		System.setProperty("server.ssl.key-store-type","PKCS12");
		SpringApplication.run(Main.class, args);
	}
}
