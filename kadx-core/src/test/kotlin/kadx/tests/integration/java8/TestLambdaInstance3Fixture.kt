package kadx.tests.integration.java8

object TestLambdaInstance3Fixture {
	class TestCls

	class Lazy<T> : java.util.function.Supplier<T> {
		override fun get(): T = throw UnsupportedOperationException()

		companion object {
			@JvmStatic
			fun <T> of(supplier: java.util.function.Supplier<out T>): Lazy<T>? = null
		}
	}

	interface Memoized

	const val JAVA_SOURCE = """package kadx.tests.integration.java8;

import java.util.function.Supplier;

@SuppressWarnings("DataFlowIssue")
public class TestLambdaInstance3Fixture {

	public interface TestCls<R> extends Supplier<R> {
		default TestCls<R> test() {
			return (TestCls<R> & Memoized) Lazy.of(this)::get;
		}
	}

	public static final class Lazy<T> implements Supplier<T> {
		public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
			return null;
		}

		@Override
		public T get() {
			return null;
		}
	}

	interface Memoized {
	}
}
"""
}
