package jadx.tests.integration.conditions;

public class TestConditions14Fixture {

	@SuppressWarnings({ "EqualsReplaceableByObjectsCall", "ConstantConditions" })
	public static class TestCls {

		public static boolean test(Object a, Object b) {
			boolean r = a == null ? b != null : !a.equals(b);
			if (r) {
				return false;
			}
			System.out.println("r=" + r);
			return true;
		}
	}
}
