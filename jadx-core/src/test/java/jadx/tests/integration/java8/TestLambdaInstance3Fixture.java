package jadx.tests.integration.java8;

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
