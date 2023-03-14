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

package org.springframework.boot.actuate.autoconfigure.endpoint;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.EndpointConverter;
import org.springframework.boot.actuate.endpoint.invoke.ParameterValueMapper;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.invoke.reflect.ReflectiveOperationInvoker;
import org.springframework.boot.actuate.endpoint.invoker.cache.CachingOperationInvokerAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link Endpoint @Endpoint}
 * support.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Chao Chang
 * @since 2.0.0
 */
@AutoConfiguration
public class EndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ParameterValueMapper endpointOperationParameterMapper(
            @EndpointConverter ObjectProvider<Converter<?, ?>> converters,
            @EndpointConverter ObjectProvider<GenericConverter> genericConverters) {
        ConversionService conversionService = createConversionService(
                converters.orderedStream()
                        .collect(Collectors.toList()), genericConverters.orderedStream()
                        .collect(Collectors.toList()));
        /**
         * 用来转换参数值的，在反射调用 @ReadOperation、@WriteOperation、@DeleteOperation 标注的方法的构造方法参数时
         * 会使用这个来映射参数的值，生成参数列表
         *
         * {@link ReflectiveOperationInvoker#invoke(InvocationContext)}
         * */
        return new ConversionServiceParameterValueMapper(conversionService);
    }

    private ConversionService createConversionService(List<Converter<?, ?>> converters,
                                                      List<GenericConverter> genericConverters) {
        if (genericConverters.isEmpty() && converters.isEmpty()) {
            return ApplicationConversionService.getSharedInstance();
        }
        ApplicationConversionService conversionService = new ApplicationConversionService();
        converters.forEach(conversionService::addConverter);
        genericConverters.forEach(conversionService::addConverter);
        return conversionService;
    }

    @Bean
    @ConditionalOnMissingBean
    public CachingOperationInvokerAdvisor endpointCachingOperationInvokerAdvisor(Environment environment) {
        /**
         * @ReadOperation、@WriteOperation、@DeleteOperation 的方法执行是 {@link ReflectiveOperationInvoker#invoke(InvocationContext)}
         * CachingOperationInvokerAdvisor 是用来对 ReflectiveOperationInvoker 进行装饰的，目的就是拦截方法的执行
         * 存在缓存就返回缓存结果。
         *
         * 注：可以通过 management.endpoint.`endpointId.toLowerCaseString()`.cache.time-to-live 设置缓存的时间
         * */
        return new CachingOperationInvokerAdvisor(new EndpointIdTimeToLivePropertyFunction(environment));
    }

}
