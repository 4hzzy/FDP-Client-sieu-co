pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://jitpack.io/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "net.minecraftforge.gradle.forge" -> useModule("com.github.gatooooooo:ForgeGradle:f2c5bb338e")
                "org.spongepowered.mixin" -> useModule("com.github.xcfrg:mixingradle:ae2a80e")
            }
        }
    }
}

rootProject.name = "FDPClient"