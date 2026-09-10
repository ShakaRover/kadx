package jadx.api.plugins.input.data.attributes

/**
 * 常驻属性基类：固定 [keepLoaded] = true，节点卸载时不释放。
 *
 **背景**：反编译过程中被频繁访问的属性（如 [jadx.api.plugins.input.data.annotations.EncodedValue]）
 * 继承本类避免 unload/reload 开销；`final` 修饰与原 Java 一致，子类不可改写。
 */
public abstract class PinnedAttribute : IJadxAttribute {

	final override fun keepLoaded(): Boolean = true
}
