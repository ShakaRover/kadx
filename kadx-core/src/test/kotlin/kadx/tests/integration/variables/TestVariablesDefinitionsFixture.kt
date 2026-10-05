package kadx.tests.integration.variables

object TestVariablesDefinitionsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.variables;

import java.util.List;

import org.slf4j.Logger;

import kadx.core.dex.nodes.ClassNode;
import kadx.core.dex.visitors.DepthTraversal;
import kadx.core.dex.visitors.IDexTreeVisitor;

public class TestVariablesDefinitionsFixture {

	public static class TestCls {
		private static Logger log;
		private ClassNode cls;
		private List<IDexTreeVisitor> passes;

		public void test() {
			try {
				cls.load();
				for (IDexTreeVisitor pass : this.passes) {
					DepthTraversal.visit(pass, cls);
				}
			} catch (Exception e) {
				log.error("Decode exception: {}", cls, e);
			}
		}
	}
}
"""
}
