@file:Suppress("ktlint:standard:property-naming")

package kadx.core.xmlgen.entry

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Original source code can be found
 * [here](https://raw.githubusercontent.com/iBotPeaches/Apktool/master/brut.apktool/apktool-lib/src/main/java/brut/androlib/res/data/ResConfigFlags.java)
 *
 * 资源“配置限定符”（如 `-zh-rCN-port-hdpi`）的解析与生成。
 * 解析 `ResTable_config` 的原始字段，校验非法值，并拼接成 Android 资源目录后缀。
 *
 * 注意：构造器会对非法字段做修正并置 [isInvalid]，随后 [generateQualifiers] 生成限定符；
 * 生成的 `-ERR<n>` 后缀依赖静态计数器 [sErrCounter]。
 */
class EntryConfig(
	mcc: Short,
	mnc: Short,
	language: CharArray,
	region: CharArray,
	orientation: Byte,
	touchscreen: Byte,
	density: Int,
	keyboard: Byte,
	navigation: Byte,
	inputFlags: Byte,
	grammaticalInflection: Byte,
	screenWidth: Short,
	screenHeight: Short,
	sdkVersion: Short,
	screenLayout: Byte,
	uiMode: Byte,
	smallestScreenWidthDp: Short,
	screenWidthDp: Short,
	screenHeightDp: Short,
	localeScript: CharArray?,
	localeVariant: CharArray?,
	screenLayout2: Byte,
	colorMode: Byte,
	isInvalid: Boolean,
	size: Int,
) {
	val mcc: Short
	val mnc: Short

	val language: CharArray
	val region: CharArray

	val orientation: Byte
	val touchscreen: Byte
	val density: Int

	val keyboard: Byte
	val navigation: Byte
	val inputFlags: Byte
	val grammaticalInflection: Byte

	val screenWidth: Short
	val screenHeight: Short

	val sdkVersion: Short

	val screenLayout: Byte
	val uiMode: Byte
	val smallestScreenWidthDp: Short

	val screenWidthDp: Short
	val screenHeightDp: Short

	private val localeScript: CharArray?
	private val localeVariant: CharArray?

	private val screenLayout2: Byte
	private val colorMode: Byte

	val isInvalid: Boolean

	private val mQualifiers: String

	private val size: Int

	init {
		var orientationV = orientation
		var touchscreenV = touchscreen
		var densityV = density
		var keyboardV = keyboard
		var navigationV = navigation
		var isInvalidV = isInvalid
		if (orientationV < 0 || orientationV > 3) {
			LOG.warn("Invalid orientation value: {}", orientationV)
			orientationV = 0
			isInvalidV = true
		}
		if (touchscreenV < 0 || touchscreenV > 3) {
			LOG.warn("Invalid touchscreen value: {}", touchscreenV)
			touchscreenV = 0
			isInvalidV = true
		}
		if (densityV < -1) {
			LOG.warn("Invalid density value: {}", densityV)
			densityV = 0
			isInvalidV = true
		}
		if (keyboardV < 0 || keyboardV > 3) {
			LOG.warn("Invalid keyboard value: {}", keyboardV)
			keyboardV = 0
			isInvalidV = true
		}
		if (navigationV < 0 || navigationV > 4) {
			LOG.warn("Invalid navigation value: {}", navigationV)
			navigationV = 0
			isInvalidV = true
		}

		var localeScriptV = localeScript
		if (localeScriptV != null && localeScriptV.isNotEmpty()) {
			if (localeScriptV[0] == '\u0000') {
				localeScriptV = null
			}
		} else {
			localeScriptV = null
		}

		var localeVariantV = localeVariant
		if (localeVariantV != null && localeVariantV.isNotEmpty()) {
			if (localeVariantV[0] == '\u0000') {
				localeVariantV = null
			}
		} else {
			localeVariantV = null
		}

		this.mcc = mcc
		this.mnc = mnc
		this.language = language
		this.region = region
		this.orientation = orientationV
		this.touchscreen = touchscreenV
		this.density = densityV
		this.keyboard = keyboardV
		this.navigation = navigationV
		this.inputFlags = inputFlags
		this.grammaticalInflection = grammaticalInflection
		this.screenWidth = screenWidth
		this.screenHeight = screenHeight
		this.sdkVersion = sdkVersion
		this.screenLayout = screenLayout
		this.uiMode = uiMode
		this.smallestScreenWidthDp = smallestScreenWidthDp
		this.screenWidthDp = screenWidthDp
		this.screenHeightDp = screenHeightDp
		this.localeScript = localeScriptV
		this.localeVariant = localeVariantV
		this.screenLayout2 = screenLayout2
		this.colorMode = colorMode
		this.isInvalid = isInvalidV
		this.size = size
		mQualifiers = generateQualifiers()
	}

	val qualifiers: String
		get() = mQualifiers

	private fun generateQualifiers(): String {
		val ret = StringBuilder()
		if (mcc.toInt() != 0) {
			ret.append("-mcc").append(String.format("%03d", mcc))
			if (mnc.toInt() != MNC_ZERO) {
				if (mnc.toInt() != 0) {
					ret.append("-mnc")
					if (size <= 32) {
						if (mnc > 0 && mnc < 10) {
							ret.append(String.format("%02d", mnc))
						} else {
							ret.append(String.format("%03d", mnc))
						}
					} else {
						ret.append(mnc.toInt())
					}
				}
			} else {
				ret.append("-mnc00")
			}
		} else {
			if (mnc.toInt() != 0) {
				ret.append("-mnc").append(mnc.toInt())
			}
		}
		ret.append(localeString)

		when (grammaticalInflection) {
			GRAMMATICAL_GENDER_NEUTER -> ret.append("-neuter")
			GRAMMATICAL_GENDER_FEMININE -> ret.append("-feminine")
			GRAMMATICAL_GENDER_MASCULINE -> ret.append("-masculine")
		}

		when (screenLayout.toInt() and MASK_LAYOUTDIR.toInt()) {
			SCREENLAYOUT_LAYOUTDIR_RTL.toInt() -> ret.append("-ldrtl")
			SCREENLAYOUT_LAYOUTDIR_LTR.toInt() -> ret.append("-ldltr")
		}
		if (smallestScreenWidthDp.toInt() != 0) {
			ret.append("-sw").append(smallestScreenWidthDp.toInt()).append("dp")
		}
		if (screenWidthDp.toInt() != 0) {
			ret.append("-w").append(screenWidthDp.toInt()).append("dp")
		}
		if (screenHeightDp.toInt() != 0) {
			ret.append("-h").append(screenHeightDp.toInt()).append("dp")
		}
		when (screenLayout.toInt() and MASK_SCREENSIZE.toInt()) {
			SCREENSIZE_SMALL.toInt() -> ret.append("-small")
			SCREENSIZE_NORMAL.toInt() -> ret.append("-normal")
			SCREENSIZE_LARGE.toInt() -> ret.append("-large")
			SCREENSIZE_XLARGE.toInt() -> ret.append("-xlarge")
		}
		when (screenLayout.toInt() and MASK_SCREENLONG.toInt()) {
			SCREENLONG_YES.toInt() -> ret.append("-long")
			SCREENLONG_NO.toInt() -> ret.append("-notlong")
		}
		when (screenLayout2.toInt() and MASK_SCREENROUND.toInt()) {
			SCREENLAYOUT_ROUND_NO.toInt() -> ret.append("-notround")
			SCREENLAYOUT_ROUND_YES.toInt() -> ret.append("-round")
		}
		when (colorMode.toInt() and COLOR_HDR_MASK.toInt()) {
			COLOR_HDR_YES.toInt() -> ret.append("-highdr")
			COLOR_HDR_NO.toInt() -> ret.append("-lowdr")
		}
		when (colorMode.toInt() and COLOR_WIDE_MASK.toInt()) {
			COLOR_WIDE_YES.toInt() -> ret.append("-widecg")
			COLOR_WIDE_NO.toInt() -> ret.append("-nowidecg")
		}
		when (orientation) {
			ORIENTATION_PORT -> ret.append("-port")
			ORIENTATION_LAND -> ret.append("-land")
			ORIENTATION_SQUARE -> ret.append("-square")
		}
		when (uiMode.toInt() and MASK_UI_MODE_TYPE.toInt()) {
			UI_MODE_TYPE_CAR.toInt() -> ret.append("-car")
			UI_MODE_TYPE_DESK.toInt() -> ret.append("-desk")
			UI_MODE_TYPE_TELEVISION.toInt() -> ret.append("-television")
			UI_MODE_TYPE_SMALLUI.toInt() -> ret.append("-smallui")
			UI_MODE_TYPE_MEDIUMUI.toInt() -> ret.append("-mediumui")
			UI_MODE_TYPE_LARGEUI.toInt() -> ret.append("-largeui")
			UI_MODE_TYPE_GODZILLAUI.toInt() -> ret.append("-godzillaui")
			UI_MODE_TYPE_HUGEUI.toInt() -> ret.append("-hugeui")
			UI_MODE_TYPE_APPLIANCE.toInt() -> ret.append("-appliance")
			UI_MODE_TYPE_WATCH.toInt() -> ret.append("-watch")
			UI_MODE_TYPE_VR_HEADSET.toInt() -> ret.append("-vrheadset")
		}
		when (uiMode.toInt() and MASK_UI_MODE_NIGHT.toInt()) {
			UI_MODE_NIGHT_YES.toInt() -> ret.append("-night")
			UI_MODE_NIGHT_NO.toInt() -> ret.append("-notnight")
		}
		when (density) {
			DENSITY_DEFAULT -> {}
			DENSITY_LOW -> ret.append("-ldpi")
			DENSITY_MEDIUM -> ret.append("-mdpi")
			DENSITY_HIGH -> ret.append("-hdpi")
			DENSITY_TV -> ret.append("-tvdpi")
			DENSITY_XHIGH -> ret.append("-xhdpi")
			DENSITY_XXHIGH -> ret.append("-xxhdpi")
			DENSITY_XXXHIGH -> ret.append("-xxxhdpi")
			DENSITY_ANY -> ret.append("-anydpi")
			DENSITY_NONE -> ret.append("-nodpi")
			else -> ret.append('-').append(density).append("dpi")
		}

		when (touchscreen) {
			TOUCHSCREEN_NOTOUCH -> ret.append("-notouch")
			TOUCHSCREEN_STYLUS -> ret.append("-stylus")
			TOUCHSCREEN_FINGER -> ret.append("-finger")
		}
		when (inputFlags.toInt() and MASK_KEYSHIDDEN.toInt()) {
			KEYSHIDDEN_NO.toInt() -> ret.append("-keysexposed")
			KEYSHIDDEN_YES.toInt() -> ret.append("-keyshidden")
			KEYSHIDDEN_SOFT.toInt() -> ret.append("-keyssoft")
		}
		when (keyboard) {
			KEYBOARD_NOKEYS -> ret.append("-nokeys")
			KEYBOARD_QWERTY -> ret.append("-qwerty")
			KEYBOARD_12KEY -> ret.append("-12key")
		}
		when (inputFlags.toInt() and MASK_NAVHIDDEN.toInt()) {
			NAVHIDDEN_NO.toInt() -> ret.append("-navexposed")
			NAVHIDDEN_YES.toInt() -> ret.append("-navhidden")
		}
		when (navigation) {
			NAVIGATION_NONAV -> ret.append("-nonav")
			NAVIGATION_DPAD -> ret.append("-dpad")
			NAVIGATION_TRACKBALL -> ret.append("-trackball")
			NAVIGATION_WHEEL -> ret.append("-wheel")
		}
		if (screenWidth.toInt() != 0 && screenHeight.toInt() != 0) {
			if (screenWidth > screenHeight) {
				ret.append(String.format("-%dx%d", screenWidth, screenHeight))
			} else {
				ret.append(String.format("-%dx%d", screenHeight, screenWidth))
			}
		}
		if (sdkVersion.toInt() > 0 && sdkVersion >= naturalSdkVersionRequirement) {
			ret.append("-v").append(sdkVersion.toInt())
		}
		if (isInvalid) {
			ret.append("-ERR").append(sErrCounter++)
		}

		return ret.toString()
	}

	private val naturalSdkVersionRequirement: Int
		get() {
			if ((uiMode.toInt() and MASK_UI_MODE_TYPE.toInt()) == UI_MODE_TYPE_VR_HEADSET.toInt() ||
				(colorMode.toInt() and COLOR_WIDE_MASK.toInt()) != 0 ||
				((colorMode.toInt() and COLOR_HDR_MASK.toInt()) != 0)
			) {
				return SDK_OREO.toInt()
			}
			if ((screenLayout2.toInt() and MASK_SCREENROUND.toInt()) != 0) {
				return SDK_MNC.toInt()
			}
			if (density == DENSITY_ANY) {
				return SDK_LOLLIPOP.toInt()
			}
			if (smallestScreenWidthDp.toInt() != 0 || screenWidthDp.toInt() != 0 || screenHeightDp.toInt() != 0) {
				return SDK_HONEYCOMB_MR2.toInt()
			}
			if ((uiMode.toInt() and (MASK_UI_MODE_TYPE.toInt() or MASK_UI_MODE_NIGHT.toInt())) != UI_MODE_NIGHT_ANY.toInt()) {
				return SDK_FROYO.toInt()
			}
			if ((screenLayout.toInt() and (MASK_SCREENSIZE.toInt() or MASK_SCREENLONG.toInt())) != SCREENSIZE_ANY.toInt() || density != DENSITY_DEFAULT) {
				return SDK_DONUT.toInt()
			}
			return 0
		}

	private val localeString: String
		get() {
			val sb = StringBuilder()

			// check for old style non BCP47 tags
			// allows values-xx-rXX, values-xx, values-xxx-rXX
			// denies values-xxx, anything else
			if (localeVariant == null && localeScript == null && (region[0] != '\u0000' || language[0] != '\u0000') &&
				region.size != 3
			) {
				sb.append('-').append(language)
				if (region[0] != '\u0000') {
					sb.append("-r").append(region)
				}
			} else { // BCP47
				if (language[0] == '\u0000' && region[0] == '\u0000') {
					return sb.toString() // early return, no language or region
				}
				sb.append("-b+")
				if (language[0] != '\u0000') {
					sb.append(language)
				}
				if (localeScript != null && localeScript.size == 4) {
					sb.append('+').append(localeScript)
				}
				if ((region.size == 2 || region.size == 3) && region[0] != '\u0000') {
					sb.append('+').append(region)
				}
				if (localeVariant != null && localeVariant.size >= 5) {
					sb.append('+').append(toUpper(localeVariant))
				}
			}
			return sb.toString()
		}

	private fun toUpper(character: CharArray): String {
		val sb = StringBuilder()
		for (ch in character) {
			sb.append(Character.toUpperCase(ch))
		}
		return sb.toString()
	}

	override fun toString(): String = if (qualifiers.isNotEmpty()) qualifiers else "[DEFAULT]"

	override fun equals(other: Any?): Boolean {
		if (other == null) {
			return false
		}
		if (javaClass != other.javaClass) {
			return false
		}
		val that = other as EntryConfig
		return this.mQualifiers == that.mQualifiers
	}

	override fun hashCode(): Int {
		var hash = 17
		hash = 31 * hash + this.mQualifiers.hashCode()
		return hash
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(EntryConfig::class.java)

		// TODO: Dirty static hack. This counter should be a part of ResPackage,
		// but it would be hard right now and this feature is very rarely used.
		private var sErrCounter = 0

		val SDK_BASE: Byte = 1

		val SDK_BASE_1_1: Byte = 2

		val SDK_CUPCAKE: Byte = 3

		val SDK_DONUT: Byte = 4

		val SDK_ECLAIR: Byte = 5

		val SDK_ECLAIR_0_1: Byte = 6

		val SDK_ECLAIR_MR1: Byte = 7

		val SDK_FROYO: Byte = 8

		val SDK_GINGERBREAD: Byte = 9

		val SDK_GINGERBREAD_MR1: Byte = 10

		val SDK_HONEYCOMB: Byte = 11

		val SDK_HONEYCOMB_MR1: Byte = 12

		val SDK_HONEYCOMB_MR2: Byte = 13

		val SDK_ICE_CREAM_SANDWICH: Byte = 14

		val SDK_ICE_CREAM_SANDWICH_MR1: Byte = 15

		val SDK_JELLY_BEAN: Byte = 16

		val SDK_JELLY_BEAN_MR1: Byte = 17

		val SDK_JELLY_BEAN_MR2: Byte = 18

		val SDK_KITKAT: Byte = 19

		val SDK_LOLLIPOP: Byte = 21

		val SDK_LOLLIPOP_MR1: Byte = 22

		val SDK_MNC: Byte = 23

		val SDK_NOUGAT: Byte = 24

		val SDK_NOUGAT_MR1: Byte = 25

		val SDK_OREO: Byte = 26

		val SDK_OREO_MR1: Byte = 27

		val SDK_P: Byte = 28

		val ORIENTATION_ANY: Byte = 0

		val ORIENTATION_PORT: Byte = 1

		val ORIENTATION_LAND: Byte = 2

		val ORIENTATION_SQUARE: Byte = 3

		val TOUCHSCREEN_ANY: Byte = 0

		val TOUCHSCREEN_NOTOUCH: Byte = 1

		val TOUCHSCREEN_STYLUS: Byte = 2

		val TOUCHSCREEN_FINGER: Byte = 3

		val DENSITY_DEFAULT: Int = 0

		val DENSITY_LOW: Int = 120

		val DENSITY_MEDIUM: Int = 160

		val DENSITY_400: Int = 190

		val DENSITY_TV: Int = 213

		val DENSITY_HIGH: Int = 240

		val DENSITY_XHIGH: Int = 320

		val DENSITY_XXHIGH: Int = 480

		val DENSITY_XXXHIGH: Int = 640

		val DENSITY_ANY: Int = 0xFFFE

		val DENSITY_NONE: Int = 0xFFFF

		val MNC_ZERO: Int = -1

		val MASK_LAYOUTDIR: Short = 0xc0

		val SCREENLAYOUT_LAYOUTDIR_ANY: Short = 0x00

		val SCREENLAYOUT_LAYOUTDIR_LTR: Short = 0x40

		val SCREENLAYOUT_LAYOUTDIR_RTL: Short = 0x80

		val SCREENLAYOUT_LAYOUTDIR_SHIFT: Short = 0x06

		val MASK_SCREENROUND: Short = 0x03

		val SCREENLAYOUT_ROUND_ANY: Short = 0

		val SCREENLAYOUT_ROUND_NO: Short = 0x1

		val SCREENLAYOUT_ROUND_YES: Short = 0x2

		val KEYBOARD_ANY: Byte = 0

		val KEYBOARD_NOKEYS: Byte = 1

		val KEYBOARD_QWERTY: Byte = 2

		val KEYBOARD_12KEY: Byte = 3

		val NAVIGATION_ANY: Byte = 0

		val NAVIGATION_NONAV: Byte = 1

		val NAVIGATION_DPAD: Byte = 2

		val NAVIGATION_TRACKBALL: Byte = 3

		val NAVIGATION_WHEEL: Byte = 4

		val MASK_KEYSHIDDEN: Byte = 0x3

		val KEYSHIDDEN_ANY: Byte = 0x0

		val KEYSHIDDEN_NO: Byte = 0x1

		val KEYSHIDDEN_YES: Byte = 0x2

		val KEYSHIDDEN_SOFT: Byte = 0x3

		val MASK_NAVHIDDEN: Byte = 0xc

		val NAVHIDDEN_ANY: Byte = 0x0

		val NAVHIDDEN_NO: Byte = 0x4

		val NAVHIDDEN_YES: Byte = 0x8

		val MASK_SCREENSIZE: Byte = 0x0f

		val SCREENSIZE_ANY: Byte = 0x00

		val SCREENSIZE_SMALL: Byte = 0x01

		val SCREENSIZE_NORMAL: Byte = 0x02

		val SCREENSIZE_LARGE: Byte = 0x03

		val SCREENSIZE_XLARGE: Byte = 0x04

		val MASK_SCREENLONG: Byte = 0x30

		val SCREENLONG_ANY: Byte = 0x00

		val SCREENLONG_NO: Byte = 0x10

		val SCREENLONG_YES: Byte = 0x20

		val MASK_UI_MODE_TYPE: Byte = 0x0f

		val UI_MODE_TYPE_ANY: Byte = 0x00

		val UI_MODE_TYPE_NORMAL: Byte = 0x01

		val UI_MODE_TYPE_DESK: Byte = 0x02

		val UI_MODE_TYPE_CAR: Byte = 0x03

		val UI_MODE_TYPE_TELEVISION: Byte = 0x04

		val UI_MODE_TYPE_APPLIANCE: Byte = 0x05

		val UI_MODE_TYPE_WATCH: Byte = 0x06

		val UI_MODE_TYPE_VR_HEADSET: Byte = 0x07

		// start - miui
		val UI_MODE_TYPE_GODZILLAUI: Byte = 0x0b

		val UI_MODE_TYPE_SMALLUI: Byte = 0x0c

		val UI_MODE_TYPE_MEDIUMUI: Byte = 0x0d

		val UI_MODE_TYPE_LARGEUI: Byte = 0x0e

		val UI_MODE_TYPE_HUGEUI: Byte = 0x0f
		// end - miui

		val MASK_UI_MODE_NIGHT: Byte = 0x30

		val UI_MODE_NIGHT_ANY: Byte = 0x00

		val UI_MODE_NIGHT_NO: Byte = 0x10

		val UI_MODE_NIGHT_YES: Byte = 0x20

		val COLOR_HDR_MASK: Byte = 0xC

		val COLOR_HDR_NO: Byte = 0x4

		val COLOR_HDR_SHIFT: Byte = 0x2

		val COLOR_HDR_UNDEFINED: Byte = 0x0

		val COLOR_HDR_YES: Byte = 0x8

		val COLOR_UNDEFINED: Byte = 0x0

		val COLOR_WIDE_UNDEFINED: Byte = 0x0

		val COLOR_WIDE_NO: Byte = 0x1

		val COLOR_WIDE_YES: Byte = 0x2

		val COLOR_WIDE_MASK: Byte = 0x3

		val GRAMMATICAL_GENDER_ANY: Byte = 0

		val GRAMMATICAL_GENDER_NEUTER: Byte = 1

		val GRAMMATICAL_GENDER_FEMININE: Byte = 2

		val GRAMMATICAL_GENDER_MASCULINE: Byte = 3
	}
}
