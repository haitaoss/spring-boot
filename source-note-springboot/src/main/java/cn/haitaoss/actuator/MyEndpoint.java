package cn.haitaoss.actuator;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.servlet.WebMvcEndpointManagementContextConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.AbstractWebMvcEndpointHandlerMapping;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-13 16:07
 *
 */
@Endpoint(id = "myEndpoint")
@Component
public class MyEndpoint {
    /**
     * @Selector 指定访问的路径
     *
     * 路径参数是这里处理的
     *      {@link RequestPredicateFactory#getRequestPredicate(String, DiscoveredOperationMethod)}
     *
     * 如何注册 Path 和 method 的看这里
     *      {@link WebMvcEndpointManagementContextConfiguration#webEndpointServletHandlerMapping(WebEndpointsSupplier, ServletEndpointsSupplier, ControllerEndpointsSupplier, EndpointMediaTypes, CorsEndpointProperties, WebEndpointProperties, Environment)}
     *      {@link AbstractWebMvcEndpointHandlerMapping#initHandlerMethods()}
     *
     *
     * @ReadOperation、@WriteOperation、@DeleteOperation 可接收的处理方法
     * {@link AbstractWebMvcEndpointHandlerMapping#registerMapping(ExposableWebEndpoint, WebOperationRequestPredicate, WebOperation, String)}
     *
     * 方法的执行看这里，就能知道有啥参数，能返回啥了
     *      {@link AbstractWebMvcEndpointHandlerMapping.ServletWebOperationAdapter#handle(HttpServletRequest, Map)}
     * */

    /**
     * http://localhost:8080/actuator/myEndpoint
     * */
    @ReadOperation
    public WebEndpointResponse myEndpoint() {
        System.out.println("myEndpoint...");
        return new WebEndpointResponse("ok...");
    }


    /**
     * http://localhost:8080/actuator/myEndpoint/{read}?name=haitao
     * @param read
     * @param name
     * @return
     */
    @ReadOperation
    public WebEndpointResponse readOperation(@Selector String read, String name) {
        System.out.println(String.format("readOperation...read is %s...name is %s", read, name));
        return new WebEndpointResponse("ok...");
    }

    /**
     * http://localhost:8080/actuator/myEndpoint/{read}/xx
     * @param read
     * @param all
     */
    @ReadOperation
    public void readOperation2(@Selector String read, @Selector(match = Selector.Match.ALL_REMAINING) String all) {
        System.out.println("readOperation2...");
    }

    /**
     * POST http://localhost:8080/actuator/myEndpoint
     */
    @WriteOperation
    public void write() {
        System.out.println("write...");
    }

    /**
     * DELETE http://localhost:8080/actuator/myEndpoint
     */
    @DeleteOperation
    public void delete() {

    }
}
