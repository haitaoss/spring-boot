/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.autoconfigure.web.server;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextFactory;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;

import java.util.List;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the management context. If the
 * {@code management.server.port} is the same as the {@code server.port} the management
 * context will be the same as the main application context. If the
 * {@code management.server.port} is different to the {@code server.port} the management
 * context will be a separate context that has the main application context as its parent.
 *
 * @author Andy Wilkinson
 * @since 2.0.0
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties({ WebEndpointProperties.class, ManagementServerProperties.class })
public class ManagementContextAutoConfiguration {

	/**
	 * @ConditionalOnManagementPort(ManagementPortType.SAME) 的作用是校验
	 * 		management.server.port == server.port 就满足
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnManagementPort(ManagementPortType.SAME)
	static class SameManagementContextConfiguration implements SmartInitializingSingleton {

		private final Environment environment;

		SameManagementContextConfiguration(Environment environment) {
			this.environment = environment;
		}

		@Override
		public void afterSingletonsInstantiated() {
			verifySslConfiguration();
			verifyAddressConfiguration();
			if (this.environment instanceof ConfigurableEnvironment) {
				addLocalManagementPortPropertyAlias((ConfigurableEnvironment) this.environment);
			}
		}

		private void verifySslConfiguration() {
			Boolean enabled = this.environment.getProperty("management.server.ssl.enabled", Boolean.class, false);
			Assert.state(!enabled, "Management-specific SSL cannot be configured as the management "
					+ "server is not listening on a separate port");
		}

		private void verifyAddressConfiguration() {
			Object address = this.environment.getProperty("management.server.address");
			Assert.state(address == null, "Management-specific server address cannot be configured as the management "
					+ "server is not listening on a separate port");
		}

		/**
		 * Add an alias for 'local.management.port' that actually resolves using
		 * 'local.server.port'.
		 * @param environment the environment
		 */
		private void addLocalManagementPortPropertyAlias(ConfigurableEnvironment environment) {
			environment.getPropertySources().addLast(new PropertySource<Object>("Management Server") {

				@Override
				public Object getProperty(String name) {
					if ("local.management.port".equals(name)) {
						return environment.getProperty("local.server.port");
					}
					return null;
				}

			});
		}

		/**
		 * @EnableManagementContext 这个很重要，会读取 META-INF/spring.factories 和 spring/`ManagementContextConfiguration.class.getName()`.imports 中的内容
		 * 注册到 BeanFactory 中
		 */
		@Configuration(proxyBeanMethods = false)
		@EnableManagementContext(ManagementContextType.SAME)
		static class EnableSameManagementContextConfiguration {

		}

	}

	/**
	 * @ConditionalOnManagementPort(ManagementPortType.DIFFERENT) 的作用是校验 management.server.port != server.port 就满足
	 *
	 * 其实就是又启动一个 WebServer
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
	static class DifferentManagementContextConfiguration implements ApplicationListener<WebServerInitializedEvent> {

		private final ApplicationContext applicationContext;

		private final ManagementContextFactory managementContextFactory;

		DifferentManagementContextConfiguration(ApplicationContext applicationContext,
				ManagementContextFactory managementContextFactory) {
			this.applicationContext = applicationContext;
			this.managementContextFactory = managementContextFactory;
		}

		@Override
		public void onApplicationEvent(WebServerInitializedEvent event) {
			/**
			 * Web类型的IOC容器执行 onRefresh 会注册一个 WebServerStartStopLifecycle，这个 Lifecycle 会发布 WebServerInitializedEvent 事件
			 * {@link ServletWebServerApplicationContext#createWebServer()}
			 * */
			if (event.getApplicationContext().equals(this.applicationContext)) {
				/**
				 * 创建 ConfigurableWebServerApplicationContext
				 *
				 * 1. applicationContext 会设置为 managementContext 的 父容器
				 * 2. EnableChildManagementContextConfiguration 会读取 META-INF/spring.factories 和 spring/`ManagementContextConfiguration.class.getName()`.imports 中的内容
				 *		注：文件中定义了这个 {@link WebMvcEndpointChildContextConfiguration} 其作用是启用 SpringMVC
				 * */
				ConfigurableWebServerApplicationContext managementContext = this.managementContextFactory
						.createManagementContext(this.applicationContext,
								EnableChildManagementContextConfiguration.class,
								PropertyPlaceholderAutoConfiguration.class);
				if (isLazyInitialization()) {
					managementContext.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
				}
				managementContext.setServerNamespace("management");
				managementContext.setId(this.applicationContext.getId() + ":management");
				// 设置 applicationContext 的 ClassLoader 给 managementContext
				setClassLoaderIfPossible(managementContext);
				// 给 applicationContext 注册 CloseManagementContextListener，当 applicationContext close 时 关闭 managementContext
				CloseManagementContextListener.addIfPossible(this.applicationContext, managementContext);
				// refresh 也就是会又启动一个 webServer
				managementContext.refresh();
			}
		}

		protected boolean isLazyInitialization() {
			AbstractApplicationContext context = (AbstractApplicationContext) this.applicationContext;
			List<BeanFactoryPostProcessor> postProcessors = context.getBeanFactoryPostProcessors();
			return postProcessors.stream().anyMatch(LazyInitializationBeanFactoryPostProcessor.class::isInstance);
		}

		private void setClassLoaderIfPossible(ConfigurableApplicationContext child) {
			if (child instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) child).setClassLoader(this.applicationContext.getClassLoader());
			}
		}

	}

	/**
	 * {@link ApplicationListener} to propagate the {@link ContextClosedEvent} and
	 * {@link ApplicationFailedEvent} from a parent to a child.
	 */
	private static class CloseManagementContextListener implements ApplicationListener<ApplicationEvent> {

		private final ApplicationContext parentContext;

		private final ConfigurableApplicationContext childContext;

		CloseManagementContextListener(ApplicationContext parentContext, ConfigurableApplicationContext childContext) {
			this.parentContext = parentContext;
			this.childContext = childContext;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			if (event instanceof ContextClosedEvent) {
				onContextClosedEvent((ContextClosedEvent) event);
			}
			if (event instanceof ApplicationFailedEvent) {
				onApplicationFailedEvent((ApplicationFailedEvent) event);
			}
		}

		private void onContextClosedEvent(ContextClosedEvent event) {
			propagateCloseIfNecessary(event.getApplicationContext());
		}

		private void onApplicationFailedEvent(ApplicationFailedEvent event) {
			propagateCloseIfNecessary(event.getApplicationContext());
		}

		private void propagateCloseIfNecessary(ApplicationContext applicationContext) {
			if (applicationContext == this.parentContext) {
				this.childContext.close();
			}
		}

		static void addIfPossible(ApplicationContext parentContext, ConfigurableApplicationContext childContext) {
			if (parentContext instanceof ConfigurableApplicationContext) {
				add((ConfigurableApplicationContext) parentContext, childContext);
			}
		}

		private static void add(ConfigurableApplicationContext parentContext,
				ConfigurableApplicationContext childContext) {
			parentContext.addApplicationListener(new CloseManagementContextListener(parentContext, childContext));
		}

	}

}
