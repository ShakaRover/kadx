package jadx.tests.integration.types;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTypeResolver20Fixture {

	public static class TestCls {
		public interface Sequence<T> {
			Iterator<T> iterator();
		}

		public static <T extends Comparable<? super T>> T max(Sequence<? extends T> seq) {
			Iterator<? extends T> it = seq.iterator();
			if (!it.hasNext()) {
				return null;
			}
			T t = it.next();
			while (it.hasNext()) {
				T next = it.next();
				if (t.compareTo(next) < 0) {
					t = next;
				}
			}
			return t;
		}

		private static class ArraySeq<T> implements Sequence<T> {
			private final List<T> list;

			@SafeVarargs
			public ArraySeq(T... arr) {
				this.list = Arrays.asList(arr);
			}

			@Override
			public Iterator<T> iterator() {
				return list.iterator();
			}
		}

		public void check() {
			assertThat(max(new ArraySeq<>(2, 5, 3, 4))).isEqualTo(5);
		}
	}
}
