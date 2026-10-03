package jadx.tests.integration.code

object TestCodeCommentStyleFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.code;

public class TestCodeCommentStyleFixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		public int aSingleLine;
		public int aMultiLine;

		public int block;
		public int blockMulti;
		public int blockCondensed;
		public int blockCondensedMulti;

		public int javaDoc;
		public int javaDocMulti;
		public int javaDocCondensed;
		public int javaDocCondensedMulti;
	}
}
"""
}
