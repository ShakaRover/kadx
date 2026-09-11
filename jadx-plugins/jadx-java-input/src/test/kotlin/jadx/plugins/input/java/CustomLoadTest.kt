package jadx.plugins.input.java

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.input.ICodeLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CustomLoadTest {

	private lateinit var jadx: JadxDecompiler

	@BeforeEach
	fun init() {
		jadx = JadxDecompiler(JadxArgs())
	}

	@AfterEach
	fun close() {
		jadx.close()
	}

	@Test
	fun loadFiles() {
		val files = listOf("HelloWorld.class", "HelloWorld\$HelloInner.class").map { getSample(it) }
		val loadResult: ICodeLoader = JavaInputPlugin.loadClassFiles(files)
		loadDecompiler(loadResult)
		assertThat(jadx.getClassesWithInners())
			.hasSize(2)
			.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloInner") }
	}

	@Test
	fun loadFromInputStream() {
		val fileName = "HelloWorld\$HelloInner.class"
		Files.newInputStream(getSample(fileName)).use { input: InputStream ->
			val loadResult: ICodeLoader = JavaInputPlugin.loadFromInputStream(input, fileName)
			loadDecompiler(loadResult)
			assertThat(jadx.getClassesWithInners())
				.hasSize(1)
				.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloWorld\$HelloInner") }

			println(jadx.getClassesWithInners()[0].code)
		}
	}

	@Test
	fun loadSingleClass() {
		val fileName = "HelloWorld.class"
		val content = Files.readAllBytes(getSample(fileName))
		val loadResult: ICodeLoader = JavaInputPlugin.loadSingleClass(content, fileName)
		loadDecompiler(loadResult)
		assertThat(jadx.getClassesWithInners())
			.hasSize(1)
			.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloWorld") }

		println(jadx.getClassesWithInners()[0].code)
	}

	@Test
	fun load() {
		val loadResult: ICodeLoader = JavaInputPlugin.load { loader ->
			val inputs = ArrayList<JavaClassReader>(2)
			try {
				val hello = "HelloWorld.class"
				val content = Files.readAllBytes(getSample(hello))
				inputs.add(loader.loadClass(content, hello))

				val helloInner = "HelloWorld\$HelloInner.class"
				Files.newInputStream(getSample(helloInner)).use { input ->
					inputs.addAll(loader.loadInputStream(input, helloInner))
				}
			} catch (e: Exception) {
				fail(e)
			}
			inputs
		}
		loadDecompiler(loadResult)
		assertThat(jadx.getClassesWithInners())
			.hasSize(2)
			.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls ->
				assertThat(cls.name).isEqualTo("HelloInner")
				assertThat(cls.code).isEmpty() // no code for moved inner class
			}

		assertThat(jadx.getClasses())
			.hasSize(1)
			.satisfiesOnlyOnce { cls -> assertThat(cls.name).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls ->
				assertThat(cls.innerClasses).hasSize(1)
					.satisfiesOnlyOnce { inner -> assertThat(inner.name).isEqualTo("HelloInner") }
			}

		jadx.getClassesWithInners().forEach { cls -> println(cls.code) }
	}

	fun loadDecompiler(codeLoader: ICodeLoader) {
		try {
			jadx.addCustomCodeLoader(codeLoader)
			jadx.load()
		} catch (e: Exception) {
			fail("Failed to load sample", e)
		}
	}

	fun getSample(name: String): Path = try {
		Paths.get(ClassLoader.getSystemResource("samples/" + name)!!.toURI())
	} catch (e: Exception) {
		throw AssertionError("Failed to load sample", e) // 原 Java 用 assertj fail(...)，效果同为抛 AssertionError
	}
}
