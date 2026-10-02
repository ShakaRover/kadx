package jadx.tests.integration.trycatch;

public class TestTryCatchInIf2Fixture {
	public static class TestCls {
		public void test(Class<?> cls) {
			Object obj = null;
			if (cls != null) {
				try {
					obj = cls.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					System.out.println("error");
				}
			}
			System.out.println("obj = " + obj);
		}
	}
}
