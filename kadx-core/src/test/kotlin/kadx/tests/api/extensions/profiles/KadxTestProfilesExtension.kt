package kadx.tests.api.extensions.profiles

import kadx.tests.api.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils
import org.junit.platform.commons.util.AnnotationUtils.isAnnotated
import org.junit.platform.commons.util.Preconditions
import java.lang.reflect.Method
import java.util.EnumSet
import java.util.stream.Stream

/**
 * [TestWithProfiles] 的 JUnit 5 扩展：为每个 [TestProfile] 生成一次测试调用。
 */
class KadxTestProfilesExtension : TestTemplateInvocationContextProvider {

	override fun supportsTestTemplate(context: ExtensionContext): Boolean = isAnnotated(context.getTestMethod(), TestWithProfiles::class.java)

	override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
		Preconditions.condition(
			IntegrationTest::class.java.isAssignableFrom(context.getRequiredTestClass()),
			"@TestWithProfiles should be used only in IntegrationTest subclasses",
		)

		val testMethod: Method = context.getRequiredTestMethod()
		val testAnnAdded = AnnotationUtils.findAnnotation(testMethod, Test::class.java).isPresent
		Preconditions.condition(!testAnnAdded, "@Test annotation should be removed")

		val profilesAnn = AnnotationUtils.findAnnotation(testMethod, TestWithProfiles::class.java).get()
		val profilesSet = EnumSet.noneOf(TestProfile::class.java)
		profilesSet.addAll(profilesAnn.value.toList())
		if (profilesSet.contains(TestProfile.ALL)) {
			profilesSet.addAll(TestProfile.entries)
		}
		profilesSet.remove(TestProfile.ALL)
		return profilesSet.stream()
			.sorted()
			.map { RunWithProfile(it) }
	}

	private class RunWithProfile(private val testProfile: TestProfile) : TestTemplateInvocationContext {

		override fun getDisplayName(invocationIndex: Int): String = testProfile.getDescription()

		override fun getAdditionalExtensions(): List<Extension> = listOf(beforeTest())

		private fun beforeTest(): BeforeTestExecutionCallback = BeforeTestExecutionCallback { execContext ->
			testProfile.apply(execContext.getRequiredTestInstance() as IntegrationTest)
		}
	}
}
