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

package org.springframework.boot.autoconfigure.security.servlet;

import javax.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.security.ConditionalOnDefaultWebSecurity;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.filter.ErrorPageSecurityFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;

/**
 * {@link Configuration @Configuration} class securing servlet applications.
 *
 * @author Madhura Bhave
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
class SpringBootWebSecurityConfiguration {

	/**
	 * The default configuration for web security. It relies on Spring Security's
	 * content-negotiation strategy to determine what sort of authentication to use. If
	 * the user specifies their own {@code WebSecurityConfigurerAdapter} or
	 * {@link SecurityFilterChain} bean, this will back-off completely and the users
	 * should specify all the bits that they want to configure as part of the custom
	 * security configuration.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnDefaultWebSecurity
	static class SecurityFilterChainConfiguration {

		@Bean
		@Order(SecurityProperties.BASIC_AUTH_ORDER)
		SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
			// 所有请求都需要认证过才可以访问
			http.authorizeRequests().anyRequest().authenticated();
			// 会注册基于 username 和 password 参数的认证Filter
			http.formLogin();
			// 会注册基于请求头的认证Filter
			http.httpBasic();
			return http.build();
		}

	}

	/**
	 * Configures the {@link ErrorPageSecurityFilter}.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(WebInvocationPrivilegeEvaluator.class)
	@ConditionalOnBean(WebInvocationPrivilegeEvaluator.class)
	static class ErrorPageSecurityFilterConfiguration {

		@Bean
		FilterRegistrationBean<ErrorPageSecurityFilter> errorPageSecurityFilter(ApplicationContext context) {
			// FilterRegistrationBean 类型的bean最终会注册到 Web容器中作为Filter
			FilterRegistrationBean<ErrorPageSecurityFilter> registration = new FilterRegistrationBean<>(
					new ErrorPageSecurityFilter(context));
			// 对于转发请求，只有转发类型是ERROR才会使用上这个Filter
			registration.setDispatcherTypes(DispatcherType.ERROR);
			return registration;
		}

	}

	/**
	 * Adds the {@link EnableWebSecurity @EnableWebSecurity} annotation if Spring Security
	 * is on the classpath. This will make sure that the annotation is present with
	 * default security auto-configuration and also if the user adds custom security and
	 * forgets to add the annotation. If {@link EnableWebSecurity @EnableWebSecurity} has
	 * already been added or if a bean with name
	 * {@value BeanIds#SPRING_SECURITY_FILTER_CHAIN} has been configured by the user, this
	 * will back-off.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(name = BeanIds.SPRING_SECURITY_FILTER_CHAIN)
	@ConditionalOnClass(EnableWebSecurity.class)
	@EnableWebSecurity
	static class WebSecurityEnablerConfiguration {

	}

}
