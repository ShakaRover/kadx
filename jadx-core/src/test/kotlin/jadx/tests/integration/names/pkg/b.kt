@file:Suppress("ktlint:standard:class-naming")

package jadx.tests.integration.names.pkg

class b {
	companion object {
		const val JAVA_SOURCE = """package jadx.tests.integration.names.pkg;

@SuppressWarnings("TypeName")
public class b {
	class a {
	}

	private jadx.tests.integration.names.pkg.a a = jadx.tests.integration.names.pkg.a.a();
}
"""
	}
}
