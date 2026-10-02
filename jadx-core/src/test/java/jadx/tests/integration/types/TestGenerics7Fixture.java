package jadx.tests.integration.types;

import static org.assertj.core.api.Assertions.assertThat;

public class TestGenerics7Fixture {

	public static class TestCls<T> {
		private Object[] elements = new Object[1];

		@SuppressWarnings("unchecked")
		public final T test(int i) {
			Object[] arr = this.elements;
			T obj = (T) arr[i];
			arr[i] = null;
			if (obj == null) {
				throw new NullPointerException();
			}
			return obj;
		}

		public void check() {
			this.elements = new Object[] { 1, "" };
			assertThat(test(1)).isEqualTo("");
		}
	}
}
