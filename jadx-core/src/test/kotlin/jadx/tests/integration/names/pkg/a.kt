@file:Suppress("ktlint:standard:class-naming")

package jadx.tests.integration.names.pkg

class a {
	companion object {
		const val JAVA_SOURCE = """package jadx.tests.integration.names.pkg;

@SuppressWarnings({ "TypeName", "MethodName" })
public class a {
	public static a a() {
		return null;
	}
}
"""
	}
}
