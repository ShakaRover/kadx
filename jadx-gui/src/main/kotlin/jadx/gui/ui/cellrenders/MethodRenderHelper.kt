package jadx.gui.ui.cellrenders

import jadx.api.JavaMethod
import jadx.gui.utils.Icons
import jadx.gui.utils.OverlayIcon
import jadx.gui.utils.UiUtils
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 方法列表/树节点的图标与基础文本生成工具。
 *
 * **做什么**：根据方法的访问标志（public/private/abstract/...）挑选基础图标，
 * 再叠加 `final`/`static` 角标；同时生成不含返回类型的“方法签名”文本。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是纯静态工具类，Java 调用方
 * （如 `JMethod`）仍以 `MethodRenderHelper.getIcon(...)` 形式访问，保持零改动。
 */
object MethodRenderHelper {

	private val ICON_METHOD_ABSTRACT: ImageIcon = UiUtils.openSvgIcon("nodes/abstractMethod")
	private val ICON_METHOD_PRIVATE: ImageIcon = UiUtils.openSvgIcon("nodes/privateMethod")
	private val ICON_METHOD_PROTECTED: ImageIcon = UiUtils.openSvgIcon("nodes/protectedMethod")
	private val ICON_METHOD_PUBLIC: ImageIcon = UiUtils.openSvgIcon("nodes/publicMethod")
	private val ICON_METHOD_CONSTRUCTOR: ImageIcon = UiUtils.openSvgIcon("nodes/constructorMethod")
	private val ICON_METHOD_SYNC: ImageIcon = UiUtils.openSvgIcon("nodes/methodReference")

	/**
	 * 计算方法节点图标。
	 *
	 * 注意：这里刻意保留原 Java 的“顺序覆盖”写法——后面的判断会覆盖前面的结果，
	 * 因此 `synchronized` 的图标优先级最高，`abstract` 最低，语义与原来完全一致。
	 */
	@JvmStatic
	fun getIcon(mth: JavaMethod): Icon {
		val accessFlags = mth.getAccessFlags()
		var icon: Icon = Icons.METHOD
		if (accessFlags.isAbstract()) {
			icon = ICON_METHOD_ABSTRACT
		}
		if (accessFlags.isConstructor()) {
			icon = ICON_METHOD_CONSTRUCTOR
		}
		if (accessFlags.isPublic()) {
			icon = ICON_METHOD_PUBLIC
		}
		if (accessFlags.isPrivate()) {
			icon = ICON_METHOD_PRIVATE
		}
		if (accessFlags.isProtected()) {
			icon = ICON_METHOD_PROTECTED
		}
		if (accessFlags.isSynchronized()) {
			icon = ICON_METHOD_SYNC
		}

		val overIcon = OverlayIcon(icon)
		if (accessFlags.isFinal()) {
			overIcon.add(Icons.FINAL)
		}
		if (accessFlags.isStatic()) {
			overIcon.add(Icons.STATIC)
		}
		return overIcon
	}

	/**
	 * 生成方法基础展示文本，例如 `foo(int, String)`；构造器使用类名。
	 * 类初始化块（`<clinit>`）特殊显示为 `{...}`。
	 */
	@JvmStatic
	fun makeBaseString(mth: JavaMethod): String {
		if (mth.isClassInit()) {
			return "{...}"
		}
		val base = StringBuilder()
		if (mth.isConstructor()) {
			base.append(mth.getDeclaringClass().getName())
		} else {
			base.append(mth.getName())
		}
		base.append('(')
		val args = mth.getArguments()
		for ((i, arg) in args.withIndex()) {
			base.append(UiUtils.typeStr(arg))
			if (i < args.size - 1) {
				base.append(", ")
			}
		}
		base.append(')')
		return base.toString()
	}
}
