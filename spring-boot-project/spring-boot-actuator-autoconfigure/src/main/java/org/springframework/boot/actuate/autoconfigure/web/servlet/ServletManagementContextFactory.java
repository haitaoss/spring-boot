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

package org.springframework.boot.actuate.autoconfigure.web.servlet;

import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextFactory;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ManagementContextFactory} for servlet-based web applications.
 *
 * @author Andy Wilkinson
 */
class ServletManagementContextFactory implements ManagementContextFactory {

	@Override
	public ConfigurableWebServerApplicationContext createManagementContext(ApplicationContext parent,
			Class<?>... configClasses) {
		Environment parentEnvironment = parent.getEnvironment();
		ConfigurableEnvironment childEnvironment = ApplicationContextFactory.DEFAULT
				.createEnvironment(WebApplicationType.SERVLET);
		if (parentEnvironment instanceof ConfigurableEnvironment) {
			childEnvironment.setConversionService(((ConfigurableEnvironment) parentEnvironment).getConversionService());
		}
		AnnotationConfigServletWebServerApplicationContext child = new AnnotationConfigServletWebServerApplicationContext();
		child.setEnvironment(childEnvironment);
		child.setParent(parent);
		List<Class<?>> combinedClasses = new ArrayList<>(Arrays.asList(configClasses));
		// ServletWebServerFactoryAutoConfiguration 是用来实现 WebServerFactory 的自动注入的
		combinedClasses.add(ServletWebServerFactoryAutoConfiguration.class);
		child.register(ClassUtils.toClassArray(combinedClasses));
		// 获取父容器的 ServletWebServerFactory 注册到 child 中
		registerServletWebServerFactory(parent, child);
		return child;
	}

	private void registerServletWebServerFactory(ApplicationContext parent,
			AnnotationConfigServletWebServerApplicationContext childContext) {
		try {
			ConfigurableListableBeanFactory beanFactory = childContext.getBeanFactory();
			if (beanFactory instanceof BeanDefinitionRegistry) {
				BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
				// 获取父容器的 ServletWebServerFactory
				registry.registerBeanDefinition("ServletWebServerFactory",
						new RootBeanDefinition(determineServletWebServerFactoryClass(parent)));
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Ignore and assume auto-configuration
		}
	}

	private Class<?> determineServletWebServerFactoryClass(ApplicationContext parent)
			throws NoSuchBeanDefinitionException {
		Class<?> factoryClass = parent.getBean(ServletWebServerFactory.class).getClass();
		if (cannotBeInstantiated(factoryClass)) {
			throw new FatalBeanException("ServletWebServerFactory implementation " + factoryClass.getName()
					+ " cannot be instantiated. To allow a separate management port to be used, a top-level class "
					+ "or static inner class should be used instead");
		}
		return factoryClass;
	}

	private boolean cannotBeInstantiated(Class<?> factoryClass) {
		return factoryClass.isLocalClass()
				|| (factoryClass.isMemberClass() && !Modifier.isStatic(factoryClass.getModifiers()))
				|| factoryClass.isAnonymousClass();
	}

}
