package cn.haitaoss.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.annotation.EndpointExtension;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-13 16:07
 * 扩展满足 MyEndpointFilter 的 endpoint 的方法
 */
@Component
@EndpointExtension(filter = MyEndpointFilter.class,
        endpoint = MyEndpoint.class)
@Slf4j
public class MyEndpointExtend {
    @ReadOperation
    public void extend1(@Selector String extend) {
        log.info("MyEndpointExtend.extend1...");
    }
}

class MyEndpointFilter implements EndpointFilter<ExposableWebEndpoint> {
    @Override
    public boolean match(ExposableWebEndpoint endpoint) {
        return endpoint.getEndpointId()
                .toString()
                .equals("myEndpoint");
    }
}