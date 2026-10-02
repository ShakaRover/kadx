package jadx.core.dex.visitors.prepare

import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.utils.android.AndroidResourcesMap
import jadx.core.utils.exceptions.JadxException

/**
 * 从内置 Android 资源映射表注入 `android.R` 常量。
 *
 * **做什么**：若开启常量替换且 `android.R` 尚未加载，则读取 [AndroidResourcesMap]
 * 里的「资源 id -> 资源路径」映射，为每个资源创建 `android.R.<类型>.<名字>` 常量字段，
 * 写入全局常量存储（[jadx.core.dex.info.ConstStorage]），供后续常量内联使用。
 *
 * **为什么**：APK 里常出现 `0x7f010001` 这类资源 id，注入符号常量后反编译结果
 * 会显示为 `R.layout.main` 之类可读名字。
 *
 * **Kotlin 转换说明**：静态常量放 companion（`const val`）；Java lambda 改为普通循环；
 * 这是 Android 专用 Pass，未来计划抽成插件（见 TODO）。
 */
// TODO: 把这个 Pass 移到独立的 "Android plugin"
@JadxVisitor(
	name = "AddAndroidConstants",
	desc = "Insert Android constants from resource mapping file",
	runBefore = [CollectConstValues::class],
)
class AddAndroidConstants : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		if (!root.getArgs().isReplaceConsts()) {
			return
		}
		if (root.resolveClass(R_CLS) != null) {
			// android.R 类已经加载
			return
		}
		val constStorage = root.getConstValues()
		for ((resId, path) in AndroidResourcesMap.getMap()) {
			val sep = path.indexOf('/')
			val clsName = R_INNER_CLS + path.substring(0, sep)
			val resName = path.substring(sep + 1)
			val cls = ClassInfo.fromName(root, clsName)
			val field = FieldInfo.from(root, cls, resName, ArgType.INT)
			constStorage.addGlobalConstField(field, resId)
		}
	}

	companion object {
		private const val R_CLS = "android.R"
		private const val R_INNER_CLS: String = R_CLS + '$'
	}
}
