package kadx.tests.integration.inner

object TestAnonymousClass5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestAnonymousClass5Fixture {

	public static class TestCls {
		private final Map<String, TestCls> map = new HashMap<>();
		private int a;

		public Iterable<TestCls> test(String name) {
			final TestCls cls = map.get(name);
			if (cls == null) {
				return null;
			}
			final int listSize = cls.size();
			final Iterator<TestCls> iterator = new Iterator<TestCls>() {
				int counter = 0;

				@Override
				public TestCls next() {
					cls.a++;
					counter++;
					return cls;
				}

				@Override
				public boolean hasNext() {
					return counter < listSize;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
			return new Iterable<TestCls>() {
				@Override
				public Iterator<TestCls> iterator() {
					return iterator;
				}
			};
		}

		private int size() {
			return 7;
		}

		public void check() {
			TestCls v = new TestCls();
			v.a = 3;
			map.put("a", v);
			Iterable<TestCls> it = test("a");
			TestCls next = it.iterator().next();
			assertThat(next).isSameAs(v);
			assertThat(next.a).isEqualTo(4);
		}
	}
}
"""
}
