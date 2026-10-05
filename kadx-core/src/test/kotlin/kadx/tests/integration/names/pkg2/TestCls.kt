package kadx.tests.integration.names.pkg2

class TestCls {
	companion object {
		const val JAVA_SOURCE = """package kadx.tests.integration.names.pkg2;

public class TestCls {
	public void doSomething() {
		System.doSomething();
		java.lang.System.out.println("Hello World");
	}
}
"""
	}
}
