plugins {
	id("kadx-java")
	id("java-library")
	id("maven-publish")
	id("signing")
}

val kadxVersion = rootProject.extra["kadxVersion"] as String

group = "io.github.shakarover"
version = kadxVersion

java {
	withJavadocJar()
	withSourcesJar()
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = project.name
			from(components["java"])
			versionMapping {
				usage("java-api") {
					fromResolutionOf("runtimeClasspath")
				}
				usage("java-runtime") {
					fromResolutionResult()
				}
			}
			pom {
				name.set(project.name)
				description.set(project.description ?: "Dex to Java decompiler")
				url.set("https://github.com/ShakaRover/kadx")
				licenses {
					license {
						name.set("The Apache License, Version 2.0")
						url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
					}
				}
				developers {
					developer {
						id.set("ShakaRover")
						name.set("ShakaRover")
						email.set(project.findProperty("libEmail") as String? ?: "" )
						url.set("https://github.com/ShakaRover")
					}
				}
				scm {
					connection.set("scm:git:git://github.com/ShakaRover/kadx.git")
					developerConnection.set("scm:git:ssh://github.com:ShakaRover/kadx.git")
					url.set("https://github.com/ShakaRover/kadx")
				}
			}
		}
	}
	repositories {
		maven {
			val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
			val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
			url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
			credentials {
				username = project.findProperty("ossrhUser") as String? ?: ""
				password = project.findProperty("ossrhPassword") as String? ?: ""
			}
		}
	}
}

signing {
	isRequired = gradle.taskGraph.hasTask("publish")
	sign(publishing.publications["mavenJava"])
}


tasks.javadoc {
	val stdOptions = options as StandardJavadocDocletOptions
	stdOptions.addBooleanOption("html5", true)
	// disable 'missing' warnings
	stdOptions.addStringOption("Xdoclint:all,-missing", "-quiet")
}
