@file:Suppress("ktlint:standard:property-naming")

package kadx.core.xmlgen

import java.util.HashMap

/**
 * 二进制资源表 / 二进制 XML 解析所需的常量。
 *
 * 常量来源为 AOSP `frameworks/base/libs/androidfw/include/androidfw/ResourceTypes.h`，
 * 命名与原 Java 完全一致。
 *
 * 转换说明：原 `protected static final` 常量放入 companion 的 `val`。
 * Kotlin 没有“包级可见性”，而 [XmlGenUtils]/[ResXmlGen] 等同类但不是子类的代码也要访问，
 * 故这些常量按 public 暴露（以 `ParserConstants.XXX` 访问）。
 */
open class ParserConstants protected constructor() {

	companion object {
		val ANDROID_NS_URL = "http://schemas.android.com/apk/res/android"

		val ANDROID_NS_VALUE = "android"

		/**
		 * Chunk types as defined in frameworks/base/libs/androidfw/include/androidfw/ResourceTypes.h (AOSP)
		 */
		val RES_NULL_TYPE = 0x0000

		val RES_STRING_POOL_TYPE = 0x0001

		val RES_TABLE_TYPE = 0x0002

		val RES_XML_TYPE = 0x0003

		val RES_XML_FIRST_CHUNK_TYPE = 0x0100

		val RES_XML_START_NAMESPACE_TYPE = 0x0100

		val RES_XML_END_NAMESPACE_TYPE = 0x0101

		val RES_XML_START_ELEMENT_TYPE = 0x0102

		val RES_XML_END_ELEMENT_TYPE = 0x0103

		val RES_XML_CDATA_TYPE = 0x0104

		val RES_XML_LAST_CHUNK_TYPE = 0x017f

		val RES_XML_RESOURCE_MAP_TYPE = 0x0180

		val RES_TABLE_PACKAGE_TYPE = 0x0200 // 512

		val RES_TABLE_TYPE_TYPE = 0x0201 // 513

		val RES_TABLE_TYPE_SPEC_TYPE = 0x0202 // 514

		val RES_TABLE_TYPE_LIBRARY = 0x0203 // 515

		val RES_TABLE_TYPE_OVERLAY = 0x0204 // 516

		val RES_TABLE_TYPE_OVERLAY_POLICY = 0x0205 // 517

		val RES_TABLE_TYPE_STAGED_ALIAS = 0x0206 // 518

		/**
		 * Type constants
		 */
		// Contains no data.
		val TYPE_NULL = 0x00

		// The 'data' holds a ResTable_ref, a reference to another resource table entry.
		val TYPE_REFERENCE = 0x01

		// The 'data' holds an attribute resource identifier.
		val TYPE_ATTRIBUTE = 0x02

		// The 'data' holds an index into the containing resource table's global value string pool.
		val TYPE_STRING = 0x03

		// The 'data' holds a single-precision floating point number.
		val TYPE_FLOAT = 0x04

		// The 'data' holds a complex number encoding a dimension value, such as "100in".
		val TYPE_DIMENSION = 0x05

		// The 'data' holds a complex number encoding a fraction of a container.
		val TYPE_FRACTION = 0x06

		/**
		 * The 'data' holds a dynamic reference, a reference to another resource table entry.
		 * See https://github.com/skylot/jadx/issues/919
		 */
		val TYPE_DYNAMIC_REFERENCE = 0x07

		/**
		 * According to the sources of apktool this type seem to be related to themes
		 * See https://github.com/skylot/jadx/issues/919
		 */
		val TYPE_DYNAMIC_ATTRIBUTE = 0x08

		// Beginning of integer flavors...
		val TYPE_FIRST_INT = 0x10

		// The 'data' is a raw integer value of the form n..n.
		val TYPE_INT_DEC = 0x10

		// The 'data' is a raw integer value of the form 0xn..n.
		val TYPE_INT_HEX = 0x11

		// The 'data' is either 0 or 1, for input "false" or "true" respectively.
		val TYPE_INT_BOOLEAN = 0x12

		// Beginning of color integer flavors...
		val TYPE_FIRST_COLOR_INT = 0x1c

		// The 'data' is a raw integer value of the form #aarrggbb.
		val TYPE_INT_COLOR_ARGB8 = 0x1c

		// The 'data' is a raw integer value of the form #rrggbb.
		val TYPE_INT_COLOR_RGB8 = 0x1d

		// The 'data' is a raw integer value of the form #argb.
		val TYPE_INT_COLOR_ARGB4 = 0x1e

		// The 'data' is a raw integer value of the form #rgb.
		val TYPE_INT_COLOR_RGB4 = 0x1f

		// ...end of integer flavors.
		val TYPE_LAST_COLOR_INT = 0x1f

		// ...end of integer flavors.
		val TYPE_LAST_INT = 0x1f

		// Where the unit type information is. This gives us 16 possible
		// types, as defined below.
		val COMPLEX_UNIT_SHIFT = 0

		val COMPLEX_UNIT_MASK = 0xf

		// TYPE_DIMENSION: Value is raw pixels.
		val COMPLEX_UNIT_PX = 0

		// TYPE_DIMENSION: Value is Device Independent Pixels.
		val COMPLEX_UNIT_DIP = 1

		// TYPE_DIMENSION: Value is a Scaled device independent Pixels.
		val COMPLEX_UNIT_SP = 2

		// TYPE_DIMENSION: Value is in points.
		val COMPLEX_UNIT_PT = 3

		// TYPE_DIMENSION: Value is in inches.
		val COMPLEX_UNIT_IN = 4

		// TYPE_DIMENSION: Value is in millimeters.
		val COMPLEX_UNIT_MM = 5

		// TYPE_FRACTION: A basic fraction of the overall size.
		val COMPLEX_UNIT_FRACTION = 0

		// TYPE_FRACTION: A fraction of the parent size.
		val COMPLEX_UNIT_FRACTION_PARENT = 1

		// Where the radix information is, telling where the decimal place
		// appears in the mantissa. This give us 4 possible fixed point
		// representations as defined below.
		val COMPLEX_RADIX_SHIFT = 4

		val COMPLEX_RADIX_MASK = 0x3

		// The mantissa is an integral number -- i.e., 0xnnnnnn.0
		val COMPLEX_RADIX_23P0 = 0

		// The mantissa magnitude is 16 bits -- i.e, 0xnnnn.nn
		val COMPLEX_RADIX_16P7 = 1

		// The mantissa magnitude is 8 bits -- i.e, 0xnn.nnnn
		val COMPLEX_RADIX_8P15 = 2

		// The mantissa magnitude is 0 bits -- i.e, 0x0.nnnnnn
		val COMPLEX_RADIX_0P23 = 3

		// Where the actual value is. This gives us 23 bits of
		// precision. The top bit is the sign.
		val COMPLEX_MANTISSA_SHIFT = 8

		val COMPLEX_MANTISSA_MASK = 0xffffff

		val MANTISSA_MULT: Double = (1.0f / (1 shl COMPLEX_MANTISSA_SHIFT)).toDouble()

		val RADIX_MULTS: DoubleArray = doubleArrayOf(
			1.0f * MANTISSA_MULT,
			1.0f / (1 shl 7) * MANTISSA_MULT,
			1.0f / (1 shl 15) * MANTISSA_MULT,
			1.0f / (1 shl 23) * MANTISSA_MULT,
		)

		/**
		 * String pool flags
		 */
		val SORTED_FLAG = 1

		val UTF8_FLAG = 1 shl 8

		/**
		 * ResTable_type
		 */
		val NO_ENTRY = 0xFFFFFFFF.toInt()

		// If set, the entry is sparse, and encodes both the entry ID and offset into each entry,
		// and a binary search is used to find the key. Only available on platforms >= O.
		// Mark any types that use this with a v26 qualifier to prevent runtime issues on older
		// platforms.
		val FLAG_SPARSE = 0x01

		// If set, the offsets to the entries are encoded in 16-bit, real_offset = offset * 4u
		// An 16-bit offset of 0xffffu means a NO_ENTRY
		val FLAG_OFFSET16 = 0x02

		/**
		 * ResTable_entry
		 */
		// If set, this is a complex entry, holding a set of name/value mappings.
		// It is followed by an array of ResTable_map structures.
		val FLAG_COMPLEX = 0x0001

		// If set, this resource has been declared public, so libraries are allowed to reference it.
		val FLAG_PUBLIC = 0x0002

		// If set, this is a weak resource and may be overridden by strong resources of the same name/type.
		// This is only useful during linking with other resource tables.
		val FLAG_WEAK = 0x0004

		// If set, this is a compact entry with data type and value directly
		// encoded in the entry, see ResTable_entry::compact
		val FLAG_COMPACT = 0x0008

		/**
		 * ResTable_map
		 */
		val ATTR_TYPE = makeResInternal(0)

		// For integral attributes, this is the minimum value it can hold.
		val ATTR_MIN = makeResInternal(1)

		// For integral attributes, this is the maximum value it can hold.
		val ATTR_MAX = makeResInternal(2)

		// Localization of this resource is can be encouraged or required with an aapt flag if this is set
		val ATTR_L10N = makeResInternal(3)

		// for plural support, see android.content.res.PluralRules#attrForQuantity(int)
		val ATTR_OTHER = makeResInternal(4)

		val ATTR_ZERO = makeResInternal(5)

		val ATTR_ONE = makeResInternal(6)

		val ATTR_TWO = makeResInternal(7)

		val ATTR_FEW = makeResInternal(8)

		val ATTR_MANY = makeResInternal(9)

		val PLURALS_MAP: Map<Int, String> = HashMap<Int, String>().apply {
			put(ATTR_OTHER, "other")
			put(ATTR_ZERO, "zero")
			put(ATTR_ONE, "one")
			put(ATTR_TWO, "two")
			put(ATTR_FEW, "few")
			put(ATTR_MANY, "many")
		}

		fun isResInternalId(resid: Int): Boolean = (resid and 0xFFFF0000.toInt()) != 0 && (resid and 0xFF0000) == 0

		private fun makeResInternal(entry: Int): Int = 0x01000000 or (entry and 0xFFFF)

		// Bit mask of allowed types, for use with ATTR_TYPE.
		val ATTR_TYPE_ANY = 0x0000FFFF

		// Attribute holds a references to another resource.
		val ATTR_TYPE_REFERENCE = 1

		// Attribute holds a generic string.
		val ATTR_TYPE_STRING = 1 shl 1

		// Attribute holds an integer value. ATTR_MIN and ATTR_MIN can
		// optionally specify a constrained range of possible integer values.
		val ATTR_TYPE_INTEGER = 1 shl 2

		// Attribute holds a boolean integer.
		val ATTR_TYPE_BOOLEAN = 1 shl 3

		// Attribute holds a color value.
		val ATTR_TYPE_COLOR = 1 shl 4

		// Attribute holds a floating point value.
		val ATTR_TYPE_FLOAT = 1 shl 5

		// Attribute holds a dimension value, such as "20px".
		val ATTR_TYPE_DIMENSION = 1 shl 6

		// Attribute holds a fraction value, such as "20%".
		val ATTR_TYPE_FRACTION = 1 shl 7

		// Attribute holds an enumeration. The enumeration values are
		// supplied as additional entries in the map.
		val ATTR_TYPE_ENUM = 1 shl 16

		// Attribute holds a bitmaks of flags. The flag bit values are
		// supplied as additional entries in the map.
		val ATTR_TYPE_FLAGS = 1 shl 17

		// Enum of localization modes, for use with ATTR_L10N
		val ATTR_L10N_NOT_REQUIRED = 0

		val ATTR_L10N_SUGGESTED = 1
	}
}
