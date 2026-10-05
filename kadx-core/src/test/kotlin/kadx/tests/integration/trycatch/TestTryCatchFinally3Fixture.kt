package kadx.tests.integration.trycatch

object TestTryCatchFinally3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kadx.core.dex.nodes.ClassNode;
import kadx.core.dex.visitors.DepthTraversal;
import kadx.core.dex.visitors.IDexTreeVisitor;

public class TestTryCatchFinally3Fixture {

	public static class TestCls {
		private static final Logger LOG = LoggerFactory.getLogger(TestCls.class);

		public static void test(ClassNode cls, List<IDexTreeVisitor> passes) {
			try {
				cls.load();
				for (IDexTreeVisitor visitor : passes) {
					DepthTraversal.visit(visitor, cls);
				}
			} catch (Exception e) {
				LOG.error("Class process exception: {}", cls, e);
			} finally {
				cls.unload();
			}
		}
	}
}
"""
}
