package jadx.tests.integration.generics

object TestGenericFieldsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.generics;

public class TestGenericFieldsFixture {

	public static class TestCls {

		public static class Summary {
			Value<Amount> price;
		}

		public static class Value<T> {
			T value;
		}

		public static class Amount {
			String cur;
			int val;
		}

		public String test(Summary summary) {
			Amount amount = summary.price.value;
			return amount.val + " " + amount.cur;
		}
	}
}
"""
}
