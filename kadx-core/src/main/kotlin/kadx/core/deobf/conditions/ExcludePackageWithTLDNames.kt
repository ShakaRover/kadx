package kadx.core.deobf.conditions

import kadx.api.deobf.IDeobfCondition.Action
import kadx.core.dex.nodes.PackageNode
import kadx.core.utils.exceptions.KadxRuntimeException
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 排除“顶级域名（TLD）”同名包的改写条件。
 *
 * **背景**：有些 Android 应用会把包名直接写成域名（如 `com`、`org`、`net`、`io`…）。
 * 如果把根包 `com` 重命名成 `p000`，就会破坏这种可读的命名，因此需要保护。
 *
 * 实现上从资源文件 `tlds.txt` 读取全部 TLD 列表，并在首次访问时懒加载（对应原 Java
 * 的静态内部类 `TldHolder` 单例持有者写法）。
 */
class ExcludePackageWithTLDNames : AbstractDeobfCondition() {

	companion object {
		/** 懒加载的顶级域名集合（只在第一次使用时读取文件） */
		private val TLD_SET: Set<String> by lazy { loadTldSet() }

		/** 读取并解析 `tlds.txt`：忽略以 `#` 开头的注释行与空行。 */
		private fun loadTldSet(): Set<String> {
			try {
				val stream = ExcludePackageWithTLDNames::class.java.getResourceAsStream("tlds.txt")
				BufferedReader(InputStreamReader(checkNotNull(stream))).use { reader ->
					return reader.readLines()
						.filter { line -> !line.startsWith("#") && line.isNotEmpty() }
						.toSet()
				}
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to load top level domain list file: tlds.txt", e)
			}
		}
	}

	override fun check(pkg: PackageNode): Action {
		if (pkg.isRoot() && TLD_SET.contains(pkg.name)) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}
}
