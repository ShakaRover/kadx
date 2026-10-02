package jadx.tests.integration.others;

public class TestStringConcatJava11Fixture {

	public static class TestCls {
		public String test(final String s) {
			return s + "test";
		}

		//@formatter:off
		/* Dynamic call looks like this:
		public String test(final String s) {
			return java.lang.invoke.StringConcatFactory.makeConcatWithConstants(
					java.lang.invoke.MethodHandles.lookup(),
					"makeConcatWithConstants",
					java.lang.invoke.MethodType.fromMethodDescriptorString("(Ljava/lang/String;)Ljava/lang/String;", this.getClass().getClassLoader()),
					"\u0001test"
			).dynamicInvoker().invoke(s);
		}
		*/
		//@formatter:on

		public String test2(final String s) {
			return s + "test" + s + 7;
		}
	}
}
