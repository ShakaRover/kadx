@file:Suppress("ktlint:standard:property-naming")

package jadx.gui.utils

import javax.swing.ImageIcon

/**
 * 界面中使用的 SVG 图标常量表。
 *
 * **做什么**：集中定义所有图标常量，供各处按 `Icons.XXX` 引用。
 *
 * **为什么用 `object` + `@JvmField`**：原 Java 是 `public static final` 字段，
 * Java 调用方按静态字段访问；`object` 内的 `@JvmField` 生成的正是同名静态字段，
 * Java 侧零改动。
 */
object Icons {

	@JvmField
	val OPEN: ImageIcon = UiUtils.openSvgIcon("ui/openDisk")

	@JvmField
	val OPEN_PROJECT: ImageIcon = UiUtils.openSvgIcon("ui/projectDirectory")

	@JvmField
	val NEW_PROJECT: ImageIcon = UiUtils.openSvgIcon("ui/newFolder")

	@JvmField
	val CLOSE: ImageIcon = UiUtils.openSvgIcon("ui/closeHovered")

	@JvmField
	val CLOSE_INACTIVE: ImageIcon = UiUtils.openSvgIcon("ui/close")

	@JvmField
	val SAVE_ALL: ImageIcon = UiUtils.openSvgIcon("ui/menu-saveall")

	@JvmField
	val FLAT_PKG: ImageIcon = UiUtils.openSvgIcon("ui/moduleGroup")

	@JvmField
	val QUICK_TABS: ImageIcon = UiUtils.openSvgIcon("ui/dataView")

	@JvmField
	val PIN: ImageIcon = UiUtils.openSvgIcon("nodes/pin")

	@JvmField
	val PIN_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/pin_dark")

	@JvmField
	val PIN_HOVERED: ImageIcon = UiUtils.openSvgIcon("nodes/pinHovered")

	@JvmField
	val PIN_HOVERED_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/pinHovered_dark")

	@JvmField
	val BOOKMARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark")

	@JvmField
	val BOOKMARK_OVERLAY: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_overlay")

	@JvmField
	val BOOKMARK_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_dark")

	@JvmField
	val BOOKMARK_OVERLAY_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_overlay_dark")

	@JvmField
	val STATIC: ImageIcon = UiUtils.openSvgIcon("nodes/staticMark")

	@JvmField
	val FINAL: ImageIcon = UiUtils.openSvgIcon("nodes/finalMark")

	@JvmField
	val START_PAGE: ImageIcon = UiUtils.openSvgIcon("nodes/newWindow")

	@JvmField
	val FOLDER: ImageIcon = UiUtils.openSvgIcon("nodes/folder")

	@JvmField
	val FILE: ImageIcon = UiUtils.openSvgIcon("nodes/file_any_type")

	@JvmField
	val PACKAGE: ImageIcon = UiUtils.openSvgIcon("nodes/package")

	@JvmField
	val CLASS: ImageIcon = UiUtils.openSvgIcon("nodes/class")

	@JvmField
	val METHOD: ImageIcon = UiUtils.openSvgIcon("nodes/method")

	@JvmField
	val FIELD: ImageIcon = UiUtils.openSvgIcon("nodes/field")

	@JvmField
	val PROPERTY: ImageIcon = UiUtils.openSvgIcon("nodes/property")

	@JvmField
	val PARAMETER: ImageIcon = UiUtils.openSvgIcon("nodes/parameter")

	@JvmField
	val RUN: ImageIcon = UiUtils.openSvgIcon("ui/run")

	@JvmField
	val CHECK: ImageIcon = UiUtils.openSvgIcon("ui/checkConstraint")

	@JvmField
	val FORMAT: ImageIcon = UiUtils.openSvgIcon("ui/toolWindowMessages")

	@JvmField
	val RESET: ImageIcon = UiUtils.openSvgIcon("ui/reset")

	@JvmField
	val FONT: ImageIcon = UiUtils.openSvgIcon("nodes/fontFile")

	@JvmField
	val ICON_MARK: ImageIcon = UiUtils.openSvgIcon("search/mark")

	@JvmField
	val ICON_MARK_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/previewSelected")

	@JvmField
	val ICON_REGEX: ImageIcon = UiUtils.openSvgIcon("search/regexHovered")

	@JvmField
	val ICON_REGEX_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/regexSelected")

	@JvmField
	val ICON_WORDS: ImageIcon = UiUtils.openSvgIcon("search/wordsHovered")

	@JvmField
	val ICON_WORDS_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/wordsSelected")

	@JvmField
	val ICON_MATCH: ImageIcon = UiUtils.openSvgIcon("search/matchCaseHovered")

	@JvmField
	val ICON_MATCH_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/matchCaseSelected")

	@JvmField
	val ICON_UP: ImageIcon = UiUtils.openSvgIcon("ui/top")

	@JvmField
	val ICON_DOWN: ImageIcon = UiUtils.openSvgIcon("ui/bottom")

	@JvmField
	val ICON_CLOSE: ImageIcon = UiUtils.openSvgIcon("ui/close")

	@JvmField
	val ICON_FIND_TYPE_TXT: ImageIcon = UiUtils.openSvgIcon("search/text")

	@JvmField
	val ICON_FIND_TYPE_HEX: ImageIcon = UiUtils.openSvgIcon("search/hexSerial")

	@JvmField
	val ICON_ACTIVE_TAB: ImageIcon = UiUtils.openSvgIcon("search/activeTab")

	@JvmField
	val ICON_ACTIVE_TAB_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/activeTabSelected")
}
