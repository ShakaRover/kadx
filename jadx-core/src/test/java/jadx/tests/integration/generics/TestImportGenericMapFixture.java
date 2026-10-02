package jadx.tests.integration.generics;

public class TestImportGenericMapFixture {

	public static class SuperClass<O extends SuperClass.ToImport> {

		interface ToImport {
		}

		interface NotToImport {
		}

		static final class Class1<C extends NotToImport> {
		}

		public <C extends NotToImport> SuperClass(Class1<C> zzf) {
		}
	}
}
