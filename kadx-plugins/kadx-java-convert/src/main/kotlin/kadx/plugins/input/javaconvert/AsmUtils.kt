package kadx.plugins.input.javaconvert

import org.objectweb.asm.ClassReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * 从 class 文件读取全限定类名的工具。
 *
 * **背景**：用 ASM ClassReader 解析 class 字节（只读头部，不解码方法体），
 * 得到内部名（如 kadx/api/Decompiler）后由调用方拼上 .class 作为 jar 条目名。
 */
public object AsmUtils {

	public fun getNameFromClassFile(file: Path): String? = Files.newInputStream(file).use { `in` ->
		getClassFullName(ClassReader(`in`))
	}

	public fun getNameFromClassFile(content: ByteArray): String? = getClassFullName(ClassReader(content))

	private fun getClassFullName(classReader: ClassReader): String? = classReader.getClassName()
}
