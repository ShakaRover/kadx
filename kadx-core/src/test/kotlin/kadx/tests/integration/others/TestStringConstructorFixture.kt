package kadx.tests.integration.others

object TestStringConstructorFixture {
	class TestCls
	class TestCls2
	class TestCls3
	class TestCls4
	class TestCls5
	class TestClsNegative
	class TestClsNegative2

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import java.nio.charset.StandardCharsets;

public class TestStringConstructorFixture {

	public static class TestCls {
		public String tag = new String(new byte[] { 'a', 'b', 'c' });
	}

	public static class TestCls2 {
		public String tag = new String(new byte[] { 'a', 'b', 'c' }, StandardCharsets.UTF_8);
	}

	public static class TestCls3 {
		public String tag = new String(new byte[] { 1, 2, 3, 'a', 'b', 'c' });
	}

	public static class TestCls4 {
		public String tag = new String(new char[] { 1, 2, 3, 'a', 'b', 'c' });
	}

	public static class TestCls5 {
		public String tag = new String(new char[] { 1, 2, 3, 'a', 'b' });
	}

	public static class TestClsNegative {
		public String tag = new String();
	}

	public static class TestClsNegative2 {
		public byte b = 32;
		public String tag = new String(new byte[] { 31, b });
	}
}
"""
}
