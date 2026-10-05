package kadx.tests.integration.others

object TestJavaDupInsnFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import kadx.core.dex.instructions.args.RegisterArg;
import kadx.core.dex.instructions.args.SSAVar;
import kadx.core.dex.nodes.BlockNode;
import kadx.core.dex.nodes.MethodNode;

public class TestJavaDupInsnFixture {

	public static class TestCls {
		private MethodNode mth;
		private BlockNode block;
		private SSAVar[] vars;
		private int[] versions;

		public SSAVar test(RegisterArg regArg) {
			int regNum = regArg.getRegNum();
			int version = versions[regNum]++;
			SSAVar ssaVar = mth.makeNewSVar(regNum, version, regArg);
			vars[regNum] = ssaVar;
			return ssaVar;
		}
	}
}
"""
}
