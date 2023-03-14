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

package org.springframework.boot.actuate.autoconfigure.endpoint.condition;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.actuate.autoconfigure.endpoint.expose.EndpointExposure;
import org.springframework.boot.actuate.autoconfigure.endpoint.expose.IncludeExcludeEndpointFilter;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.EndpointExtension;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * A condition that checks if an endpoint is available (i.e. enabled and exposed).
 *
 * @author Brian Clozel
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @see ConditionalOnAvailableEndpoint
 */
class OnAvailableEndpointCondition extends SpringBootCondition {

	private static final String JMX_ENABLED_KEY = "spring.jmx.enabled";

	private static final String ENABLED_BY_DEFAULT_KEY = "management.endpoints.enabled-by-default";

	private static final Map<Environment, Set<ExposureFilter>> exposureFiltersCache = new ConcurrentReferenceHashMap<>();

	private static final ConcurrentReferenceHashMap<Environment, Optional<Boolean>> enabledByDefaultCache = new ConcurrentReferenceHashMap<>();

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// 环境变量
		Environment environment = context.getEnvironment();
		// 从元数据中获取 ConditionalOnAvailableEndpoint 注解的值
		MergedAnnotation<ConditionalOnAvailableEndpoint> conditionAnnotation = metadata.getAnnotations()
				.get(ConditionalOnAvailableEndpoint.class);
		/**
		 * 比如 @ConditionalOnAvailableEndpoint(endpoint = EnvironmentEndpoint.class) 返回的
		 * target 就是 EnvironmentEndpoint
		 * */
		Class<?> target = getTarget(context, metadata, conditionAnnotation);
		/**
		 * target 必须得有 @Endpoint 或者 @EndpointExtension
		 * 否则就报错 ，拿到 @Endpoint 注解的信息然后返回
		 * */
		MergedAnnotation<Endpoint> endpointAnnotation = getEndpointAnnotation(target);
		return getMatchOutcome(environment, conditionAnnotation, endpointAnnotation);
	}

	private Class<?> getTarget(ConditionContext context, AnnotatedTypeMetadata metadata,
			MergedAnnotation<ConditionalOnAvailableEndpoint> condition) {
		// 获取 ConditionalOnAvailableEndpoint 注解的 endpoint 属性 的值
		Class<?> target = condition.getClass("endpoint");
		// 不是 Void 就返回
		if (target != Void.class) {
			return target;
		}
		// 是方法 且 有@Bean注解
		Assert.state(metadata instanceof MethodMetadata && metadata.isAnnotated(Bean.class.getName()),
				"EndpointCondition must be used on @Bean methods when the endpoint is not specified");
		MethodMetadata methodMetadata = (MethodMetadata) metadata;
		try {
			// 返回方法返回值类型
			return ClassUtils.forName(methodMetadata.getReturnTypeName(), context.getClassLoader());
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to extract endpoint id for "
					+ methodMetadata.getDeclaringClassName() + "." + methodMetadata.getMethodName(), ex);
		}
	}

	protected MergedAnnotation<Endpoint> getEndpointAnnotation(Class<?> target) {
		MergedAnnotations annotations = MergedAnnotations.from(target, SearchStrategy.TYPE_HIERARCHY);
		// 存在 Endpoint 注解就返回
		MergedAnnotation<Endpoint> endpoint = annotations.get(Endpoint.class);
		if (endpoint.isPresent()) {
			return endpoint;
		}
		// 存在 EndpointExtension 也行
		MergedAnnotation<EndpointExtension> extension = annotations.get(EndpointExtension.class);
		Assert.state(extension.isPresent(), "No endpoint is specified and the return type of the @Bean method is "
				+ "neither an @Endpoint, nor an @EndpointExtension");
		return getEndpointAnnotation(extension.getClass("endpoint"));
	}

	private ConditionOutcome getMatchOutcome(Environment environment,
			MergedAnnotation<ConditionalOnAvailableEndpoint> conditionAnnotation,
			MergedAnnotation<Endpoint> endpointAnnotation) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnAvailableEndpoint.class);
		/**
		 * @Endpoint(id = "env")
		 * public class EnvironmentEndpoint {}
		 *
		 * 其实就是将 注解值装饰成 EndpointId 对象
		 * */
		EndpointId endpointId = EndpointId.of(environment, endpointAnnotation.getString("id"));

		/**
		 * 启用结果的值 获取逻辑：
		 * 	1. 从environment读取属性 management.endpoint.`endpointId.toLowerCaseString()`.enabled 的值
		 * 	2. 从environment读取属性 management.endpoints.enabled-by-default 的值
		 * 	3. 注解的值 @Endpoint.enableByDefault() , 这个值默认是true
		 * */
		ConditionOutcome enablementOutcome = getEnablementOutcome(environment, endpointAnnotation, endpointId, message);
		// 未启用，就直接返回
		if (!enablementOutcome.isMatch()) {
			return enablementOutcome;
		}
		/**
		 * 启用，那就紧接着校验一下 @ConditionalOnAvailableEndpoint 允许暴露的端点
		 *
		 * @ConditionalOnAvailableEndpoint(exposure={}) 会映射成这个枚举的值 {@link EndpointExposure}
		 * 		{ JMX("*"),WEB("health"),CLOUD_FOUNDRY("*") }
		 *
		 * */
		Set<EndpointExposure> exposuresToCheck = getExposuresToCheck(conditionAnnotation);
		/**
		 * 默认是只有这一个 new ExposureFilter(environment, EndpointExposure.WEB)
		 *
		 * 实例化时就会从 environment 中获取属性值，来匹配是否暴露
		 * 	management.endpoints.[web|jmx|cloud-foundry].exposure.include
		 *  management.endpoints.[web|jmx|cloud-foundry].exposure.exclude
		 * {@link ExposureFilter#ExposureFilter(Environment, EndpointExposure)}
		 * */
		Set<ExposureFilter> exposureFilters = getExposureFilters(environment);
		for (ExposureFilter exposureFilter : exposureFilters) {
			/**
			 * exposuresToCheck.contains(exposureFilter.getExposure()) ---> 默认就是true
			 * exposureFilter.isExposed(endpointId) ---> include满足 + 不满足exclude 就是true
			 * */
			if (exposuresToCheck.contains(exposureFilter.getExposure()) && exposureFilter.isExposed(endpointId)) {
				return ConditionOutcome.match(message.because("marked as exposed by a 'management.endpoints."
						+ exposureFilter.getExposure().name().toLowerCase() + ".exposure' property"));
			}
		}
		return ConditionOutcome.noMatch(message.because("no 'management.endpoints' property marked it as exposed"));
	}

	private ConditionOutcome getEnablementOutcome(Environment environment,
			MergedAnnotation<Endpoint> endpointAnnotation, EndpointId endpointId, ConditionMessage.Builder message) {
		String key = "management.endpoint." + endpointId.toLowerCaseString() + ".enabled";
		Boolean userDefinedEnabled = environment.getProperty(key, Boolean.class);
		if (userDefinedEnabled != null) {
			return new ConditionOutcome(userDefinedEnabled,
					message.because("found property " + key + " with value " + userDefinedEnabled));
		}
		// 属性值 management.endpoints.enabled-by-default
		Boolean userDefinedDefault = isEnabledByDefault(environment);
		if (userDefinedDefault != null) {
			return new ConditionOutcome(userDefinedDefault, message.because(
					"no property " + key + " found so using user defined default from " + ENABLED_BY_DEFAULT_KEY));
		}
		// 获取 @Endpoint 注解值，决定是否启用，默认是启用
		boolean endpointDefault = endpointAnnotation.getBoolean("enableByDefault");
		return new ConditionOutcome(endpointDefault,
				message.because("no property " + key + " found so using endpoint default of " + endpointDefault));
	}

	private Boolean isEnabledByDefault(Environment environment) {
		Optional<Boolean> enabledByDefault = enabledByDefaultCache.get(environment);
		if (enabledByDefault == null) {
			enabledByDefault = Optional.ofNullable(environment.getProperty(ENABLED_BY_DEFAULT_KEY, Boolean.class));
			enabledByDefaultCache.put(environment, enabledByDefault);
		}
		return enabledByDefault.orElse(null);
	}

	private Set<EndpointExposure> getExposuresToCheck(
			MergedAnnotation<ConditionalOnAvailableEndpoint> conditionAnnotation) {
		EndpointExposure[] exposure = conditionAnnotation.getEnumArray("exposure", EndpointExposure.class);
		return (exposure.length == 0) ? EnumSet.allOf(EndpointExposure.class)
				: new LinkedHashSet<>(Arrays.asList(exposure));
	}

	private Set<ExposureFilter> getExposureFilters(Environment environment) {
		Set<ExposureFilter> exposureFilters = exposureFiltersCache.get(environment);
		if (exposureFilters == null) {
			exposureFilters = new HashSet<>(2);
			if (environment.getProperty(JMX_ENABLED_KEY, Boolean.class, false)) {
				exposureFilters.add(new ExposureFilter(environment, EndpointExposure.JMX));
			}
			if (CloudPlatform.CLOUD_FOUNDRY.isActive(environment)) {
				exposureFilters.add(new ExposureFilter(environment, EndpointExposure.CLOUD_FOUNDRY));
			}
			exposureFilters.add(new ExposureFilter(environment, EndpointExposure.WEB));
			exposureFiltersCache.put(environment, exposureFilters);
		}
		return exposureFilters;
	}

	static final class ExposureFilter extends IncludeExcludeEndpointFilter<ExposableEndpoint<?>> {

		private final EndpointExposure exposure;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private ExposureFilter(Environment environment, EndpointExposure exposure) {
			/**
			 * 第二个参数很重要，会获取到 include 和 exclude 的值
			 *
			 * "management.endpoints." + getCanonicalName(exposure) + ".exposure" + ".include"
			 * "management.endpoints." + getCanonicalName(exposure) + ".exposure" + ".exclude"
			 * */
			super((Class) ExposableEndpoint.class, environment,
					"management.endpoints." + getCanonicalName(exposure) + ".exposure", exposure.getDefaultIncludes());
			this.exposure = exposure;

		}

		private static String getCanonicalName(EndpointExposure exposure) {
			if (EndpointExposure.CLOUD_FOUNDRY.equals(exposure)) {
				return "cloud-foundry";
			}
			return exposure.name().toLowerCase();
		}

		EndpointExposure getExposure() {
			return this.exposure;
		}

		boolean isExposed(EndpointId id) {
			return super.match(id);
		}

	}

}
