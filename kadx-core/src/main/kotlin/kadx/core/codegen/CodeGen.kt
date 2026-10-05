package kadx.core.codegen

import kadx.api.ICodeInfo
import kadx.api.KadxArgs
import kadx.api.impl.SimpleCodeInfo
import kadx.core.codegen.json.JsonCodeGen
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.nodes.ClassNode
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.concurrent.Callable

/**
 * 代码生成入口：根据输出格式（Java 源码 / JSON）把 [ClassNode] 转成 [ICodeInfo]。
 *
 * **重试机制**：若生成过程中某些 visitor 请求重启（[AFlag.RESTART_CODEGEN]），
 * 会清除标记并重试一次；再次失败才抛异常。
 *
 * **Kotlin 转换说明**：原 Java 为静态工具类，这里保留私有构造器 + `companion object`，
 * 调用方 `CodeGen.generate(cls)` 保持不变。
 */
class CodeGen private constructor() {

	companion object {
		fun generate(cls: ClassNode): ICodeInfo {
			if (cls.contains(AFlag.DONT_GENERATE)) {
				return ICodeInfo.EMPTY
			}
			val args = cls.root().getArgs()
			return when (args.outputFormat) {
				KadxArgs.OutputFormatEnum.JAVA -> generateJavaCode(cls, args)
				KadxArgs.OutputFormatEnum.JSON -> generateJson(cls)
				else -> throw KadxRuntimeException("Unknown output format")
			}
		}

		private fun generateJavaCode(cls: ClassNode, args: KadxArgs): ICodeInfo {
			val clsGen = ClassGen(cls, args)
			return wrapCodeGen(cls) { clsGen.makeClass() }
		}

		private fun generateJson(cls: ClassNode): ICodeInfo {
			val codeGen = JsonCodeGen(cls)
			val clsJson = wrapCodeGen(cls) { codeGen.process() }
			return SimpleCodeInfo(clsJson)
		}

		private fun <R> wrapCodeGen(cls: ClassNode, codeGenFunc: Callable<R>): R {
			try {
				return codeGenFunc.call()
			} catch (e: Exception) {
				if (cls.contains(AFlag.RESTART_CODEGEN)) {
					cls.remove(AFlag.RESTART_CODEGEN)
					try {
						return codeGenFunc.call()
					} catch (ex: Exception) {
						throw KadxRuntimeException("Code generation error after restart", ex)
					}
				} else {
					throw KadxRuntimeException("Code generation error", e)
				}
			}
		}
	}
}
