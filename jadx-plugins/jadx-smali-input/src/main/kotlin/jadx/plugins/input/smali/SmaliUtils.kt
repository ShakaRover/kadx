package jadx.plugins.input.smali

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.smali.SmaliOptions
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.RecognitionException
import org.antlr.runtime.TokenStream
import org.antlr.runtime.tree.CommonTreeNodeStream
import org.antlr.runtime.tree.TreeNodeStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 把 smali 文件汇编成 dex 字节数组的工具。
 *
 * **背景**：直接复用 smali 库内部的 ANTLR 解析器（smaliFlexLexer / smaliParser /
 * smaliTreeWalker）和 dexlib2 的 DexBuilder，在内存中完成 smali → dex 转换。
 * 两个私有 Wrapper 类覆写 emitErrorMessage()，把语法错误收集到 StringBuilder
 * 而不是打印到控制台，出错时统一抛出带完整错误信息的 RuntimeException。
 */
public object SmaliUtils {

	public fun assemble(smaliFile: File, options: SmaliOptions): ByteArray {
		val errors = StringBuilder()
		FileInputStream(smaliFile).use { fis ->
			InputStreamReader(fis, StandardCharsets.UTF_8).use { reader ->
				val lexer = smaliFlexLexer(reader, options.apiLevel)
				lexer.setSourceFile(smaliFile)
				val tokens = CommonTokenStream(lexer)
				val parser = ParserWrapper(tokens, errors)
				parser.setVerboseErrors(options.verboseErrors)
				parser.setAllowOdex(options.allowOdexOpcodes)
				parser.setApiLevel(options.apiLevel)
				val parseResult = parser.smali_file()
				if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
					throw RuntimeException("Smali parse error: $errors")
				}
				val treeStream = CommonTreeNodeStream(parseResult.getTree())
				treeStream.setTokenStream(tokens)

				val dexBuilder = DexBuilder(Opcodes.forApi(options.apiLevel))
				val dexGen = TreeWalkerWrapper(treeStream, errors)
				dexGen.setApiLevel(options.apiLevel)
				dexGen.setVerboseErrors(options.verboseErrors)
				dexGen.setDexBuilder(dexBuilder)
				dexGen.smali_file()
				if (dexGen.numberOfSyntaxErrors > 0) {
					throw RuntimeException("Smali compile error: $errors")
				}
				val dataStore = MemoryDataStore()
				dexBuilder.writeTo(dataStore)
				return dataStore.getData()
			}
		}
	}

	private class ParserWrapper(input: TokenStream, private val errors: StringBuilder) : smaliParser(input) {
		override fun emitErrorMessage(msg: String) {
			errors.append('\n').append(msg)
		}
	}

	private class TreeWalkerWrapper(input: TreeNodeStream, private val errors: StringBuilder) : smaliTreeWalker(input) {
		override fun emitErrorMessage(msg: String) {
			errors.append('\n').append(msg)
		}
	}
}
