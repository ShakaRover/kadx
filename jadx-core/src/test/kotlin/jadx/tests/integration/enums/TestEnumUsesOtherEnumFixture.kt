package jadx.tests.integration.enums

object TestEnumUsesOtherEnumFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

public class TestEnumUsesOtherEnumFixture {

	public static class TestCls {

		public enum VType {
			INT(1),
			OTHER_INT(INT);

			private final int type;

			VType(int type) {
				this.type = type;
			}

			VType(VType refType) {
				this(refType.type);
			}
		}
	}
}
"""
}
