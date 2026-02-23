import net.minecraftforge.gradle.user.ReobfMappingType
import org.gradle.kotlin.dsl.provideDelegate

plugins {
    java
    idea
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("net.minecraftforge.gradle.forge")
    id("org.spongepowered.mixin")
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://jitpack.io/")
}

version = "5.4.0"
group = "org.unlegitmc.fdp"
setProperty("archivesBaseName", "FDPClient")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val mcVersion = "1.8.9-11.15.1.2318-1.8.9"

minecraft {
    version = mcVersion
    runDir = "run"
    mappings = "stable_22"
    makeObfSourceJar = false
    clientJvmArgs = listOf(
        "-Dfml.coreMods.load=net.ccbluex.liquidbounce.injection.forge.TransformerLoader",
        "-Xmx1024m",
        "-Xms1024m",
        "-Ddev-mode"
    )
}

configurations.runtimeOnly.get().isCanBeResolved = true

dependencies {
    implementation("org.projectlombok:lombok:1.18.26")
    
    implementation("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        exclude(module = "guava")
        exclude(module = "commons-io")
        exclude(module = "gson")
        exclude(module = "launchwrapper")
        exclude(module = "log4j-core")
        exclude(module = "slf4j-api")
    }

    annotationProcessor("org.spongepowered:mixin:0.7.11-SNAPSHOT")

    implementation("me.friwi:jcefmaven:100.0.14.2") {
        exclude(module = "commons-compress")
        exclude(module = "jogl-all")
        exclude(module = "gluegen-rt")
    }
    
    implementation("com.jagrosh:DiscordIPC:0.4")
    
    implementation("com.github.SkidderMC:elixir-2:1.2.4") {
        exclude(module = "kotlin-stdlib")
        exclude(module = "authlib")
    }

    implementation("com.github.zh79325:open-gif:1.0.4") {
        exclude(module = "slf4j-api")
        exclude(module = "logback-core")
        exclude(module = "logback-classic")
        exclude(module = "junit")
    }

    implementation("com.github.UnlegitMC:Astar3d:bec2291cf2")
}

tasks {
    compileJava.get().options.encoding = "UTF-8"

    withType<JavaExec> {
        classpath += sourceSets.main.get().output
        if (name == "runClient") classpath += sourceSets.main.get().runtimeClasspath
    }

    shadowJar {
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("native-binaries/**")
        exclude("LICENSE.txt")
        exclude("META-INF/maven/**")
        exclude("META-INF/versions/**")
        exclude("org/apache/**")
        exclude("org/junit/**")
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("mcversion", mcVersion)

        filesMatching("mcmod.info") {
            expand(
                "version" to project.version,
                "mcversion" to mcVersion
            )
        }

        rename("(.+_at.cfg)", "META-INF/$1")
    }

    val moveResources by registering {
        doLast {
            ant.withGroovyBuilder {
                "move"("file" to "$buildDir/resources/main",
                    "todir" to "$buildDir/classes/java")
            }
        }
        dependsOn(processResources)
    }

    classes.get().dependsOn(moveResources)

    jar {
        manifest.attributes(
            "FMLCorePlugin" to "net.ccbluex.liquidbounce.injection.forge.TransformerLoader",
            "FMLCorePluginContainsFMLMod" to true,
            "ForceLoadAsMod" to true,
            "MixinConfigs" to "mixins.fdpclient.json",
            "ModSide" to "CLIENT",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "TweakOrder" to "0",
            "FMLAT" to "fdpclient_at.cfg"
        )

        enabled = false
    }

    reobfJar.get().dependsOn(processResources)
}

mixin {
    disableRefMapWarning = true
    defaultObfuscationEnv = "searge"
    add(sourceSets.main.get(), "mixins.fdpclient.refmap.json")
}

reobf.maybeCreate("shadowJar").mappingType = ReobfMappingType.SEARGE