import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Linux aplikacija — isti Compose/Kotlin kod kao Windows (dijeli ../windows/src),
// samo drugačiji VPN pokretač (pkexec), ikona i pakovanje (.deb).

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

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

// dijelimo sav izvorni kod i resurse sa Windows modulom (jedini OS-specifični dijelovi su u Core.kt/ConfigBuilder.kt, uslovno)
sourceSets["main"].kotlin.srcDir("../windows/src/main/kotlin")
sourceSets["main"].resources.srcDir("../windows/src/main/resources")

// verzija, panel i repozitorij dostupni u kodu (isto kao Windows)
val genDir = layout.buildDirectory.dir("generated/buildinfo")
val genInfo by tasks.registering {
    val out = genDir
    inputs.property("build", build)
    inputs.property("panel", System.getenv("PANEL_URL") ?: "")
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/veltrix/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        val panel = System.getenv("PANEL_URL")?.takeIf { it.isNotBlank() } ?: "http://178.104.237.74"
        f.writeText("package com.veltrix.desktop\n\nobject BuildInfo {\n    const val BUILD = $build\n    const val VERSION = \"1.0.$build\"\n    const val UPDATE_REPO = \"maid-cyber/veltrix-app\"\n    const val PANEL_URL = \"$panel\"\n}\n")
    }
}
sourceSets["main"].kotlin.srcDir(genDir)
tasks.named("compileKotlin") { dependsOn(genInfo) }

compose.desktop {
    application {
        mainClass = "com.veltrix.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "VeltrixVPN"
            packageVersion = "1.0.$build"
            vendor = "AdiLink"
            description = "AdiLink VPN"
            // sing-box (Linux binarni) ide uz aplikaciju (resources/linux-x64/sing-box)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.naming", "jdk.crypto.ec", "java.net.http")
            linux {
                iconFile.set(project.file("packaging/veltrix.png"))
                packageName = "adilink-vpn"
                debMaintainer = "podrska@adilinkvpn.com"
                menuGroup = "Network"
                shortcut = true
                // Napomena: TUN tunel se u runtime-u podiže preko pkexec (polkit).
                // Polkit je prisutan na praktično svim desktop Linux instalacijama.
            }
        }
    }
}
