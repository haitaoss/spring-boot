package cn.haitaoss.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class Main {
	public static void main(String[] args) {
		/**
		 * {@link SpringBootTest}
		 */
	}

	/**
	 * 1. 声明了 `@ExtendWith(SpringExtension.class)`
	 * 2. 所以当执行 @Test 方法是会回调 {@link SpringExtension#postProcessTestInstance(Object, ExtensionContext)}
	 */
	/**
	 * 加工 TestInstance
	 * {@link SpringExtension#postProcessTestInstance}
	 *
	 * 1. new TestContextManager(TestContextManager.class)
	 * 	{@link TestContextManager#TestContextManager(Class)}
	 * 	TestContextManager testContextManager = new TestContextManager(TestContextManager.class)
	 *
	 *	DefaultBootstrapContext
	 *  从 testClass 上找到 @BootstrapWith 注解，然后实例化 @BootstrapWith.value() 得到 TestContextBootstrapper
	 *
	 * 2. 使用 TestContextManager 加工 TestInstance
	 *	testContextManager.prepareTestInstance(testInstance);
	 */
/*
 临时记录，懒得整理了
13 mock 的流程
    TestContextBootstrapper
org.springframework.test.context.junit.jupiter.SpringExtension#postProcessTestInstance
org.springframework.test.context.web.ServletTestExecutionListener#prepareTestInstance


  org.springframework.test.context.junit.jupiter.SpringExtension#postProcessTestInstance
  org.springframework.boot.test.context.SpringBootTestContextBootstrapper#buildTestContext
  org.springframework.boot.test.mock.mockito.MockitoContextCustomizerFactory#createContextCustomizer
  org.springframework.boot.test.mock.mockito.MockitoContextCustomizer#customizeContext
  SpyPostProcessor
  MockitoPostProcessor

spring-boot-project/spring-boot-test/src/main/resources/META-INF/spring.factories

1. org.springframework.test.context.junit.jupiter.SpringExtension#postProcessTestInstance
2. org.springframework.test.context.TestContextManager
3. org.springframework.test.context.TestContextManager#TestContextManager(java.lang.Class<?>)
    TestContextBootstrapper <-- @BootstrapWith
        org.springframework.boot.test.context.SpringBootTestContextBootstrapper#buildTestContext
        org.springframework.test.context.TestContextBootstrapper#buildTestContext

    List<TestExecutionListener> <-- @TestExecutionListeners

    result = {ArrayList@3594}  size = 15
 0 = {ServletTestExecutionListener@3593}
 1 = {DirtiesContextBeforeModesTestExecutionListener@3665}
 2 = {ApplicationEventsTestExecutionListener@3666}
 3 = {MockitoTestExecutionListener@3667}
 4 = {SpringBootDependencyInjectionTestExecutionListener@3668}
 5 = {DirtiesContextTestExecutionListener@3669}
 6 = {TransactionalTestExecutionListener@3670}
 7 = {SqlScriptsTestExecutionListener@3671}
 8 = {EventPublishingTestExecutionListener@3672}
 9 = {RestDocsTestExecutionListener@3673}
 10 = {MockRestServiceServerResetTestExecutionListener@3674}
 11 = {MockMvcPrintOnlyOnFailureTestExecutionListener@3675}
 12 = {WebDriverTestExecutionListener@3676}
 13 = {MockWebServiceServerTestExecutionListener@3677}
 14 = {ResetMocksTestExecutionListener@3678}
5. org.springframework.test.context.TestContextManager#prepareTestInstance

    org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener#prepareTestInstance xx

    org.springframework.test.context.web.ServletTestExecutionListener#prepareTestInstance
    org.springframework.boot.test.context.SpringBootContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration)
    org.springframework.boot.test.context.SpringBootContextLoader.ContextCustomizerAdapter#ContextCustomizerAdapter

*/
}
