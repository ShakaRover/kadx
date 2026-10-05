@file:Suppress("ktlint:standard:class-naming")

package kadx.tests.integration.names.pkg

class a {
	companion object {
		const val JAVA_SOURCE = """package kadx.tests.integration.names.pkg;

@SuppressWarnings({ "TypeName", "MethodName" })
public class a {
	public static a a() {
		return null;
	}
}
"""
	}
}
