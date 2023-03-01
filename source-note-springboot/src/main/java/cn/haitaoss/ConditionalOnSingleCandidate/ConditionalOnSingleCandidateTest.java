package cn.haitaoss.ConditionalOnSingleCandidate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-16 15:47
 *
 */
public class ConditionalOnSingleCandidateTest {
    @Bean
    public A a() {
        return new A();
    }

    @Bean
    public A a2() {
        return new A();
    }

    @ConditionalOnSingleCandidate
    @Bean
    public A a3() {
        return new A();
    }
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConditionalOnSingleCandidateTest.class);
        String[] beanNamesForType = context.getBeanNamesForType(A.class);
        System.out.println("beanNamesForType = " + Arrays.toString(beanNamesForType));
    }
}
class A {}