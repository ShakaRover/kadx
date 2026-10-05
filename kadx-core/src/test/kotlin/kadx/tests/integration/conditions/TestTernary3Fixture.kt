package kadx.tests.integration.conditions

object TestTernary3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

import kadx.core.dex.instructions.args.InsnArg;
import kadx.core.dex.instructions.args.Named;
import kadx.core.dex.instructions.args.RegisterArg;

public class TestTernary3Fixture {

	public static class TestCls {

		public boolean isNameEquals(InsnArg arg) {
			String n = getName(arg);
			if (n == null || !(arg instanceof Named)) {
				return false;
			}
			return n.equals(((Named) arg).getName());
		}

		private String getName(InsnArg arg) {
			if (arg instanceof RegisterArg) {
				return "r";
			}
			if (arg instanceof Named) {
				return "n";
			}
			return arg.toString();
		}
	}
}
"""
}
