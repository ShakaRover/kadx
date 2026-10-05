@file:Suppress("ktlint:standard:class-naming")

package kadx.tests.integration.names.pkg

class b {
	companion object {
		const val JAVA_SOURCE = """package kadx.tests.integration.names.pkg;

@SuppressWarnings("TypeName")
public class b {
	class a {
	}

	private kadx.tests.integration.names.pkg.a a = kadx.tests.integration.names.pkg.a.a();
}
"""
	}
}
