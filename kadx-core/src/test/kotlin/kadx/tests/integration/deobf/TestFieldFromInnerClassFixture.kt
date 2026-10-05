package kadx.tests.integration.deobf

object TestFieldFromInnerClassFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.deobf;

import java.util.List;
import java.util.Queue;

public class TestFieldFromInnerClassFixture {

	public static class TestCls<T> {
		TestCls<T>.I f;

		public class I {
			Queue<T> a;

			Queue<TestCls<T>.I> b;

			public class X {
				List<TestCls<T>.I.X> c;
			}
		}
	}
}
"""
}
