/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.*;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @author Chris Bono
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 * @since 1.0.0
 */
public class SpringApplication {

    /**
     * Default banner location.
     */
    public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

    /**
     * Banner location property key.
     */
    public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

    private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

    private static final Log logger = LogFactory.getLog(SpringApplication.class);

    static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();

    private Set<Class<?>> primarySources;

    private Set<String> sources = new LinkedHashSet<>();

    private Class<?> mainApplicationClass;

    private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

    private boolean logStartupInfo = true;

    private boolean addCommandLineProperties = true;

    private boolean addConversionService = true;

    private Banner banner;

    private ResourceLoader resourceLoader;

    private BeanNameGenerator beanNameGenerator;

    private ConfigurableEnvironment environment;

    private WebApplicationType webApplicationType;

    private boolean headless = true;

    private boolean registerShutdownHook = true;

    private List<ApplicationContextInitializer<?>> initializers;

    private List<ApplicationListener<?>> listeners;

    private Map<String, Object> defaultProperties;

    private List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;

    private Set<String> additionalProfiles = Collections.emptySet();

    private boolean allowBeanDefinitionOverriding;

    private boolean allowCircularReferences;

    private boolean isCustomEnvironment = false;

    private boolean lazyInitialization = false;

    private String environmentPrefix;

    private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

    private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

    /**
     * Create a new {@link SpringApplication} instance. The application context will load
     * beans from the specified primary sources (see {@link SpringApplication class-level}
     * documentation for details). The instance can be customized before calling
     * {@link #run(String...)}.
     *
     * @param primarySources the primary bean sources
     * @see #run(Class, String[])
     * @see #SpringApplication(ResourceLoader, Class...)
     * @see #setSources(Set)
     */
    public SpringApplication(Class<?>... primarySources) {
        this(null, primarySources);
    }

    /**
     * Create a new {@link SpringApplication} instance. The application context will load
     * beans from the specified primary sources (see {@link SpringApplication class-level}
     * documentation for details). The instance can be customized before calling
     * {@link #run(String...)}.
     *
     * @param resourceLoader the resource loader to use
     * @param primarySources the primary bean sources
     * @see #run(Class, String[])
     * @see #setSources(Set)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
        this.resourceLoader = resourceLoader;
        Assert.notNull(primarySources, "PrimarySources must not be null");
        // 记录主类
        this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
        /**
         * 推断出 webApplicationType，其实就是根据classpath中是否存在某些类进行判断。
         * 会根据这个类型，决定创建出什么类型的ApplicationContext
         *
         * {@link SpringApplication#run(String...)}
         * */
        this.webApplicationType = WebApplicationType.deduceFromClasspath();
        // 读取 META-INF/spring.factories 中key为 BootstrapRegistryInitializer.class.getName() 的信息，然后反射实例化
        /**
         * 这是用来在 run 环节对 BootstrapRegistry 进行初始化的
         *    {@link SpringApplication#createBootstrapContext()}
         * */
        this.bootstrapRegistryInitializers = new ArrayList<>(
                getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
        // 这是用来配置IOC容器的，配置之后才会refresh
        setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
        /**
         * 这是用来在 SpringBoot 启动环节 接收事件的，并且会在构造IOC容器时，将这些 ApplicationListener 也扩展给 IOC容器
         * {@link SpringApplication#prepareContext(DefaultBootstrapContext, ConfigurableApplicationContext, ConfigurableEnvironment, SpringApplicationRunListeners, ApplicationArguments, Banner)}
         * {@link EventPublishingRunListener#contextLoaded(ConfigurableApplicationContext)}
         * */
        setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
        // 记录启动类
        this.mainApplicationClass = deduceMainApplicationClass();
    }

    private Class<?> deduceMainApplicationClass() {
        try {
            StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
            for (StackTraceElement stackTraceElement : stackTrace) {
                if ("main".equals(stackTraceElement.getMethodName())) {
                    return Class.forName(stackTraceElement.getClassName());
                }
            }
        } catch (ClassNotFoundException ex) {
            // Swallow and continue
        }
        return null;
    }

    /**
     * Run the Spring application, creating and refreshing a new
     * {@link ApplicationContext}.
     *
     * @param args the application arguments (usually passed from a Java main method)
     * @return a running {@link ApplicationContext}
     */
    public ConfigurableApplicationContext run(String... args) {
        long startTime = System.nanoTime();
        /**
         * 构造 DefaultBootstrapContext，会使用 BootstrapRegistryInitializer 对其初始化
         *
         * 是在构造器收集好有哪些 BootstrapRegistryInitializer
         *      {@link SpringApplication#SpringApplication(ResourceLoader, Class[])}
         *
         * DefaultBootstrapContext 的生命周期范围是 IOC容器refresh之前
         * */
        DefaultBootstrapContext bootstrapContext = createBootstrapContext();
        ConfigurableApplicationContext context = null;
        // 设置一个系统属性
        configureHeadlessProperty();
        /**
         * SpringApplicationRunListeners 是 SpringApplicationRunListener 的注册器，具体干活的是 SpringApplicationRunListener
         * 而具体有哪些 SpringApplicationRunListener 是读取 META-INF/spring.factories 中key为 SpringApplicationRunListener.class.getName() 的信息
         *
         * tips：这就是观察者模式
         * */
        SpringApplicationRunListeners listeners = getRunListeners(args);
        // 回调 SpringApplicationRunListener#starting 方法，可以在这个方法装饰 bootstrapContext
        listeners.starting(bootstrapContext, this.mainApplicationClass);
        try {
            // 装饰一下 args
            ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
            /**
             * 构造出 ConfigurableEnvironment
             *
             * 1. 可以使用 ApplicationContextFactory 来生成 ConfigurableEnvironment
             * 2. 回调 SpringApplicationRunListener#environmentPrepared 配置 ConfigurableEnvironment
             * 3. 修改属性的访问顺序为： 命令行参数 -> 系统属性 -> 环境变量 ... -> 默认属性(默认是空的)
             * */
            ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
            // 设置一个名叫 spring.beaninfo.ignore 的系统属性
            configureIgnoreBeanInfo(environment);
            /**
             * 构造出 Banner 并打印出banner的内容
             * 会尝试从 environment 获取属性指定的文件
             *  spring.banner.image.location
             *  spring.banner.location
             * 或者是 banner.[jpg | gif | png | txt]对应的文件
             * 作为要输出的内容
             * */
            Banner printedBanner = printBanner(environment);

            /**
             * 会根据环境推断出应该创建什么类型的IOC容器：
             *  - AnnotationConfigApplicationContext
             *  - AnnotationConfigServletWebServerApplicationContext
             *  - ApplicationReactiveWebEnvironment
             *
             * 会回调 {@link ApplicationContextFactory#create(WebApplicationType)} 方法构造出 context
             * */
            context = createApplicationContext();
            context.setApplicationStartup(this.applicationStartup);

            /**
             * 配置 IOC 容器：
             *  1. 给IOC容器设置上 environment
             *  2. 给IOC容器设置上 ResourceLoader、ConversionService
             *  3. 回调 ApplicationContextInitializer、SpringApplicationRunListener、bootstrapContext 方法
             *      3.1 初始化IOC容器    ApplicationContextInitializer#initialize
             *      3.2 配置IOC容器     SpringApplicationRunListener#contextPrepared
             *      3.3 完成DefaultBootstrapContext的生命周期      DefaultBootstrapContext#close
             *      3.4 IOC容器配置好了   SpringApplicationRunListener#contextLoaded
             *
             *  4. 添加单例bean：springApplicationArguments、springBootBanner
             *  5. 设置两个属性 allowCircularReferences、allowBeanDefinitionOverriding
             *  6. 添加两个BeanFactoryPostProcessor：
             *          LazyInitializationBeanFactoryPostProcessor：这个是用来判断是否需要给补全信息的 `beanDefinition.setLazyInit(true);`
             *          PropertySourceOrderingBeanFactoryPostProcessor：这个是用来将名叫 defaultProperties 的 PropertySource 移到 最后面的，也就是属性访问的优先级最低
             *  7. 将启动类添加到IOC容器中，说白了就是注册配置类
             * */
            prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);

            // 刷新IOC容器
            refreshContext(context);

            // 预留的模板方法
            afterRefresh(context, applicationArguments);

            Duration timeTakenToStartup = Duration.ofNanos(System.nanoTime() - startTime);
            if (this.logStartupInfo) {
                new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), timeTakenToStartup);
            }
            /**
             * 回调 SpringApplicationRunListener#started 方法（IOC容器启动耗时）
             * {@link SpringApplicationRunListener#started(ConfigurableApplicationContext, Duration)}
             * */
            listeners.started(context, timeTakenToStartup);
            // 回调 容器中类型是 ApplicationRunner、CommandLineRunner 的bean的方法
            callRunners(context, applicationArguments);
        } catch (Throwable ex) {
            /**
             * 处理异常情况:
             * 1. 使用 context 发布 ExitCodeEvent 事件
             * 2. 回调 SpringApplicationRunListener#failed 方法
             * */
            handleRunFailure(context, ex, listeners);
            throw new IllegalStateException(ex);
        }
        try {
            // 启动耗时
            Duration timeTakenToReady = Duration.ofNanos(System.nanoTime() - startTime);
            /**
             * 回调 SpringApplicationRunListener#ready 方法（SpringBoot启动耗时）
             * {@link SpringApplicationRunListener#ready(ConfigurableApplicationContext, Duration)}
             * */
            listeners.ready(context, timeTakenToReady);
        } catch (Throwable ex) {
            handleRunFailure(context, ex, null);
            throw new IllegalStateException(ex);
        }
        // 返回IOC容器
        return context;
    }

    private DefaultBootstrapContext createBootstrapContext() {
        DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
        this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
        return bootstrapContext;
    }

    private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
                                                       DefaultBootstrapContext bootstrapContext,
                                                       ApplicationArguments applicationArguments) {
        /**
         * 创建出 ConfigurableEnvironment
         *
         * 如果没有默认的，就会读取 META-INF/spring.factories 中key为 `ApplicationContextFactory.class.getMame()` 的实例
         * 回调 {@link ApplicationContextFactory#create(WebApplicationType)} 方法生成 ConfigurableEnvironment
         *
         * */
        // Create and configure the environment
        ConfigurableEnvironment environment = getOrCreateEnvironment();
        /**
         * 配置Environment，其实就是扩展Environment能访问的属性信息
         * 		访问顺序：命令行参数 -> ... -> 默认属性
         *
         * 注：默认属性可以这样子进行配置 {@link org.springframework.boot.SpringApplicationTests#defaultCommandLineArgs()}
         * */
        configureEnvironment(environment, applicationArguments.getSourceArgs());
        // 将 ConfigurationPropertySourcesPropertySource 放到第一个位置
        ConfigurationPropertySources.attach(environment);
        /**
         * 回调 {@link SpringApplicationRunListener#environmentPrepared(ConfigurableBootstrapContext, ConfigurableEnvironment)}
         * 对 environment 进行配置
         * */
        listeners.environmentPrepared(bootstrapContext, environment);
        // 移动 defaultProperties 到最后，即优先级最低
        DefaultPropertiesPropertySource.moveToEnd(environment);
        Assert.state(
                !environment.containsProperty("spring.main.environment-prefix"),
                "Environment prefix cannot be set via properties."
        );
        // TODOHAITAO: 2023/2/14 不知道是个啥东东
        bindToSpringApplication(environment);
        if (!this.isCustomEnvironment) {
            EnvironmentConverter environmentConverter = new EnvironmentConverter(getClassLoader());
            // 转换成 ConfigurableEnvironment 类型的
            environment = environmentConverter.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
        }
        // 同上
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private Class<? extends ConfigurableEnvironment> deduceEnvironmentClass() {
        Class<? extends ConfigurableEnvironment> environmentType = this.applicationContextFactory.getEnvironmentType(
                this.webApplicationType);
        if (environmentType == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
            environmentType = ApplicationContextFactory.DEFAULT.getEnvironmentType(this.webApplicationType);
        }
        if (environmentType == null) {
            return ApplicationEnvironment.class;
        }
        return environmentType;
    }

    private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
                                ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
                                ApplicationArguments applicationArguments, Banner printedBanner) {
        // 给IOC容器设置上 environment
        context.setEnvironment(environment);
        // 给IOC容器设置上 ResourceLoader、ConversionService
        postProcessApplicationContext(context);

        /**
         * 回调 {@link ApplicationContextInitializer#initialize(ConfigurableApplicationContext)} 加工IOC容器
         * */
        applyInitializers(context);
        /**
         * 回调 {@link SpringApplicationRunListener#contextPrepared(ConfigurableApplicationContext)}
         * */
        listeners.contextPrepared(context);
        // bootstrapContext 的生命周期结束了
        bootstrapContext.close(context);
        if (this.logStartupInfo) {
            logStartupInfo(context.getParent() == null);
            logStartupProfileInfo(context);
        }
        // Add boot specific singleton beans
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        // 给注册单例bean
        beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
        if (printedBanner != null) {
            beanFactory.registerSingleton("springBootBanner", printedBanner);
        }
        // 设置属性
        if (beanFactory instanceof AbstractAutowireCapableBeanFactory) {
            ((AbstractAutowireCapableBeanFactory) beanFactory).setAllowCircularReferences(this.allowCircularReferences);
            if (beanFactory instanceof DefaultListableBeanFactory) {
                ((DefaultListableBeanFactory) beanFactory).setAllowBeanDefinitionOverriding(
                        this.allowBeanDefinitionOverriding);
            }
        }
        // 给IOC容器添加 BeanFactoryPostProcessor
        if (this.lazyInitialization) {
            // 这个是用来判断是否需要给补全信息的 `beanDefinition.setLazyInit(true);`
            context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
        }
        // 这个是用来将名叫 defaultProperties 的 PropertySource 移到 最后面的，也就是属性访问的优先级最低
        context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
        // Load the sources
        Set<Object> sources = getAllSources();
        Assert.notEmpty(sources, "Sources must not be empty");
        // 注册 sources 到IOC容器中
        load(context, sources.toArray(new Object[0]));
        /**
         * 回调
         * {@link SpringApplicationRunListener#contextLoaded(ConfigurableApplicationContext)}
         * {@link EventPublishingRunListener#contextLoaded(ConfigurableApplicationContext)}
         *
         * 1. 将 listeners 中的 ApplicationListener 扩展给 context
         * 2. 发布事件 ApplicationPreparedEvent
         * */
        listeners.contextLoaded(context);
    }

    private void refreshContext(ConfigurableApplicationContext context) {
        if (this.registerShutdownHook) {
            shutdownHook.registerApplicationContext(context);
        }
        refresh(context);
    }

    private void configureHeadlessProperty() {
        System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
                System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless))
        );
    }

    private SpringApplicationRunListeners getRunListeners(String[] args) {
        Class<?>[] types = new Class<?>[]{SpringApplication.class, String[].class};
        return new SpringApplicationRunListeners(logger,
                getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args),
                this.applicationStartup
        );
    }

    private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
        return getSpringFactoriesInstances(type, new Class<?>[]{});
    }

    private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
        ClassLoader classLoader = getClassLoader();

        // 读取 META-INF/spring.factories 中key为 type.getName() 的信息
        // Use names and ensure unique to protect against duplicates
        Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
        // 实例化
        List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
        // 排序
        AnnotationAwareOrderComparator.sort(instances);
        return instances;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
                                                       ClassLoader classLoader, Object[] args, Set<String> names) {
        List<T> instances = new ArrayList<>(names.size());
        for (String name : names) {
            try {
                Class<?> instanceClass = ClassUtils.forName(name, classLoader);
                Assert.isAssignable(type, instanceClass);
                Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
                T instance = (T) BeanUtils.instantiateClass(constructor, args);
                instances.add(instance);
            } catch (Throwable ex) {
                throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
            }
        }
        return instances;
    }

    private ConfigurableEnvironment getOrCreateEnvironment() {
        if (this.environment != null) {
            return this.environment;
        }
        ConfigurableEnvironment environment = this.applicationContextFactory.createEnvironment(this.webApplicationType);
        if (environment == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
            environment = ApplicationContextFactory.DEFAULT.createEnvironment(this.webApplicationType);
        }
        return (environment != null) ? environment : new ApplicationEnvironment();
    }

    /**
     * Template method delegating to
     * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
     * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
     * Override this method for complete control over Environment customization, or one of
     * the above for fine-grained control over property sources or profiles, respectively.
     *
     * @param environment this application's environment
     * @param args        arguments passed to the {@code run} method
     * @see #configureProfiles(ConfigurableEnvironment, String[])
     * @see #configurePropertySources(ConfigurableEnvironment, String[])
     */
    protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
        if (this.addConversionService) {
            environment.setConversionService(new ApplicationConversionService());
        }
        /**
         * 扩展 environment 访问的属性
         *
         * 访问顺序：命令行参数 -> ... -> 默认属性
         *
         * */
        configurePropertySources(environment, args);
        // 模板方法，空实现
        configureProfiles(environment, args);
    }

    /**
     * Add, remove or re-order any {@link PropertySource}s in this application's
     * environment.
     *
     * @param environment this application's environment
     * @param args        arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
        MutablePropertySources sources = environment.getPropertySources();
        if (!CollectionUtils.isEmpty(this.defaultProperties)) {
            // 将 defaultProperties 装饰成 propertySource 然后注册到 sources 中（放在最后）
            DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
        }
        // 将 args 构造成 SimpleCommandLinePropertySource 然后注册到 sources 中（放到前面）
        if (this.addCommandLineProperties && args.length > 0) {
            String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
            if (sources.contains(name)) {
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite.addPropertySource(
                        new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            } else {
                // 放到前面
                sources.addFirst(new SimpleCommandLinePropertySource(args));
            }
        }
    }

    /**
     * Configure which profiles are active (or active by default) for this application
     * environment. Additional profiles may be activated during configuration file
     * processing through the {@code spring.profiles.active} property.
     *
     * @param environment this application's environment
     * @param args        arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
    }

    private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
        if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
            Boolean ignore = environment.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME,
                    Boolean.class, Boolean.TRUE
            );
            System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
        }
    }

    /**
     * Bind the environment to the {@link SpringApplication}.
     *
     * @param environment the environment to bind
     */
    protected void bindToSpringApplication(ConfigurableEnvironment environment) {
        try {
            Binder.get(environment)
                    .bind("spring.main", Bindable.ofInstance(this));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot bind to SpringApplication", ex);
        }
    }

    private Banner printBanner(ConfigurableEnvironment environment) {
        if (this.bannerMode == Banner.Mode.OFF) {
            return null;
        }
        ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader : new DefaultResourceLoader(
                null);
        SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
        if (this.bannerMode == Mode.LOG) {
            return bannerPrinter.print(environment, this.mainApplicationClass, logger);
        }
        return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
    }

    /**
     * Strategy method used to create the {@link ApplicationContext}. By default this
     * method will respect any explicitly set application context class or factory before
     * falling back to a suitable default.
     *
     * @return the application context (not yet refreshed)
     * @see #setApplicationContextFactory(ApplicationContextFactory)
     */
    protected ConfigurableApplicationContext createApplicationContext() {
        return this.applicationContextFactory.create(this.webApplicationType);
    }

    /**
     * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
     * apply additional processing as required.
     *
     * @param context the application context
     */
    protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        if (this.beanNameGenerator != null) {
            context.getBeanFactory()
                    .registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, this.beanNameGenerator);
        }
        if (this.resourceLoader != null) {
            if (context instanceof GenericApplicationContext) {
                ((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
            }
            if (context instanceof DefaultResourceLoader) {
                ((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
            }
        }
        if (this.addConversionService) {
            context.getBeanFactory()
                    .setConversionService(context.getEnvironment()
                            .getConversionService());
        }
    }

    /**
     * Apply any {@link ApplicationContextInitializer}s to the context before it is
     * refreshed.
     *
     * @param context the configured ApplicationContext (not refreshed yet)
     * @see ConfigurableApplicationContext#refresh()
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void applyInitializers(ConfigurableApplicationContext context) {
        for (ApplicationContextInitializer initializer : getInitializers()) {
            Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
                    ApplicationContextInitializer.class
            );
            Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
            initializer.initialize(context);
        }
    }

    /**
     * Called to log startup information, subclasses may override to add additional
     * logging.
     *
     * @param isRoot true if this application is the root of a context hierarchy
     */
    protected void logStartupInfo(boolean isRoot) {
        if (isRoot) {
            new StartupInfoLogger(this.mainApplicationClass).logStarting(getApplicationLog());
        }
    }

    /**
     * Called to log active profile information.
     *
     * @param context the application context
     */
    protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
        Log log = getApplicationLog();
        if (log.isInfoEnabled()) {
            List<String> activeProfiles = quoteProfiles(context.getEnvironment()
                    .getActiveProfiles());
            if (ObjectUtils.isEmpty(activeProfiles)) {
                List<String> defaultProfiles = quoteProfiles(context.getEnvironment()
                        .getDefaultProfiles());
                String message = String.format("%s default %s: ", defaultProfiles.size(),
                        (defaultProfiles.size() <= 1) ? "profile" : "profiles"
                );
                log.info("No active profile set, falling back to " + message + StringUtils.collectionToDelimitedString(
                        defaultProfiles, ", "));
            } else {
                String message = (activeProfiles.size() == 1) ? "1 profile is active: " : activeProfiles.size() + " profiles are active: ";
                log.info("The following " + message + StringUtils.collectionToDelimitedString(activeProfiles, ", "));
            }
        }
    }

    private List<String> quoteProfiles(String[] profiles) {
        return Arrays.stream(profiles)
                .map((profile) -> "\"" + profile + "\"")
                .collect(Collectors.toList());
    }

    /**
     * Returns the {@link Log} for the application. By default will be deduced.
     *
     * @return the application log
     */
    protected Log getApplicationLog() {
        if (this.mainApplicationClass == null) {
            return logger;
        }
        return LogFactory.getLog(this.mainApplicationClass);
    }

    /**
     * Load beans into the application context.
     *
     * @param context the context to load beans into
     * @param sources the sources to load
     */
    protected void load(ApplicationContext context, Object[] sources) {
        if (logger.isDebugEnabled()) {
            logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
        }
        BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
        if (this.beanNameGenerator != null) {
            loader.setBeanNameGenerator(this.beanNameGenerator);
        }
        if (this.resourceLoader != null) {
            loader.setResourceLoader(this.resourceLoader);
        }
        if (this.environment != null) {
            loader.setEnvironment(this.environment);
        }
        loader.load();
    }

    /**
     * The ResourceLoader that will be used in the ApplicationContext.
     *
     * @return the resourceLoader the resource loader that will be used in the
     * ApplicationContext (or null if the default)
     */
    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    /**
     * Either the ClassLoader that will be used in the ApplicationContext (if
     * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set), or the context
     * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
     *
     * @return a ClassLoader (never null)
     */
    public ClassLoader getClassLoader() {
        if (this.resourceLoader != null) {
            return this.resourceLoader.getClassLoader();
        }
        return ClassUtils.getDefaultClassLoader();
    }

    /**
     * Get the bean definition registry.
     *
     * @param context the application context
     * @return the BeanDefinitionRegistry if it can be determined
     */
    private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
        if (context instanceof BeanDefinitionRegistry) {
            return (BeanDefinitionRegistry) context;
        }
        if (context instanceof AbstractApplicationContext) {
            return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
        }
        throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
    }

    /**
     * Factory method used to create the {@link BeanDefinitionLoader}.
     *
     * @param registry the bean definition registry
     * @param sources  the sources to load
     * @return the {@link BeanDefinitionLoader} that will be used to load beans
     */
    protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
        return new BeanDefinitionLoader(registry, sources);
    }

    /**
     * Refresh the underlying {@link ApplicationContext}.
     *
     * @param applicationContext the application context to refresh
     */
    protected void refresh(ConfigurableApplicationContext applicationContext) {
        applicationContext.refresh();
    }

    /**
     * Called after the context has been refreshed.
     *
     * @param context the application context
     * @param args    the application arguments
     */
    protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
    }

    private void callRunners(ApplicationContext context, ApplicationArguments args) {
        List<Object> runners = new ArrayList<>();
        runners.addAll(context.getBeansOfType(ApplicationRunner.class)
                .values());
        runners.addAll(context.getBeansOfType(CommandLineRunner.class)
                .values());
        // 排序，会按照 Ordered 或者 @Order 或者 @Priority 升序排序
        AnnotationAwareOrderComparator.sort(runners);
        // 回调方法
        for (Object runner : new LinkedHashSet<>(runners)) {
            if (runner instanceof ApplicationRunner) {
                callRunner((ApplicationRunner) runner, args);
            }
            if (runner instanceof CommandLineRunner) {
                callRunner((CommandLineRunner) runner, args);
            }
        }
    }

    private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
        try {
            (runner).run(args);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
        }
    }

    private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
        try {
            (runner).run(args.getSourceArgs());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
        }
    }

    private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
                                  SpringApplicationRunListeners listeners) {
        try {
            try {
                // 使用 context 发布 ExitCodeEvent 事件
                handleExitCode(context, exception);
                if (listeners != null) {
                    // 回调 SpringApplicationRunListener 的 failed 方法
                    listeners.failed(context, exception);
                }
            } finally {
                reportFailure(getExceptionReporters(context), exception);
                if (context != null) {
                    context.close();
                    shutdownHook.deregisterFailedApplicationContext(context);
                }
            }
        } catch (Exception ex) {
            logger.warn("Unable to close ApplicationContext", ex);
        }
        // 抛出异常
        ReflectionUtils.rethrowRuntimeException(exception);
    }

    private Collection<SpringBootExceptionReporter> getExceptionReporters(ConfigurableApplicationContext context) {
        try {
            return getSpringFactoriesInstances(SpringBootExceptionReporter.class,
                    new Class<?>[]{ConfigurableApplicationContext.class}, context
            );
        } catch (Throwable ex) {
            return Collections.emptyList();
        }
    }

    private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
        try {
            for (SpringBootExceptionReporter reporter : exceptionReporters) {
                if (reporter.reportException(failure)) {
                    registerLoggedException(failure);
                    return;
                }
            }
        } catch (Throwable ex) {
            // Continue with normal handling of the original failure
        }
        if (logger.isErrorEnabled()) {
            logger.error("Application run failed", failure);
            registerLoggedException(failure);
        }
    }

    /**
     * Register that the given exception has been logged. By default, if the running in
     * the main thread, this method will suppress additional printing of the stacktrace.
     *
     * @param exception the exception that was logged
     */
    protected void registerLoggedException(Throwable exception) {
        SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
        if (handler != null) {
            handler.registerLoggedException(exception);
        }
    }

    private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromException(context, exception);
        if (exitCode != 0) {
            if (context != null) {
                // 发布事件
                context.publishEvent(new ExitCodeEvent(context, exitCode));
            }
            SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
            if (handler != null) {
                // 记录下 code
                handler.registerExitCode(exitCode);
            }
        }
    }

    private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromMappedException(context, exception);
        if (exitCode == 0) {
            exitCode = getExitCodeFromExitCodeGeneratorException(exception);
        }
        return exitCode;
    }

    private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
        if (context == null || !context.isActive()) {
            return 0;
        }
        ExitCodeGenerators generators = new ExitCodeGenerators();
        Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class)
                .values();
        generators.addAll(exception, beans);
        return generators.getExitCode();
    }

    private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
        if (exception == null) {
            return 0;
        }
        if (exception instanceof ExitCodeGenerator) {
            return ((ExitCodeGenerator) exception).getExitCode();
        }
        return getExitCodeFromExitCodeGeneratorException(exception.getCause());
    }

    SpringBootExceptionHandler getSpringBootExceptionHandler() {
        if (isMainThread(Thread.currentThread())) {
            return SpringBootExceptionHandler.forCurrentThread();
        }
        return null;
    }

    private boolean isMainThread(Thread currentThread) {
        return ("main".equals(currentThread.getName()) || "restartedMain".equals(
                currentThread.getName())) && "main".equals(currentThread.getThreadGroup()
                .getName());
    }

    /**
     * Returns the main application class that has been deduced or explicitly configured.
     *
     * @return the main application class or {@code null}
     */
    public Class<?> getMainApplicationClass() {
        return this.mainApplicationClass;
    }

    /**
     * Set a specific main application class that will be used as a log source and to
     * obtain version information. By default the main application class will be deduced.
     * Can be set to {@code null} if there is no explicit application class.
     *
     * @param mainApplicationClass the mainApplicationClass to set or {@code null}
     */
    public void setMainApplicationClass(Class<?> mainApplicationClass) {
        this.mainApplicationClass = mainApplicationClass;
    }

    /**
     * Returns the type of web application that is being run.
     *
     * @return the type of web application
     * @since 2.0.0
     */
    public WebApplicationType getWebApplicationType() {
        return this.webApplicationType;
    }

    /**
     * Sets the type of web application to be run. If not explicitly set the type of web
     * application will be deduced based on the classpath.
     *
     * @param webApplicationType the web application type
     * @since 2.0.0
     */
    public void setWebApplicationType(WebApplicationType webApplicationType) {
        Assert.notNull(webApplicationType, "WebApplicationType must not be null");
        this.webApplicationType = webApplicationType;
    }

    /**
     * Sets if bean definition overriding, by registering a definition with the same name
     * as an existing definition, should be allowed. Defaults to {@code false}.
     *
     * @param allowBeanDefinitionOverriding if overriding is allowed
     * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
     * @since 2.1.0
     */
    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
    }

    /**
     * Sets whether to allow circular references between beans and automatically try to
     * resolve them. Defaults to {@code false}.
     *
     * @param allowCircularReferences if circular references are allowed
     * @see AbstractAutowireCapableBeanFactory#setAllowCircularReferences(boolean)
     * @since 2.6.0
     */
    public void setAllowCircularReferences(boolean allowCircularReferences) {
        this.allowCircularReferences = allowCircularReferences;
    }

    /**
     * Sets if beans should be initialized lazily. Defaults to {@code false}.
     *
     * @param lazyInitialization if initialization should be lazy
     * @see BeanDefinition#setLazyInit(boolean)
     * @since 2.2
     */
    public void setLazyInitialization(boolean lazyInitialization) {
        this.lazyInitialization = lazyInitialization;
    }

    /**
     * Sets if the application is headless and should not instantiate AWT. Defaults to
     * {@code true} to prevent java icons appearing.
     *
     * @param headless if the application is headless
     */
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    /**
     * Sets if the created {@link ApplicationContext} should have a shutdown hook
     * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
     * gracefully.
     *
     * @param registerShutdownHook if the shutdown hook should be registered
     * @see #getShutdownHandlers()
     */
    public void setRegisterShutdownHook(boolean registerShutdownHook) {
        this.registerShutdownHook = registerShutdownHook;
    }

    /**
     * Sets the {@link Banner} instance which will be used to print the banner when no
     * static banner file is provided.
     *
     * @param banner the Banner instance to use
     */
    public void setBanner(Banner banner) {
        this.banner = banner;
    }

    /**
     * Sets the mode used to display the banner when the application runs. Defaults to
     * {@code Banner.Mode.CONSOLE}.
     *
     * @param bannerMode the mode used to display the banner
     */
    public void setBannerMode(Banner.Mode bannerMode) {
        this.bannerMode = bannerMode;
    }

    /**
     * Sets if the application information should be logged when the application starts.
     * Defaults to {@code true}.
     *
     * @param logStartupInfo if startup info should be logged.
     */
    public void setLogStartupInfo(boolean logStartupInfo) {
        this.logStartupInfo = logStartupInfo;
    }

    /**
     * Sets if a {@link CommandLinePropertySource} should be added to the application
     * context in order to expose arguments. Defaults to {@code true}.
     *
     * @param addCommandLineProperties if command line arguments should be exposed
     */
    public void setAddCommandLineProperties(boolean addCommandLineProperties) {
        this.addCommandLineProperties = addCommandLineProperties;
    }

    /**
     * Sets if the {@link ApplicationConversionService} should be added to the application
     * context's {@link Environment}.
     *
     * @param addConversionService if the application conversion service should be added
     * @since 2.1.0
     */
    public void setAddConversionService(boolean addConversionService) {
        this.addConversionService = addConversionService;
    }

    /**
     * Adds {@link BootstrapRegistryInitializer} instances that can be used to initialize
     * the {@link BootstrapRegistry}.
     *
     * @param bootstrapRegistryInitializer the bootstrap registry initializer to add
     * @since 2.4.5
     */
    public void addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
        Assert.notNull(bootstrapRegistryInitializer, "BootstrapRegistryInitializer must not be null");
        this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
    }

    /**
     * Set default environment properties which will be used in addition to those in the
     * existing {@link Environment}.
     *
     * @param defaultProperties the additional properties to set
     */
    public void setDefaultProperties(Map<String, Object> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    /**
     * Convenient alternative to {@link #setDefaultProperties(Map)}.
     *
     * @param defaultProperties some {@link Properties}
     */
    public void setDefaultProperties(Properties defaultProperties) {
        this.defaultProperties = new HashMap<>();
        for (Object key : Collections.list(defaultProperties.propertyNames())) {
            this.defaultProperties.put((String) key, defaultProperties.get(key));
        }
    }

    /**
     * Set additional profile values to use (on top of those set in system or command line
     * properties).
     *
     * @param profiles the additional profiles to set
     */
    public void setAdditionalProfiles(String... profiles) {
        this.additionalProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
    }

    /**
     * Return an immutable set of any additional profiles in use.
     *
     * @return the additional profiles
     */
    public Set<String> getAdditionalProfiles() {
        return this.additionalProfiles;
    }

    /**
     * Sets the bean name generator that should be used when generating bean names.
     *
     * @param beanNameGenerator the bean name generator
     */
    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    /**
     * Sets the underlying environment that should be used with the created application
     * context.
     *
     * @param environment the environment
     */
    public void setEnvironment(ConfigurableEnvironment environment) {
        this.isCustomEnvironment = true;
        this.environment = environment;
    }

    /**
     * Add additional items to the primary sources that will be added to an
     * ApplicationContext when {@link #run(String...)} is called.
     * <p>
     * The sources here are added to those that were set in the constructor. Most users
     * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
     * calling this method.
     *
     * @param additionalPrimarySources the additional primary sources to add
     * @see #SpringApplication(Class...)
     * @see #getSources()
     * @see #setSources(Set)
     * @see #getAllSources()
     */
    public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
        this.primarySources.addAll(additionalPrimarySources);
    }

    /**
     * Returns a mutable set of the sources that will be added to an ApplicationContext
     * when {@link #run(String...)} is called.
     * <p>
     * Sources set here will be used in addition to any primary sources set in the
     * constructor.
     *
     * @return the application sources.
     * @see #SpringApplication(Class...)
     * @see #getAllSources()
     */
    public Set<String> getSources() {
        return this.sources;
    }

    /**
     * Set additional sources that will be used to create an ApplicationContext. A source
     * can be: a class name, package name, or an XML resource location.
     * <p>
     * Sources set here will be used in addition to any primary sources set in the
     * constructor.
     *
     * @param sources the application sources to set
     * @see #SpringApplication(Class...)
     * @see #getAllSources()
     */
    public void setSources(Set<String> sources) {
        Assert.notNull(sources, "Sources must not be null");
        this.sources = new LinkedHashSet<>(sources);
    }

    /**
     * Return an immutable set of all the sources that will be added to an
     * ApplicationContext when {@link #run(String...)} is called. This method combines any
     * primary sources specified in the constructor with any additional ones that have
     * been {@link #setSources(Set) explicitly set}.
     *
     * @return an immutable set of all sources
     */
    public Set<Object> getAllSources() {
        Set<Object> allSources = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(this.primarySources)) {
            allSources.addAll(this.primarySources);
        }
        if (!CollectionUtils.isEmpty(this.sources)) {
            allSources.addAll(this.sources);
        }
        return Collections.unmodifiableSet(allSources);
    }

    /**
     * Sets the {@link ResourceLoader} that should be used when loading resources.
     *
     * @param resourceLoader the resource loader
     */
    public void setResourceLoader(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }

    /**
     * Return a prefix that should be applied when obtaining configuration properties from
     * the system environment.
     *
     * @return the environment property prefix
     * @since 2.5.0
     */
    public String getEnvironmentPrefix() {
        return this.environmentPrefix;
    }

    /**
     * Set the prefix that should be applied when obtaining configuration properties from
     * the system environment.
     *
     * @param environmentPrefix the environment property prefix to set
     * @since 2.5.0
     */
    public void setEnvironmentPrefix(String environmentPrefix) {
        this.environmentPrefix = environmentPrefix;
    }

    /**
     * Sets the factory that will be called to create the application context. If not set,
     * defaults to a factory that will create
     * {@link AnnotationConfigServletWebServerApplicationContext} for servlet web
     * applications, {@link AnnotationConfigReactiveWebServerApplicationContext} for
     * reactive web applications, and {@link AnnotationConfigApplicationContext} for
     * non-web applications.
     *
     * @param applicationContextFactory the factory for the context
     * @since 2.4.0
     */
    public void setApplicationContextFactory(ApplicationContextFactory applicationContextFactory) {
        this.applicationContextFactory = (applicationContextFactory != null) ? applicationContextFactory : ApplicationContextFactory.DEFAULT;
    }

    /**
     * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
     * {@link ApplicationContext}.
     *
     * @param initializers the initializers to set
     */
    public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
        this.initializers = new ArrayList<>(initializers);
    }

    /**
     * Add {@link ApplicationContextInitializer}s to be applied to the Spring
     * {@link ApplicationContext}.
     *
     * @param initializers the initializers to add
     */
    public void addInitializers(ApplicationContextInitializer<?>... initializers) {
        this.initializers.addAll(Arrays.asList(initializers));
    }

    /**
     * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
     * will be applied to the Spring {@link ApplicationContext}.
     *
     * @return the initializers
     */
    public Set<ApplicationContextInitializer<?>> getInitializers() {
        return asUnmodifiableOrderedSet(this.initializers);
    }

    /**
     * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
     * and registered with the {@link ApplicationContext}.
     *
     * @param listeners the listeners to set
     */
    public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }

    /**
     * Add {@link ApplicationListener}s to be applied to the SpringApplication and
     * registered with the {@link ApplicationContext}.
     *
     * @param listeners the listeners to add
     */
    public void addListeners(ApplicationListener<?>... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
    }

    /**
     * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
     * applied to the SpringApplication and registered with the {@link ApplicationContext}
     * .
     *
     * @return the listeners
     */
    public Set<ApplicationListener<?>> getListeners() {
        return asUnmodifiableOrderedSet(this.listeners);
    }

    /**
     * Set the {@link ApplicationStartup} to use for collecting startup metrics.
     *
     * @param applicationStartup the application startup to use
     * @since 2.4.0
     */
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        this.applicationStartup = (applicationStartup != null) ? applicationStartup : ApplicationStartup.DEFAULT;
    }

    /**
     * Returns the {@link ApplicationStartup} used for collecting startup metrics.
     *
     * @return the application startup
     * @since 2.4.0
     */
    public ApplicationStartup getApplicationStartup() {
        return this.applicationStartup;
    }

    /**
     * Return a {@link SpringApplicationShutdownHandlers} instance that can be used to add
     * or remove handlers that perform actions before the JVM is shutdown.
     *
     * @return a {@link SpringApplicationShutdownHandlers} instance
     * @since 2.5.1
     */
    public static SpringApplicationShutdownHandlers getShutdownHandlers() {
        return shutdownHook.getHandlers();
    }

    /**
     * Static helper that can be used to run a {@link SpringApplication} from the
     * specified source using default settings.
     *
     * @param primarySource the primary source to load
     * @param args          the application arguments (usually passed from a Java main method)
     * @return the running {@link ApplicationContext}
     */
    public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
        return run(new Class<?>[]{primarySource}, args);
    }

    /**
     * Static helper that can be used to run a {@link SpringApplication} from the
     * specified sources using default settings and user supplied arguments.
     *
     * @param primarySources the primary sources to load
     * @param args           the application arguments (usually passed from a Java main method)
     * @return the running {@link ApplicationContext}
     */
    public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
        return new SpringApplication(primarySources).run(args);
    }

    /**
     * A basic main that can be used to launch an application. This method is useful when
     * application sources are defined through a {@literal --spring.main.sources} command
     * line argument.
     * <p>
     * Most developers will want to define their own main method and call the
     * {@link #run(Class, String...) run} method instead.
     *
     * @param args command line arguments
     * @throws Exception if the application cannot be started
     * @see SpringApplication#run(Class[], String[])
     * @see SpringApplication#run(Class, String...)
     */
    public static void main(String[] args) throws Exception {
        SpringApplication.run(new Class<?>[0], args);
    }

    /**
     * Static helper that can be used to exit a {@link SpringApplication} and obtain a
     * code indicating success (0) or otherwise. Does not throw exceptions but should
     * print stack traces of any encountered. Applies the specified
     * {@link ExitCodeGenerator ExitCodeGenerators} in addition to any Spring beans that
     * implement {@link ExitCodeGenerator}. When multiple generators are available, the
     * first non-zero exit code is used. Generators are ordered based on their
     * {@link Ordered} implementation and {@link Order @Order} annotation.
     *
     * @param context            the context to close if possible
     * @param exitCodeGenerators exit code generators
     * @return the outcome (0 if successful)
     */
    public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
        Assert.notNull(context, "Context must not be null");
        int exitCode = 0;
        try {
            try {
                ExitCodeGenerators generators = new ExitCodeGenerators();
                Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class)
                        .values();
                generators.addAll(exitCodeGenerators);
                generators.addAll(beans);
                exitCode = generators.getExitCode();
                if (exitCode != 0) {
                    context.publishEvent(new ExitCodeEvent(context, exitCode));
                }
            } finally {
                close(context);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            exitCode = (exitCode != 0) ? exitCode : 1;
        }
        return exitCode;
    }

    private static void close(ApplicationContext context) {
        if (context instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
            closable.close();
        }
    }

    private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
        List<E> list = new ArrayList<>(elements);
        list.sort(AnnotationAwareOrderComparator.INSTANCE);
        return new LinkedHashSet<>(list);
    }

    /**
     * {@link BeanFactoryPostProcessor} to re-order our property sources below any
     * {@code @PropertySource} items added by the {@link ConfigurationClassPostProcessor}.
     */
    private static class PropertySourceOrderingBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

        private final ConfigurableApplicationContext context;

        PropertySourceOrderingBeanFactoryPostProcessor(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            DefaultPropertiesPropertySource.moveToEnd(this.context.getEnvironment());
        }

    }

}
