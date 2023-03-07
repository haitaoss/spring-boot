package cn.haitaoss.applicationlistener;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-07 08:40
 *
 */
public class MyApplicationListener implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("cn.haitaoss.MyApplicationListener.onApplicationEvent...");
    }
}
