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

package org.springframework.boot.actuate.endpoint.annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.Operation;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvoker;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvokerAdvisor;
import org.springframework.boot.actuate.endpoint.invoke.ParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointDiscoverer;
import org.springframework.boot.util.LambdaSafe;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * A Base for {@link EndpointsSupplier} implementations that discover
 * {@link Endpoint @Endpoint} beans and {@link EndpointExtension @EndpointExtension} beans
 * in an application context.
 *
 * @param <E> the endpoint type
 * @param <O> the operation type
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 2.0.0
 */
public abstract class EndpointDiscoverer<E extends ExposableEndpoint<O>, O extends Operation>
		implements EndpointsSupplier<E> {

	private final ApplicationContext applicationContext;

	private final Collection<EndpointFilter<E>> filters;

	private final DiscoveredOperationsFactory<O> operationsFactory;

	private final Map<EndpointBean, E> filterEndpoints = new ConcurrentHashMap<>();

	private volatile Collection<E> endpoints;

	/**
	 * Create a new {@link EndpointDiscoverer} instance.
	 * @param applicationContext the source application context
	 * @param parameterValueMapper the parameter value mapper
	 * @param invokerAdvisors invoker advisors to apply
	 * @param filters filters to apply
	 */
	public EndpointDiscoverer(ApplicationContext applicationContext, ParameterValueMapper parameterValueMapper,
			Collection<OperationInvokerAdvisor> invokerAdvisors, Collection<EndpointFilter<E>> filters) {
		Assert.notNull(applicationContext, "ApplicationContext must not be null");
		Assert.notNull(parameterValueMapper, "ParameterValueMapper must not be null");
		Assert.notNull(invokerAdvisors, "InvokerAdvisors must not be null");
		Assert.notNull(filters, "Filters must not be null");
		this.applicationContext = applicationContext;
		this.filters = Collections.unmodifiableCollection(filters);
		this.operationsFactory = getOperationsFactory(parameterValueMapper, invokerAdvisors);
	}

	private DiscoveredOperationsFactory<O> getOperationsFactory(ParameterValueMapper parameterValueMapper,
			Collection<OperationInvokerAdvisor> invokerAdvisors) {
		return new DiscoveredOperationsFactory<O>(parameterValueMapper, invokerAdvisors) {

			@Override
			protected O createOperation(EndpointId endpointId, DiscoveredOperationMethod operationMethod,
					OperationInvoker invoker) {
				return EndpointDiscoverer.this.createOperation(endpointId, operationMethod, invoker);
			}

		};
	}

	@Override
	public final Collection<E> getEndpoints() {
		if (this.endpoints == null) {
			this.endpoints = discoverEndpoints();
		}
		return this.endpoints;
	}

	private Collection<E> discoverEndpoints() {
		// 找到标注了 @Endpoint 的bean 映射成 EndpointBean 返回
		Collection<EndpointBean> endpointBeans = createEndpointBeans();
		/**
		 * 找到标注了 @EndpointExtension 的bean 映射成 ExtensionBean
		 * 然后执行配置的Filter 满足了 Filter 才将 extensionBean 关联给 EndpointBean
		 * 		@EndpointExtension(filter=A.class)
		 * */
		addExtensionBeans(endpointBeans);
		// 会解析 @Endpoint 中的 @ReadOperation 、@WriteOperation 、@DeleteOperation
		return convertToEndpoints(endpointBeans);
	}

	private Collection<EndpointBean> createEndpointBeans() {
		Map<EndpointId, EndpointBean> byId = new LinkedHashMap<>();
		// 找到有 @Endpoint 的bean
		String[] beanNames = BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(this.applicationContext,
				Endpoint.class);
		for (String beanName : beanNames) {
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				/**
				 * 装饰成 EndpointBean
				 *
				 * new EndpointBean(Environment, beanName, beanType, () -> getBean(beanName))
				 * */
				EndpointBean endpointBean = createEndpointBean(beanName);
				EndpointBean previous = byId.putIfAbsent(endpointBean.getId(), endpointBean);
				// 存在重复的ID 就直接报错
				Assert.state(previous == null, () -> "Found two endpoints with the id '" + endpointBean.getId() + "': '"
						+ endpointBean.getBeanName() + "' and '" + previous.getBeanName() + "'");
			}
		}
		// 返回结果
		return byId.values();
	}

	private EndpointBean createEndpointBean(String beanName) {
		Class<?> beanType = ClassUtils.getUserClass(this.applicationContext.getType(beanName, false));
		Supplier<Object> beanSupplier = () -> this.applicationContext.getBean(beanName);
		return new EndpointBean(this.applicationContext.getEnvironment(), beanName, beanType, beanSupplier);
	}

	private void addExtensionBeans(Collection<EndpointBean> endpointBeans) {
		Map<EndpointId, EndpointBean> byId = endpointBeans.stream()
				.collect(Collectors.toMap(EndpointBean::getId, Function.identity()));
		// 找到有 @EndpointExtension 的bean
		String[] beanNames = BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(this.applicationContext,
				EndpointExtension.class);
		for (String beanName : beanNames) {
			// 装饰成 ExtensionBean
			ExtensionBean extensionBean = createExtensionBean(beanName);
			// 查找对应的 Endpoint，找不到就报错
			EndpointBean endpointBean = byId.get(extensionBean.getEndpointId());
			Assert.state(endpointBean != null, () -> ("Invalid extension '" + extensionBean.getBeanName()
					+ "': no endpoint found with id '" + extensionBean.getEndpointId() + "'"));
			/**
			 * 将 extensionBean 设置给 endpointBean
			 *
			 * 注：得 @EndpointExtension(filter=A.class) 满足了 Filter 才行
			 * */
			addExtensionBean(endpointBean, extensionBean);
		}
	}

	private ExtensionBean createExtensionBean(String beanName) {
		Class<?> beanType = ClassUtils.getUserClass(this.applicationContext.getType(beanName));
		Supplier<Object> beanSupplier = () -> this.applicationContext.getBean(beanName);
		return new ExtensionBean(this.applicationContext.getEnvironment(), beanName, beanType, beanSupplier);
	}

	private void addExtensionBean(EndpointBean endpointBean, ExtensionBean extensionBean) {
		/**
		 * @EndpointExtension(filter=A.class) 满足了 Filter 才行
		 * */
		if (isExtensionExposed(endpointBean, extensionBean)) {
			Assert.state(isEndpointExposed(endpointBean) || isEndpointFiltered(endpointBean),
					() -> "Endpoint bean '" + endpointBean.getBeanName() + "' cannot support the extension bean '"
							+ extensionBean.getBeanName() + "'");
			endpointBean.addExtension(extensionBean);
		}
	}

	private Collection<E> convertToEndpoints(Collection<EndpointBean> endpointBeans) {
		Set<E> endpoints = new LinkedHashSet<>();
		for (EndpointBean endpointBean : endpointBeans) {
			/**
			 * 满足 @FilteredEndpoint  &&
			 * 满足 {@link EndpointDiscoverer#filters} &&
			 * 满足 {@link ControllerEndpointDiscoverer#isEndpointTypeExposed(Class)}
			 * 		这个就是校验类上有 @ControllerEndpoint @RestControllerEndpoint 才行
			 * */
			if (isEndpointExposed(endpointBean)) {
				endpoints.add(convertToEndpoint(endpointBean));
			}
		}
		return Collections.unmodifiableSet(endpoints);
	}

	private E convertToEndpoint(EndpointBean endpointBean) {
		MultiValueMap<OperationKey, O> indexed = new LinkedMultiValueMap<>();
		EndpointId id = endpointBean.getId();
		// 拿到标注了 @ReadOperation 、@WriteOperation 、@DeleteOperation 的方法 装饰 然后返回
		addOperations(indexed, id, endpointBean.getBean(), false);
		if (endpointBean.getExtensions().size() > 1) {
			String extensionBeans = endpointBean.getExtensions().stream().map(ExtensionBean::getBeanName)
					.collect(Collectors.joining(", "));
			throw new IllegalStateException("Found multiple extensions for the endpoint bean "
					+ endpointBean.getBeanName() + " (" + extensionBeans + ")");
		}
		for (ExtensionBean extensionBean : endpointBean.getExtensions()) {
			// 同上
			addOperations(indexed, id, extensionBean.getBean(), true);
		}
		// 重复 key 就报错
		assertNoDuplicateOperations(endpointBean, indexed);
		List<O> operations = indexed.values().stream().map(this::getLast).filter(Objects::nonNull)
				.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
		// 装饰然后返回
		return createEndpoint(endpointBean.getBean(), id, endpointBean.isEnabledByDefault(), operations);
	}

	private void addOperations(MultiValueMap<OperationKey, O> indexed, EndpointId id, Object target,
			boolean replaceLast) {
		Set<OperationKey> replacedLast = new HashSet<>();
		/**
		 * 拿到标注了 @ReadOperation 、@WriteOperation 、@DeleteOperation 的方法 装饰 然后返回
		 *
		 * 将 Method 装饰成 DiscoveredOperationMethod
		 * 将 target、DiscoveredOperationMethod、parameterValueMapper 装饰成 OperationInvoker
		 * 使用 OperationInvokerAdvisor 对 OperationInvoker 进行装饰
		 * 最后 DiscoveredOperationMethod + OperationInvokerAdvisor 构造出 Operation
		 * */
		Collection<O> operations = this.operationsFactory.createOperations(id, target);
		for (O operation : operations) {
			OperationKey key = createOperationKey(operation);
			O last = getLast(indexed.get(key));
			if (replaceLast && replacedLast.add(key) && last != null) {
				indexed.get(key).remove(last);
			}
			indexed.add(key, operation);
		}
	}

	private <T> T getLast(List<T> list) {
		return CollectionUtils.isEmpty(list) ? null : list.get(list.size() - 1);
	}

	private void assertNoDuplicateOperations(EndpointBean endpointBean, MultiValueMap<OperationKey, O> indexed) {
		List<OperationKey> duplicates = indexed.entrySet().stream().filter((entry) -> entry.getValue().size() > 1)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		if (!duplicates.isEmpty()) {
			Set<ExtensionBean> extensions = endpointBean.getExtensions();
			String extensionBeanNames = extensions.stream().map(ExtensionBean::getBeanName)
					.collect(Collectors.joining(", "));
			throw new IllegalStateException("Unable to map duplicate endpoint operations: " + duplicates.toString()
					+ " to " + endpointBean.getBeanName()
					+ (extensions.isEmpty() ? "" : " (" + extensionBeanNames + ")"));
		}
	}

	private boolean isExtensionExposed(EndpointBean endpointBean, ExtensionBean extensionBean) {
		return isFilterMatch(extensionBean.getFilter(), endpointBean)
				&& isExtensionTypeExposed(extensionBean.getBeanType());
	}

	/**
	 * Determine if an extension bean should be exposed. Subclasses can override this
	 * method to provide additional logic.
	 * @param extensionBeanType the extension bean type
	 * @return {@code true} if the extension is exposed
	 */
	protected boolean isExtensionTypeExposed(Class<?> extensionBeanType) {
		return true;
	}

	private boolean isEndpointExposed(EndpointBean endpointBean) {
		return isFilterMatch(endpointBean.getFilter(), endpointBean) && !isEndpointFiltered(endpointBean)
			   // 类得有 @ControllerEndpoint @RestControllerEndpoint
				&& isEndpointTypeExposed(endpointBean.getBeanType());
	}

	/**
	 * Determine if an endpoint bean should be exposed. Subclasses can override this
	 * method to provide additional logic.
	 * @param beanType the endpoint bean type
	 * @return {@code true} if the endpoint is exposed
	 */
	protected boolean isEndpointTypeExposed(Class<?> beanType) {
		return true;
	}

	/**
	 * 是被过滤的，也就是不满足filter规则
	 * @param endpointBean
	 * @return
	 */
	private boolean isEndpointFiltered(EndpointBean endpointBean) {
		for (EndpointFilter<E> filter : this.filters) {
			// 不匹配，说明是被过滤的
			if (!isFilterMatch(filter, endpointBean)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private boolean isFilterMatch(Class<?> filter, EndpointBean endpointBean) {
		if (!isEndpointTypeExposed(endpointBean.getBeanType())) {
			return false;
		}
		if (filter == null) {
			return true;
		}
		E endpoint = getFilterEndpoint(endpointBean);
		// 拿到 泛型
		Class<?> generic = ResolvableType.forClass(EndpointFilter.class, filter).resolveGeneric(0);
		if (generic == null || generic.isInstance(endpoint)) {
			// 实例化 EndpointFilter
			EndpointFilter<E> instance = (EndpointFilter<E>) BeanUtils.instantiateClass(filter);
			return isFilterMatch(instance, endpoint);
		}
		return false;
	}

	private boolean isFilterMatch(EndpointFilter<E> filter, EndpointBean endpointBean) {
		return isFilterMatch(filter, getFilterEndpoint(endpointBean));
	}

	@SuppressWarnings("unchecked")
	private boolean isFilterMatch(EndpointFilter<E> filter, E endpoint) {
		return LambdaSafe.callback(EndpointFilter.class, filter, endpoint).withLogger(EndpointDiscoverer.class)
				.invokeAnd((f) -> f.match(endpoint)).get();
	}

	private E getFilterEndpoint(EndpointBean endpointBean) {
		E endpoint = this.filterEndpoints.get(endpointBean);
		if (endpoint == null) {
			endpoint = createEndpoint(endpointBean.getBean(), endpointBean.getId(), endpointBean.isEnabledByDefault(),
					Collections.emptySet());
			this.filterEndpoints.put(endpointBean, endpoint);
		}
		return endpoint;
	}

	@SuppressWarnings("unchecked")
	protected Class<? extends E> getEndpointType() {
		return (Class<? extends E>) ResolvableType.forClass(EndpointDiscoverer.class, getClass()).resolveGeneric(0);
	}

	/**
	 * Factory method called to create the {@link ExposableEndpoint endpoint}.
	 * @param endpointBean the source endpoint bean
	 * @param id the ID of the endpoint
	 * @param enabledByDefault if the endpoint is enabled by default
	 * @param operations the endpoint operations
	 * @return a created endpoint (a {@link DiscoveredEndpoint} is recommended)
	 */
	protected abstract E createEndpoint(Object endpointBean, EndpointId id, boolean enabledByDefault,
			Collection<O> operations);

	/**
	 * Factory method to create an {@link Operation endpoint operation}.
	 * @param endpointId the endpoint id
	 * @param operationMethod the operation method
	 * @param invoker the invoker to use
	 * @return a created operation
	 */
	protected abstract O createOperation(EndpointId endpointId, DiscoveredOperationMethod operationMethod,
			OperationInvoker invoker);

	/**
	 * Create an {@link OperationKey} for the given operation.
	 * @param operation the source operation
	 * @return the operation key
	 */
	protected abstract OperationKey createOperationKey(O operation);

	/**
	 * A key generated for an {@link Operation} based on specific criteria from the actual
	 * operation implementation.
	 */
	protected static final class OperationKey {

		private final Object key;

		private final Supplier<String> description;

		/**
		 * Create a new {@link OperationKey} instance.
		 * @param key the underlying key for the operation
		 * @param description a human-readable description of the key
		 */
		public OperationKey(Object key, Supplier<String> description) {
			Assert.notNull(key, "Key must not be null");
			Assert.notNull(description, "Description must not be null");
			this.key = key;
			this.description = description;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			return this.key.equals(((OperationKey) obj).key);
		}

		@Override
		public int hashCode() {
			return this.key.hashCode();
		}

		@Override
		public String toString() {
			return this.description.get();
		}

	}

	/**
	 * Information about an {@link Endpoint @Endpoint} bean.
	 */
	private static class EndpointBean {

		private final String beanName;

		private final Class<?> beanType;

		private final Supplier<Object> beanSupplier;

		private final EndpointId id;

		private boolean enabledByDefault;

		private final Class<?> filter;

		private Set<ExtensionBean> extensions = new LinkedHashSet<>();

		EndpointBean(Environment environment, String beanName, Class<?> beanType, Supplier<Object> beanSupplier) {
			MergedAnnotation<Endpoint> annotation = MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY)
					.get(Endpoint.class);
			String id = annotation.getString("id");
			Assert.state(StringUtils.hasText(id),
					() -> "No @Endpoint id attribute specified for " + beanType.getName());
			this.beanName = beanName;
			this.beanType = beanType;
			this.beanSupplier = beanSupplier;
			this.id = EndpointId.of(environment, id);
			this.enabledByDefault = annotation.getBoolean("enableByDefault");
			this.filter = getFilter(beanType);
		}

		void addExtension(ExtensionBean extensionBean) {
			this.extensions.add(extensionBean);
		}

		Set<ExtensionBean> getExtensions() {
			return this.extensions;
		}

		private Class<?> getFilter(Class<?> type) {
			return MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY).get(FilteredEndpoint.class)
					.getValue(MergedAnnotation.VALUE, Class.class).orElse(null);
		}

		String getBeanName() {
			return this.beanName;
		}

		Class<?> getBeanType() {
			return this.beanType;
		}

		Object getBean() {
			return this.beanSupplier.get();
		}

		EndpointId getId() {
			return this.id;
		}

		boolean isEnabledByDefault() {
			return this.enabledByDefault;
		}

		Class<?> getFilter() {
			return this.filter;
		}

	}

	/**
	 * Information about an {@link EndpointExtension @EndpointExtension} bean.
	 */
	private static class ExtensionBean {

		private final String beanName;

		private final Class<?> beanType;

		private final Supplier<Object> beanSupplier;

		private final EndpointId endpointId;

		private final Class<?> filter;

		ExtensionBean(Environment environment, String beanName, Class<?> beanType, Supplier<Object> beanSupplier) {
			this.beanName = beanName;
			this.beanType = beanType;
			this.beanSupplier = beanSupplier;
			MergedAnnotation<EndpointExtension> extensionAnnotation = MergedAnnotations
					.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(EndpointExtension.class);
			Class<?> endpointType = extensionAnnotation.getClass("endpoint");
			MergedAnnotation<Endpoint> endpointAnnotation = MergedAnnotations
					.from(endpointType, SearchStrategy.TYPE_HIERARCHY).get(Endpoint.class);
			Assert.state(endpointAnnotation.isPresent(),
					() -> "Extension " + endpointType.getName() + " does not specify an endpoint");
			this.endpointId = EndpointId.of(environment, endpointAnnotation.getString("id"));
			this.filter = extensionAnnotation.getClass("filter");
		}

		String getBeanName() {
			return this.beanName;
		}

		Class<?> getBeanType() {
			return this.beanType;
		}

		Object getBean() {
			return this.beanSupplier.get();
		}

		EndpointId getEndpointId() {
			return this.endpointId;
		}

		Class<?> getFilter() {
			return this.filter;
		}

	}

}
