package cn.haitaoss.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.invoke.MissingParametersException;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvoker;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvokerAdvisor;
import org.springframework.boot.actuate.endpoint.invoke.OperationParameters;
import org.springframework.boot.actuate.endpoint.web.ExposableServletEndpoint;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.annotation.ExposableControllerEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-13 20:07
 *
 */
@Component
@Slf4j
public class EndpointConfig {
    @Bean
    public EndpointFilter<ExposableWebEndpoint> webEndpointEndpointFilter() {
        return new EndpointFilter<ExposableWebEndpoint>() {
            @Override
            public boolean match(ExposableWebEndpoint endpoint) {
                log.info("可以过滤endpoint是否应该暴露: webEndpointEndpointFilter ...");
                return true;
            }
        };
    }

    @Bean
    public OperationInvokerAdvisor operationInvokerAdvisor() {
        return new OperationInvokerAdvisor() {
            @Override
            public OperationInvoker apply(EndpointId endpointId, OperationType operationType,
                                          OperationParameters parameters, OperationInvoker invoker) {
                log.info("可以对 invoker 进行代理等等操作");
                return new OperationInvoker() {

                    @Override
                    public Object invoke(InvocationContext context) throws MissingParametersException {
                        log.info("before...");
                        Object invoke = invoker.invoke(context);
                        log.info("after...");
                        return invoke;
                    }
                };

            }
        };
    }

    @Bean
    public EndpointFilter<ExposableControllerEndpoint> controllerEndpointEndpointFilter() {
        return new EndpointFilter<ExposableControllerEndpoint>() {
            @Override
            public boolean match(ExposableControllerEndpoint endpoint) {
                log.info("可以过滤endpoint是否应该暴露: controllerEndpointEndpointFilter ...");
                return true;
            }
        };
    }

    @Bean
    public EndpointFilter<ExposableServletEndpoint> servletEndpointEndpointFilter() {
        return new EndpointFilter<ExposableServletEndpoint>() {
            @Override
            public boolean match(ExposableServletEndpoint endpoint) {
                log.info("可以过滤endpoint是否应该暴露: servletEndpointEndpointFilter ...");
                return true;
            }
        };
    }

    /**
     * http://localhost:8080/actuator/health
     * {@link HealthEndpointConfiguration#healthContributorRegistry(ApplicationContext, HealthEndpointGroups, Map, Map)}
     * @return
     */
    @Bean
    HealthIndicator haitaoHealthIndicator() {
        return () -> Health.up().withDetail("counter", 42).build();
    }
}
