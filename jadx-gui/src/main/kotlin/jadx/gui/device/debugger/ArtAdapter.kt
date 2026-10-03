package jadx.gui.device.debugger

/**
 * Android ART 虚拟机差异适配器。
 *
 * **做什么**：不同 Android 版本对 smali 寄存器编号与 JDWP 运行时寄存器编号的
 * 对应关系不同，这里按版本返回对应的 [IArtAdapter] 实现：
 * - Android 8（Oreo）及以下：运行时寄存器编号做了「本地寄存器左移」处理；
 * - Android 9（Pie）及以上：smali 编号与运行时编号一致。
 *
 * **为什么是普通 class**：原 Java 只是一个静态工厂容器，内部是接口 + 两个实现类。
 */
class ArtAdapter {

	/**
	 * 版本差异适配接口。
	 */
	interface IArtAdapter {
		/**
		 * 把 smali 寄存器编号换算为 JDWP 运行时寄存器编号。
		 *
		 * @param smaliNum   smali 中的寄存器编号
		 * @param regCount   方法寄存器总数
		 * @param paramStart 参数寄存器的起始编号
		 */
		fun getRuntimeRegNum(smaliNum: Int, regCount: Int, paramStart: Int): Int

		/** 读到的对象引用为空时，是否直接视为 `null`。 */
		fun readNullObject(): Boolean

		/** 对象引用为空时应展示的类型描述。 */
		fun typeForNull(): String
	}

	companion object {
		/**
		 * 按 Android 版本选择适配器。
		 *
		 * @param androidReleaseVer Android 主版本号（如 8、9、10）
		 */
		@JvmStatic
		fun getAdapter(androidReleaseVer: Int): IArtAdapter = if (androidReleaseVer <= 8) AndroidOreoAndBelow() else AndroidPieAndAbove()
	}

	/** Android 8 及以下的适配实现：运行时寄存器编号需要左移本地寄存器数量。 */
	class AndroidOreoAndBelow : IArtAdapter {
		override fun getRuntimeRegNum(smaliNum: Int, regCount: Int, paramStart: Int): Int {
			val localRegCount = regCount - paramStart
			return (smaliNum + localRegCount) % regCount
		}

		override fun readNullObject(): Boolean = true

		override fun typeForNull(): String = ""
	}

	/** Android 9 及以上的适配实现：编号一致，空引用显示为 `zero value`。 */
	class AndroidPieAndAbove : IArtAdapter {
		override fun getRuntimeRegNum(smaliNum: Int, regCount: Int, paramStart: Int): Int = smaliNum

		override fun readNullObject(): Boolean = false

		override fun typeForNull(): String = "zero value"
	}
}
