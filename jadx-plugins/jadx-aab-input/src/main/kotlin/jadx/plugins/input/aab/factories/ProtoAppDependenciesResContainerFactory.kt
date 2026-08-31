package jadx.plugins.input.aab.factories

import com.android.bundle.AppDependenciesOuterClass
import jadx.api.ResourceFile
import jadx.api.impl.SimpleCodeInfo
import jadx.api.plugins.resources.IResContainerFactory
import jadx.core.xmlgen.ResContainer
import java.io.InputStream

/**
 * 为 BUNDLE-METADATA/.../dependencies.pb 创建 ResContainer（内容即 protobuf 的 toString）。
 */
public class ProtoAppDependenciesResContainerFactory : IResContainerFactory {

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		if (!resFile.getOriginalName().endsWith("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")) {
			return null
		}

		val appDependencies = AppDependenciesOuterClass.AppDependencies.parseFrom(inputStream)
		val content = SimpleCodeInfo(appDependencies.toString())
		return ResContainer.textResource(resFile.getDeobfName(), content)
	}
}
