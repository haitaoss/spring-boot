package cn.haitaoss.multidbdriver;

import cn.hutool.core.lang.JarClassLoader;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-06-01 14:06
 */
public class DBDriverContext implements DisposableBean {
    private static final String INIT_SQL = "select 'haitaoss' from dual";
    private static final String DB_PREFIX = "spring.datasource.";
    private static final String MULTI_DB_DRIVER = DB_PREFIX + "dynamic-db-driver";
    private Map<String, AnnotationConfigApplicationContext> driverContextMap = new HashMap<>(5);
    private Map<String, Map<String, String>> driverMap = new HashMap<>(5);
    private Function<String, JarClassLoader> jarClassLoaderFunction = fileName ->
            JarClassLoader.loadJar(Paths.get(System.getProperty("user.dir"), fileName).toFile());

    public DBDriverContext(Environment environment, ApplicationContext parent) {
        initDriverInfo(environment);
        initDriverIOC(parent);
    }

    private void initDriverInfo(Environment environment) {
        Binder.get(environment)
                .bind(MULTI_DB_DRIVER, Bindable.mapOf(String.class, String.class))
                .orElseGet(HashMap::new)
                .forEach((key, value) -> {
                    String[] split = key.split("\\.");
                    driverMap.computeIfAbsent(split[0], k -> new HashMap<>())
                            .put(split[1], value);
                });
    }

    public void initDriverIOC(ApplicationContext parent) {
        driverMap.forEach((key, value) -> {
            String driverPath = value.get("driver-lib");
            driverContextMap.put(key,
                    newMysqlContext(
                            parent,
                            jarClassLoaderFunction.apply(driverPath),
                            value));
        });
    }

    public Map<String, AnnotationConfigApplicationContext> getDriverContextMap() {
        return driverContextMap;
    }

    private AnnotationConfigApplicationContext newMysqlContext(ApplicationContext parent, JarClassLoader jarClassLoader, Map<String, String> map) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(jarClassLoader);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setParent(parent);
        context.register(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class);
        context.setEnvironment(new AbstractEnvironment() {
            @Override
            protected void customizePropertySources(MutablePropertySources propertySources) {
                HashMap<String, Object> tmpMap = new HashMap<>();
                map.forEach((key, value) ->
                        tmpMap.put(DB_PREFIX + key, value)
                );
                propertySources.addFirst(new MapPropertySource("database", tmpMap));
            }
        });
        context.refresh();

        // 提前加载 Driver
        context.getBean(JdbcTemplate.class).queryForMap(map.getOrDefault("init-sql", INIT_SQL));

        Thread.currentThread().setContextClassLoader(contextClassLoader);
        return context;
    }

    public static void main(String[] args) {
        System.setProperty("spring.config.additional-location", "classpath:config/dynamic-database-driver.yml");
        ConfigurableApplicationContext run = new SpringApplicationBuilder()
                .sources(Object.class)
                .web(WebApplicationType.NONE)
                .run(args);
        ConfigurableListableBeanFactory beanFactory = run.getBeanFactory();

        // 开启
        DBDriverContext dbDriverContext = beanFactory.createBean(DBDriverContext.class);
        Map<String, AnnotationConfigApplicationContext> driverContextMap = dbDriverContext.getDriverContextMap();
        System.out.println(driverContextMap.get("mysql5").getBean(JdbcTemplate.class).queryForMap(INIT_SQL));
        System.out.println("========================");
        System.out.println(driverContextMap.get("mysql8").getBean(JdbcTemplate.class).queryForMap(INIT_SQL));

        // 关闭
        beanFactory.destroyBean(dbDriverContext);
    }

    @Override
    public void destroy() throws Exception {
        driverContextMap.values().forEach(AnnotationConfigApplicationContext::close);
    }
}
