package jadx.core.clsp

import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.DecodeException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Objects

/**
 * 类清单（class set）：把一组类的层次/方法信息序列化为 `.jcst` 文件，或反向加载。
 *
 * **用途**：jadx 预先从 android.jar 等库生成 `core.jcst`，反编译时加载它即可快速获得
 * Android SDK 的类信息，无需每次都解析 jar。也支持从当前已解析的 [RootNode] 反向导出。
 *
 * **文件格式**：头部 `jadx-cst` + 版本号 + Android API 级别 + 类表 + 每类的方法表。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 的静态常量/工具方法放入 companion；[makeParentsArray] 被 Java 调用故加 `@JvmStatic`；
 * - 受检异常通过 `@Throws` 保留，Java 调用方（如 jadx-cli 的 ConvertToClsSet）无差异；
 * - 数组元素可能为 null（classpath 信息可能缺失），如实声明为 `Array<ArgType?>?`。
 */
class ClsSet(private val root: RootNode) {

	private var androidApiLevel: Int = 0

	private lateinit var classes: Array<ClspClass>

	/** .jcst 文件中保存类型时使用的类型标签（与原 Java 的序数一致，不能随意调整顺序） */
	private enum class TypeEnum {
		WILDCARD,
		GENERIC,
		GENERIC_TYPE_VARIABLE,
		OUTER_GENERIC,
		OBJECT,
		ARRAY,
		PRIMITIVE,
	}

	/** 从 classpath 资源 `/clst/core.jcst` 加载类清单 */
	@Throws(IOException::class, DecodeException::class)
	fun loadFromClstFile() {
		val startTime = System.currentTimeMillis()
		val input = ClsSet::class.java.getResourceAsStream(CLST_PATH)
		if (input == null) {
			throw JadxRuntimeException("Can't load classpath file: $CLST_PATH")
		}
		input.use { load(it) }
		if (LOG.isDebugEnabled) {
			val time = System.currentTimeMillis() - startTime
			val methodsCount = classes.sumOf { it.methodsMap.size }
			LOG.debug(
				"Clst file loaded in {}ms, android api: {}, classes: {}, methods: {}",
				time,
				androidApiLevel,
				classes.size,
				methodsCount,
			)
		}
	}

	/**
	 * 从当前已解析的类集合反向构建类清单。
	 *
	 * 第一遍：为每个类创建 [ClspClass] 并记录方法；
	 * 第二遍：填充父类/接口数组（需要所有类先就位才能解析引用）。
	 */
	fun loadFrom(root: RootNode) {
		val list = root.getClasses(true)
		val names = HashMap<String, ClspClass>(list.size)
		var k = 0
		for (cls in list) {
			val clsType = cls.classInfo.type
			val clsRawName = clsType.getObject()
			cls.load()

			val source = getClspClassSource(cls)
			val nClass = ClspClass(clsType, k, cls.accessFlags.rawValue(), source)
			if (names.put(clsRawName, nClass) != null) {
				throw JadxRuntimeException("Duplicate class: $clsRawName")
			}
			k++
			nClass.typeParameters = cls.genericTypeParameters
			nClass.setMethods(getMethodsDetails(cls))
		}
		classes = Array(list.size) { i ->
			val cls = list[i]
			val nClass = getCls(cls, names) ?: throw JadxRuntimeException("Missing class: $cls")
			nClass.parents = makeParentsArray(cls)
			nClass
		}
	}

	/** 根据类的输入文件名判断它来自哪个 jar（应用自身应使用 [ClspClassSource.APP]） */
	private fun getClspClassSource(cls: ClassNode): ClspClassSource {
		val inputFileName = checkNotNull(cls.getClsData()).inputFileName
		val idx = inputFileName.indexOf(':')
		val sourceFile = inputFileName.substring(0, idx)
		val source = ClspClassSource.getClspClassSource(sourceFile)
		if (source === ClspClassSource.APP) {
			throw JadxRuntimeException("Unexpected input file: $inputFileName")
		}
		return source
	}

	private fun getMethodsDetails(cls: ClassNode): List<ClspMethod> {
		val methodsList = cls.methods
		val methods = ArrayList<ClspMethod>(methodsList.size)
		for (mth in methodsList) {
			processMethodDetails(mth, methods)
		}
		return methods
	}

	/** 把单个方法转为 [ClspMethod]（跳过 private/synthetic/bridge 方法） */
	private fun processMethodDetails(mth: MethodNode, methods: MutableList<ClspMethod>) {
		val accessFlags: AccessInfo = mth.accessFlags
		if (accessFlags.isPrivate() || accessFlags.isSynthetic() || accessFlags.isBridge()) {
			return
		}
		val clspMethod = ClspMethod(
			mth.methodInfo,
			mth.argTypes,
			mth.returnType,
			mth.typeParameters,
			mth.throws,
			accessFlags.rawValue(),
		)
		methods.add(clspMethod)
	}

	@Throws(IOException::class)
	fun save(path: Path) {
		FileUtils.makeDirsForFile(path)
		val outputName = path.fileName.toString()
		if (outputName.endsWith(CLST_EXTENSION)) {
			BufferedOutputStream(Files.newOutputStream(path)).use { outputStream -> save(outputStream) }
		} else {
			throw JadxRuntimeException("Unknown file format: $outputName")
		}
	}

	@Throws(IOException::class)
	private fun save(output: OutputStream) {
		val out = DataOutputStream(output)
		out.writeBytes(JADX_CLS_SET_HEADER)
		out.writeByte(VERSION)
		out.writeInt(androidApiLevel)

		val names = HashMap<String, ClspClass>(classes.size)
		out.writeInt(classes.size)
		for (cls in classes) {
			out.writeInt(cls.accFlags)
			writeUnsignedByte(out, cls.source.ordinal)
			val clsName = cls.name
			writeString(out, clsName)
			names[clsName] = cls
		}
		for (cls in classes) {
			writeArgTypesArray(out, cls.parents, names)
			writeArgTypesList(out, cls.typeParameters, names)
			val methods = cls.sortedMethodsList
			out.writeShort(methods.size)
			for (method in methods) {
				writeMethod(out, method, names)
			}
		}
		val methodsCount = classes.sumOf { it.methodsMap.size }
		LOG.info("Classes: {}, methods: {}, file size: {} bytes", classes.size, methodsCount, out.size())
	}

	private fun load(input: InputStream) {
		DataInputStream(BufferedInputStream(input)).use { din ->
			val header = ByteArray(JADX_CLS_SET_HEADER.length)
			val readHeaderLength = din.read(header)
			if (readHeaderLength != JADX_CLS_SET_HEADER.length ||
				JADX_CLS_SET_HEADER != String(header, STRING_CHARSET)
			) {
				throw DecodeException("Wrong jadx class set header")
			}
			val version = din.readByte().toInt()
			if (version != VERSION) {
				throw DecodeException("Wrong jadx class set version, got: $version, expect: $VERSION")
			}
			androidApiLevel = din.readInt()
			val clsCount = din.readInt()
			classes = Array(clsCount) { i ->
				val accFlags = din.readInt()
				val clsSource = readClsSource(din)
				val name = readString(din)
				ClspClass(ArgType.`object`(name), i, accFlags, clsSource)
			}
			for (i in 0 until clsCount) {
				val nClass = classes[i]
				val clsInfo = ClassInfo.fromType(root, nClass.clsType)
				nClass.parents = readArgTypesArray(din)
				nClass.typeParameters = readArgTypesList(din)
				nClass.setMethods(readClsMethods(din, clsInfo))
			}
		}
	}

	private fun readClsMethods(din: DataInputStream, clsInfo: ClassInfo): List<ClspMethod> {
		val mCount = din.readShort()
		val methods = ArrayList<ClspMethod>(mCount.toInt())
		for (j in 0 until mCount) {
			methods.add(readMethod(din, clsInfo))
		}
		return methods
	}

	private fun readMethod(din: DataInputStream, clsInfo: ClassInfo): ClspMethod {
		val name = readString(din)
		val argTypes = readArgTypesList(din)
		val retType = checkNotNull(readArgType(din))
		var genericArgTypes = readArgTypesList(din)
		if (genericArgTypes.isEmpty() || genericArgTypes == argTypes) {
			genericArgTypes = argTypes
		}
		var genericRetType = checkNotNull(readArgType(din))
		if (genericRetType == retType) {
			genericRetType = retType
		}
		val typeParameters = readArgTypesList(din)
		val accFlags = din.readInt()
		val throwList = readArgTypesList(din)
		val methodInfo = MethodInfo.fromDetails(root, clsInfo, name, argTypes, retType)
		return ClspMethod(
			methodInfo,
			genericArgTypes,
			genericRetType,
			typeParameters,
			throwList,
			accFlags,
		)
	}

	private fun readArgTypesList(din: DataInputStream): List<ArgType> {
		val count = din.readByte().toInt()
		if (count == 0) {
			return emptyList()
		}
		val list = ArrayList<ArgType>(count)
		for (i in 0 until count) {
			list.add(checkNotNull(readArgType(din)))
		}
		return list
	}

	@Nullable
	private fun readArgTypesArray(din: DataInputStream): Array<ArgType?>? {
		val count = din.readByte().toInt()
		return when (count) {
			-1 -> null
			-2 -> OBJECT_ARGTYPE_ARRAY
			0 -> EMPTY_ARGTYPE_ARRAY
			else -> Array(count) { readArgType(din) }
		}
	}

	@Nullable
	private fun readArgType(din: DataInputStream): ArgType? {
		val ordinal = din.readByte().toInt()
		if (ordinal == -1) {
			return null
		}
		return when (TypeEnum.values()[ordinal]) {
			TypeEnum.WILDCARD -> {
				val bound = ArgType.WildcardBound.getByNum(din.readByte().toInt())
				if (bound === ArgType.WildcardBound.UNBOUND) {
					ArgType.WILDCARD
				} else {
					val objType = readArgType(din)
					ArgType.wildcard(checkNotNull(objType), bound)
				}
			}

			TypeEnum.OUTER_GENERIC -> {
				val outerType = checkNotNull(readArgType(din))
				val innerType = checkNotNull(readArgType(din))
				ArgType.outerGeneric(outerType, innerType)
			}

			TypeEnum.GENERIC -> {
				val clsType = classes[din.readInt()].clsType
				ArgType.generic(clsType, readArgTypesList(din))
			}

			TypeEnum.GENERIC_TYPE_VARIABLE -> {
				val typeVar = readString(din)
				val extendTypes = readArgTypesList(din)
				ArgType.genericType(typeVar, extendTypes)
			}

			TypeEnum.OBJECT -> classes[din.readInt()].clsType

			TypeEnum.ARRAY -> ArgType.array(checkNotNull(readArgType(din)))

			TypeEnum.PRIMITIVE -> {
				val shortName = din.readByte().toInt().toChar()
				ArgType.parse(shortName)
			}

			else -> throw JadxRuntimeException("Unsupported Arg Type: $ordinal")
		}
	}

	val classesCount: Int get() = classes.size

	fun addToMap(nameMap: MutableMap<String, ClspClass>) {
		for (cls in classes) {
			nameMap[cls.name] = cls
		}
	}

	fun getAndroidApiLevel(): Int = androidApiLevel

	fun setAndroidApiLevel(androidApiLevel: Int) {
		this.androidApiLevel = androidApiLevel
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ClsSet::class.java)

		private const val CLST_EXTENSION = ".jcst"
		private const val CLST_FILENAME = "core$CLST_EXTENSION"
		private const val CLST_PATH = "/clst/$CLST_FILENAME"
		private const val JADX_CLS_SET_HEADER = "jadx-cst"
		private const val VERSION = 5
		private val STRING_CHARSET: Charset = Charsets.US_ASCII

		/** 空父类型数组（如 java.lang.Object） */
		private val EMPTY_ARGTYPE_ARRAY: Array<ArgType?> = arrayOfNulls(0)

		/** 仅含 java.lang.Object 的父类型数组（普通类直接继承 Object 且无接口时复用） */
		private val OBJECT_ARGTYPE_ARRAY: Array<ArgType?> = arrayOf(ArgType.OBJECT)

		/** 由类节点构建“父类 + 接口”数组；java.lang.Object 返回空数组 */
		fun makeParentsArray(cls: ClassNode): Array<ArgType?> {
			val superClass = cls.superClass
			if (superClass == null) {
				// cls 就是 java.lang.Object
				return EMPTY_ARGTYPE_ARRAY
			}
			val interfacesCount = cls.interfaces.size
			if (interfacesCount == 0 && superClass === ArgType.OBJECT) {
				return OBJECT_ARGTYPE_ARRAY
			}
			val parents = arrayOfNulls<ArgType>(1 + interfacesCount)
			parents[0] = superClass
			var k = 1
			for (iface in cls.interfaces) {
				parents[k] = iface
				k++
			}
			return parents
		}

		private fun getCls(cls: ClassNode, names: Map<String, ClspClass>): ClspClass? = getCls(cls.rawName, names)

		private fun getCls(clsType: ArgType, names: Map<String, ClspClass>): ClspClass? = getCls(clsType.getObject(), names)

		private fun getCls(fullName: String, names: Map<String, ClspClass>): ClspClass? {
			val cls = names[fullName]
			if (cls == null) {
				LOG.debug("Class not found: {}", fullName)
			}
			return cls
		}

		private fun writeMethod(out: DataOutputStream, method: ClspMethod, names: Map<String, ClspClass>) {
			val methodInfo = method.methodInfo
			writeString(out, methodInfo.name)
			writeArgTypesList(out, methodInfo.argumentsTypes, names)
			writeArgType(out, methodInfo.returnType, names)

			writeArgTypesList(out, if (method.containsGenericArgs()) method.argTypes else emptyList(), names)
			writeArgType(out, method.returnType, names)
			writeArgTypesList(out, method.typeParameters, names)
			out.writeInt(method.rawAccessFlags)
			writeArgTypesList(out, method.throws, names)
		}

		private fun writeArgTypesList(out: DataOutputStream, list: List<ArgType>, names: Map<String, ClspClass>) {
			val size = list.size
			writeUnsignedByte(out, size)
			if (size != 0) {
				for (type in list) {
					writeArgType(out, type, names)
				}
			}
		}

		private fun writeArgTypesArray(out: DataOutputStream, arr: Array<ArgType?>?, names: Map<String, ClspClass>) {
			if (arr == null) {
				out.writeByte(-1)
				return
			}
			if (arr === OBJECT_ARGTYPE_ARRAY) {
				out.writeByte(-2)
				return
			}
			val size = arr.size
			out.writeByte(size)
			if (size != 0) {
				for (type in arr) {
					writeArgType(out, type, names)
				}
			}
		}

		private fun writeArgType(out: DataOutputStream, argType: ArgType?, names: Map<String, ClspClass>) {
			if (argType == null) {
				out.writeByte(-1)
				return
			}
			if (argType.isPrimitive()) {
				out.writeByte(TypeEnum.PRIMITIVE.ordinal)
				out.writeByte(checkNotNull(argType.getPrimitiveType()).shortName[0].code)
			} else if (argType.getOuterType() != null) {
				out.writeByte(TypeEnum.OUTER_GENERIC.ordinal)
				writeArgType(out, argType.getOuterType(), names)
				writeArgType(out, argType.getInnerType(), names)
			} else if (argType.getWildcardType() != null) {
				out.writeByte(TypeEnum.WILDCARD.ordinal)
				val bound = checkNotNull(argType.getWildcardBound())
				out.writeByte(bound.num)
				if (bound !== ArgType.WildcardBound.UNBOUND) {
					writeArgType(out, argType.getWildcardType(), names)
				}
			} else if (argType.isGeneric()) {
				out.writeByte(TypeEnum.GENERIC.ordinal)
				out.writeInt(checkNotNull(getCls(argType, names)).id)
				writeArgTypesList(out, checkNotNull(argType.getGenericTypes()), names)
			} else if (argType.isGenericType()) {
				out.writeByte(TypeEnum.GENERIC_TYPE_VARIABLE.ordinal)
				writeString(out, argType.getObject())
				writeArgTypesList(out, argType.getExtendTypes(), names)
			} else if (argType.isObject()) {
				out.writeByte(TypeEnum.OBJECT.ordinal)
				out.writeInt(checkNotNull(getCls(argType, names)).id)
			} else if (argType.isArray()) {
				out.writeByte(TypeEnum.ARRAY.ordinal)
				writeArgType(out, argType.getArrayElement(), names)
			} else {
				throw JadxRuntimeException("Cannot save type: $argType")
			}
		}

		private fun writeString(out: DataOutputStream, name: String) {
			val bytes = name.toByteArray(STRING_CHARSET)
			val len = bytes.size
			if (len >= 0xFF) {
				throw JadxRuntimeException("String is too long: $name")
			}
			writeUnsignedByte(out, bytes.size)
			out.write(bytes)
		}

		private fun readString(din: DataInputStream): String {
			val len = readUnsignedByte(din)
			return readString(din, len)
		}

		private fun readString(din: DataInputStream, len: Int): String {
			val bytes = ByteArray(len)
			var count = din.read(bytes)
			while (count != len) {
				val res = din.read(bytes, count, len - count)
				if (res == -1) {
					throw IOException("String read error")
				} else {
					count += res
				}
			}
			return String(bytes, STRING_CHARSET)
		}

		private fun writeUnsignedByte(out: DataOutputStream, value: Int) {
			if (value < 0 || value >= 0xFF) {
				throw JadxRuntimeException("Unsigned byte value is too big: $value")
			}
			out.writeByte(value)
		}

		private fun readUnsignedByte(din: DataInputStream): Int = din.readByte().toInt() and 0xFF

		private fun readClsSource(din: DataInputStream): ClspClassSource {
			val source = readUnsignedByte(din)
			val clspClassSources = ClspClassSource.values()
			if (source < 0 || source > clspClassSources.size) {
				throw DecodeException("Wrong jadx source identifier: $source")
			}
			return clspClassSources[source]
		}
	}
}
