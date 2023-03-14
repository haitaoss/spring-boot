package cn.haitaoss.actuator;

import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-13 16:05
 *
 */
@ControllerEndpoint(id = "myControllerEndpoint")
@Component
// @RestControllerEndpoint(id = "myControllerEndpoint")
public class MyControllerEndpoint {
    /**
     * http://localhost:8080/actuator/myControllerEndpoint/index
     * @return
     */
    @GetMapping("index")
    @ResponseBody
    public Object index() {
        System.out.println("MyControllerEndpoint.index...");
        return "ok....";
    }
}
