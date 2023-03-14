package cn.haitaoss.actuator;

import org.springframework.boot.actuate.endpoint.web.EndpointServlet;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-13 16:03
 *
 */
@ServletEndpoint(id = "myServletendpoint")
@Component
public class MyServletEndpoint implements Supplier<EndpointServlet> {
    /**
     * http://localhost:8080/actuator/myServletendpoint
     * @return
     */
    @Override
    public EndpointServlet get() {
        return new EndpointServlet(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                System.out.println("MyServletEndpoint...");
                resp.getWriter().println("ok...");
            }
        });
    }
}