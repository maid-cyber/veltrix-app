import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

// broj builda sa GitHuba: po njemu aplikacija zna da postoji novija verzija
val build = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
version = "1.0.$build"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

kotlin { jvmToolchain(17) }

// verzija i repozitorij dostupni u kodu
val genDir = layout.buildDirectory.dir("generated/buildinfo")
val genInfo by tasks.registering {
    val out = genDir
    inputs.property("build", build)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/veltrix/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.veltrix.desktop\n\nobject BuildInfo {\n    const val BUILD = $build\n    const val VERSION = \"1.0.$build\"\n    const val UPDATE_REPO = \"maid-cyber/veltrix-app\"\n}\n")
    }
}
sourceSets["main"].kotlin.srcDir(genDir)
tasks.named("compileKotlin") { dependsOn(genInfo) }

compose.desktop {
    application {
        mainClass = "com.veltrix.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "VeltrixVPN"
            packageVersion = "1.0.$build"
            vendor = "Veltrix"
            description = "Veltrix VPN"
            // sing-box.exe ide uz aplikaciju (resources/windows)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.naming", "jdk.crypto.ec", "java.net.http")
            windows {
                iconFile.set(project.file("packaging/veltrix.ico"))
            }
        }
    }
}

// screenshotovi ekrana (bez prozora) za provjeru izgleda
tasks.register<JavaExec>("shots") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.veltrix.desktop.ShotsKt")
    args(layout.buildDirectory.dir("shots").get().asFile.path)
}
