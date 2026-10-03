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

	val OPEN: ImageIcon = UiUtils.openSvgIcon("ui/openDisk")

	val OPEN_PROJECT: ImageIcon = UiUtils.openSvgIcon("ui/projectDirectory")

	val NEW_PROJECT: ImageIcon = UiUtils.openSvgIcon("ui/newFolder")

	val CLOSE: ImageIcon = UiUtils.openSvgIcon("ui/closeHovered")

	val CLOSE_INACTIVE: ImageIcon = UiUtils.openSvgIcon("ui/close")

	val SAVE_ALL: ImageIcon = UiUtils.openSvgIcon("ui/menu-saveall")

	val FLAT_PKG: ImageIcon = UiUtils.openSvgIcon("ui/moduleGroup")

	val QUICK_TABS: ImageIcon = UiUtils.openSvgIcon("ui/dataView")

	val PIN: ImageIcon = UiUtils.openSvgIcon("nodes/pin")

	val PIN_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/pin_dark")

	val PIN_HOVERED: ImageIcon = UiUtils.openSvgIcon("nodes/pinHovered")

	val PIN_HOVERED_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/pinHovered_dark")

	val BOOKMARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark")

	val BOOKMARK_OVERLAY: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_overlay")

	val BOOKMARK_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_dark")

	val BOOKMARK_OVERLAY_DARK: ImageIcon = UiUtils.openSvgIcon("nodes/bookmark_overlay_dark")

	val STATIC: ImageIcon = UiUtils.openSvgIcon("nodes/staticMark")

	val FINAL: ImageIcon = UiUtils.openSvgIcon("nodes/finalMark")

	val START_PAGE: ImageIcon = UiUtils.openSvgIcon("nodes/newWindow")

	val FOLDER: ImageIcon = UiUtils.openSvgIcon("nodes/folder")

	val FILE: ImageIcon = UiUtils.openSvgIcon("nodes/file_any_type")

	val PACKAGE: ImageIcon = UiUtils.openSvgIcon("nodes/package")

	val CLASS: ImageIcon = UiUtils.openSvgIcon("nodes/class")

	val METHOD: ImageIcon = UiUtils.openSvgIcon("nodes/method")

	val FIELD: ImageIcon = UiUtils.openSvgIcon("nodes/field")

	val PROPERTY: ImageIcon = UiUtils.openSvgIcon("nodes/property")

	val PARAMETER: ImageIcon = UiUtils.openSvgIcon("nodes/parameter")

	val RUN: ImageIcon = UiUtils.openSvgIcon("ui/run")

	val CHECK: ImageIcon = UiUtils.openSvgIcon("ui/checkConstraint")

	val FORMAT: ImageIcon = UiUtils.openSvgIcon("ui/toolWindowMessages")

	val RESET: ImageIcon = UiUtils.openSvgIcon("ui/reset")

	val FONT: ImageIcon = UiUtils.openSvgIcon("nodes/fontFile")

	val ICON_MARK: ImageIcon = UiUtils.openSvgIcon("search/mark")

	val ICON_MARK_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/previewSelected")

	val ICON_REGEX: ImageIcon = UiUtils.openSvgIcon("search/regexHovered")

	val ICON_REGEX_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/regexSelected")

	val ICON_WORDS: ImageIcon = UiUtils.openSvgIcon("search/wordsHovered")

	val ICON_WORDS_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/wordsSelected")

	val ICON_MATCH: ImageIcon = UiUtils.openSvgIcon("search/matchCaseHovered")

	val ICON_MATCH_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/matchCaseSelected")

	val ICON_UP: ImageIcon = UiUtils.openSvgIcon("ui/top")

	val ICON_DOWN: ImageIcon = UiUtils.openSvgIcon("ui/bottom")

	val ICON_CLOSE: ImageIcon = UiUtils.openSvgIcon("ui/close")

	val ICON_FIND_TYPE_TXT: ImageIcon = UiUtils.openSvgIcon("search/text")

	val ICON_FIND_TYPE_HEX: ImageIcon = UiUtils.openSvgIcon("search/hexSerial")

	val ICON_ACTIVE_TAB: ImageIcon = UiUtils.openSvgIcon("search/activeTab")

	val ICON_ACTIVE_TAB_SELECTED: ImageIcon = UiUtils.openSvgIcon("search/activeTabSelected")
}
