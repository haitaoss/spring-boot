/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure.endpoint.web;

import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.boot.actuate.autoconfigure.endpoint.expose.IncludeExcludeEndpointFilter;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.endpoint.web.ExposableServletEndpoint;
import org.springframework.boot.actuate.endpoint.web.ServletEndpointRegistrar;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.autoconfigure.web.servlet.JerseyApplicationPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * {@link ManagementContextConfiguration @ManagementContextConfiguration} for servlet
 * endpoints.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @since 2.0.0
 */
@ManagementContextConfiguration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ServletEndpointManagementContextConfiguration {

	@Bean
	public IncludeExcludeEndpointFilter<ExposableServletEndpoint> servletExposeExcludePropertyEndpointFilter(
			WebEndpointProperties properties) {
		/**
		 * 根据下面两个属性 构造出 IncludeExcludeEndpointFilter
		 * 	management.endpoints.web.exposure.include
		 * 	management.endpoints.web.exposure.exclude
		 * */
		WebEndpointProperties.Exposure exposure = properties.getExposure();
		return new IncludeExcludeEndpointFilter<>(ExposableServletEndpoint.class, exposure.getInclude(),
				exposure.getExclude());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(DispatcherServlet.class)
	public static class WebMvcServletEndpointManagementContextConfiguration {

		@Bean
		public ServletEndpointRegistrar servletEndpointRegistrar(WebEndpointProperties properties,
				ServletEndpointsSupplier servletEndpointsSupplier, DispatcherServletPath dispatcherServletPath) {
			/**
			 *  - properties 是用来拼接路径前缀的
			 *  - servletEndpointsSupplier 是用来获取 @ServletEndpoint 的bean的
			 *
			 *  将 servletEndpointsSupplier.getEndpoints() 的映射成 Servlet ,然后使用 ServletContext 进行注册
			 * */
			return new ServletEndpointRegistrar(dispatcherServletPath.getRelativePath(properties.getBasePath()),
					servletEndpointsSupplier.getEndpoints());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ResourceConfig.class)
	@ConditionalOnMissingClass("org.springframework.web.servlet.DispatcherServlet")
	public static class JerseyServletEndpointManagementContextConfiguration {

		@Bean
		public ServletEndpointRegistrar servletEndpointRegistrar(WebEndpointProperties properties,
				ServletEndpointsSupplier servletEndpointsSupplier, JerseyApplicationPath jerseyApplicationPath) {
			return new ServletEndpointRegistrar(jerseyApplicationPath.getRelativePath(properties.getBasePath()),
					servletEndpointsSupplier.getEndpoints());
		}

	}

}
