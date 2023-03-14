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

package org.springframework.boot.actuate.autoconfigure.endpoint.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.expose.EndpointExposure;
import org.springframework.boot.actuate.autoconfigure.endpoint.expose.IncludeExcludeEndpointFilter;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.EndpointDiscoverer;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvokerAdvisor;
import org.springframework.boot.actuate.endpoint.invoke.ParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.annotation.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for web {@link Endpoint @Endpoint}
 * support.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@AutoConfiguration(after = EndpointAutoConfiguration.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(WebEndpointProperties.class)
public class WebEndpointAutoConfiguration {

	private final ApplicationContext applicationContext;

	private final WebEndpointProperties properties;

	public WebEndpointAutoConfiguration(ApplicationContext applicationContext, WebEndpointProperties properties) {
		this.applicationContext = applicationContext;
		this.properties = properties;
	}

	/**
	 * 读取的属性
	 * 	management.endpoints.web.pathMapping.endpointId = newEndpointId
	 *
	 * PathMapper 会在下面的三个 EndpointDiscoverer 被用到，其作用是为了修改 endpoint 暴露的url
	 * @return
	 */
	@Bean
	public PathMapper webEndpointPathMapper() {
		return new MappingWebEndpointPathMapper(this.properties.getPathMapping());
	}

	@Bean
	@ConditionalOnMissingBean
	public EndpointMediaTypes endpointMediaTypes() {
		return EndpointMediaTypes.DEFAULT;
	}

	@Bean
	@ConditionalOnMissingBean(WebEndpointsSupplier.class)
	public WebEndpointDiscoverer webEndpointDiscoverer(ParameterValueMapper parameterValueMapper,
			EndpointMediaTypes endpointMediaTypes, ObjectProvider<PathMapper> endpointPathMappers,
			ObjectProvider<OperationInvokerAdvisor> invokerAdvisors,
													   ObjectProvider<EndpointFilter<ExposableWebEndpoint>> filters) {
		/**
		 * - ParameterValueMapper 反射回调方法时，会使用这个来对参数进行装换
		 * - PathMapper 用来替换 endpoint 暴露的url
		 * - OperationInvokerAdvisor 用来对调用@ReadOperation、@WriteOperation、@DeleteOperation方法的装饰类进行增强
		 * 		{@link EndpointDiscoverer#getOperationsFactory(ParameterValueMapper, Collection)}
		 * 		{@link DiscoveredOperationsFactory#createOperation(EndpointId, Object, Method, OperationType, Class)}
		 *
		 * - EndpointFilter<ExposableWebEndpoint> 过滤哪些 endpoint 应该暴露
		 * */
		return new WebEndpointDiscoverer(this.applicationContext, parameterValueMapper, endpointMediaTypes,
				endpointPathMappers.orderedStream().collect(Collectors.toList()),
				invokerAdvisors.orderedStream().collect(Collectors.toList()),
				filters.orderedStream().collect(Collectors.toList()));
	}

	@Bean
	@ConditionalOnMissingBean(ControllerEndpointsSupplier.class)
	public ControllerEndpointDiscoverer controllerEndpointDiscoverer(ObjectProvider<PathMapper> endpointPathMappers,
			ObjectProvider<Collection<EndpointFilter<ExposableControllerEndpoint>>> filters) {
		/**
		 * - PathMapper 用来替换 endpoint 暴露的url
		 * - EndpointFilter<ExposableControllerEndpoint> 过滤哪些 endpoint 应该暴露
		 * */
		return new ControllerEndpointDiscoverer(this.applicationContext,
				endpointPathMappers.orderedStream().collect(Collectors.toList()),
				filters.getIfAvailable(Collections::emptyList));
	}

	@Bean
	@ConditionalOnMissingBean
	public PathMappedEndpoints pathMappedEndpoints(Collection<EndpointsSupplier<?>> endpointSuppliers) {
		/**
		 * endpointSuppliers 其实就是 webEndpointDiscoverer、controllerEndpointDiscoverer、servletEndpointDiscoverer 的集合
		 * */
		return new PathMappedEndpoints(this.properties.getBasePath(), endpointSuppliers);
	}

	@Bean
	public IncludeExcludeEndpointFilter<ExposableWebEndpoint> webExposeExcludePropertyEndpointFilter() {
		/**
		 * 根据下面两个属性 构造出 IncludeExcludeEndpointFilter
		 * 	management.endpoints.web.exposure.include
		 * 	management.endpoints.web.exposure.exclude
		 * */
		WebEndpointProperties.Exposure exposure = this.properties.getExposure();
		return new IncludeExcludeEndpointFilter<>(ExposableWebEndpoint.class, exposure.getInclude(),
				exposure.getExclude(), EndpointExposure.WEB.getDefaultIncludes());
	}

	@Bean
	public IncludeExcludeEndpointFilter<ExposableControllerEndpoint> controllerExposeExcludePropertyEndpointFilter() {
		/**
		 * 根据下面两个属性 构造出 IncludeExcludeEndpointFilter
		 * management.endpoints.web.exposure.include
		 * management.endpoints.web.exposure.exclude
		 * */
		WebEndpointProperties.Exposure exposure = this.properties.getExposure();
		return new IncludeExcludeEndpointFilter<>(ExposableControllerEndpoint.class, exposure.getInclude(),
				exposure.getExclude());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.SERVLET)
	static class WebEndpointServletConfiguration {

		@Bean
		@ConditionalOnMissingBean(ServletEndpointsSupplier.class)
		ServletEndpointDiscoverer servletEndpointDiscoverer(ApplicationContext applicationContext,
				ObjectProvider<PathMapper> endpointPathMappers,
				ObjectProvider<EndpointFilter<ExposableServletEndpoint>> filters) {
			/**
			 * - PathMapper 用来替换 endpoint 暴露的url
			 * - EndpointFilter<ExposableServletEndpoint> 过滤哪些 endpoint 应该暴露
			 * */
			return new ServletEndpointDiscoverer(applicationContext,
					endpointPathMappers.orderedStream().collect(Collectors.toList()),
					filters.orderedStream().collect(Collectors.toList()));
		}

	}

}
