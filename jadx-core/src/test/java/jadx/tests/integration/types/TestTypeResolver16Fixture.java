package jadx.tests.integration.types;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.emptyList;

public class TestTypeResolver16Fixture {

	@SuppressWarnings("unchecked")
	public static class TestCls {

		public final <T, K> List<T> test(List<? extends T> list,
				Set<? extends T> set, Function<? super T, ? extends K> function) {
			if (set != null) {
				List<? extends T> union = list != null ? union(list, set, function) : null;
				if (union != null) {
					list = union;
				}
			}
			return list != null ? (List<T>) list : emptyList();
		}

		public static <T, K> List<T> union(
				Collection<? extends T> collection,
				Iterable<? extends T> iterable,
				Function<? super T, ? extends K> function) {
			return null;
		}
	}
}
