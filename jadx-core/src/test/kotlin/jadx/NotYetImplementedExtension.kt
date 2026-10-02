package jadx

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import java.lang.reflect.Method

/**
 * [NotYetImplemented] 的 JUnit5 扩展。
 *
 * 行为与原 Java 实现一致：
 * - 被标记的测试抛出的异常被吞掉（记录到 [knownFailedMethods]）；
 * - 被标记的测试若正常通过，则在收尾阶段抛出 [AssertionError]。
 */
class NotYetImplementedExtension :
	AfterTestExecutionCallback,
	TestExecutionExceptionHandler {

	private val knownFailedMethods = HashSet<Method>()

	override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
		if (!isNotYetImplemented(context)) {
			throw throwable
		}
		knownFailedMethods.add(context.getTestMethod().get())
	}

	override fun afterTestExecution(context: ExtensionContext) {
		val testMethod = context.getTestMethod().get()
		if (!knownFailedMethods.contains(testMethod) &&
			isNotYetImplemented(context) &&
			context.getExecutionException().isEmpty
		) {
			throw AssertionError(
				"Test " +
					context.getTestClass().get().getName() + '.' + testMethod.getName() +
					" is marked as @NotYetImplemented, but passes!",
			)
		}
	}

	private fun isNotYetImplemented(context: ExtensionContext): Boolean = context.getTestMethod().get().getAnnotation(NotYetImplemented::class.java) != null ||
		context.getTestClass().get().getAnnotation(NotYetImplemented::class.java) != null
}
