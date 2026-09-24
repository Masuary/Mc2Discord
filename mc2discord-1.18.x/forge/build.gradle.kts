import groovy.json.JsonSlurper
import java.util.zip.ZipFile

plugins {
    id("net.minecraftforge.gradle")
    id("org.spongepowered.mixin")
    id("com.github.johnrengelman.shadow")
}

val sharedProperties = readProperties(file("../../shared.properties"))

base {
    archivesName.set("${sharedProperties["modId"]}-forge-${rootProject.extra["minecraftDisplayVersion"]}")
}

mixin {
    add(sourceSets.main.get(), "${sharedProperties["modId"]}.refmap.json")

    config("${sharedProperties["modId"]}.mixins.json")
    config("${sharedProperties["modId"]}.forge.mixins.json")
}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

val shadowMinecraftLibrary: Configuration by configurations.creating
val shadowCompileOnly: Configuration by configurations.creating
configurations.minecraftLibrary.get().extendsFrom(shadowMinecraftLibrary)
configurations.compileOnly.get().extendsFrom(shadowCompileOnly)
dependencies {
    minecraft("net.minecraftforge:forge:${rootProject.extra["minecraftVersion"]}-${rootProject.extra["forgeVersion"]}")
    shadowCompileOnly(project(":common"))
    shadowMinecraftLibrary(project(":mc2discord-core"))

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

// Fix for running Discord4J on Forge in dev. Exclude forge netty and use the one from Discord4J
configurations.minecraft {
    exclude(group = "io.netty")
}


minecraft {
    mappings("official", "${rootProject.extra["minecraftVersion"]}")

    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        create("server") {
            taskName("Server")
            workingDirectory(project.file("run"))
            ideaModule("${rootProject.name}.${project.name}.main")
            arg("nogui")
            singleInstance(true)
            mods {
                create("${sharedProperties["modId"]}") {
                    source(sourceSets.main.get())
                    source(project(":common").sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

tasks {
    compileJava {
        source(project(":common").sourceSets.main.get().allSource)
        // MixinGradle deletes AP outputs before compilation, even when incremental javac processes no mixins.
        options.isIncremental = false
        outputs.files(
            layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json"),
            layout.buildDirectory.file("tmp/compileJava/${sharedProperties["modId"]}.refmap.json"),
            layout.buildDirectory.file("tmp/compileJava/compileJava-mappings.tsrg")
        )
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
    }

    jar {
        archiveClassifier.set("slim")
        finalizedBy("reobfJar")
    }

    shadowJar {
        archiveClassifier.set("")
        configurations = listOf(shadowCompileOnly, shadowMinecraftLibrary)
        val relocateLocation = "${sharedProperties["modGroup"]}.shadow"
        val relocations = listOf(
            "io.netty",
            "reactor",
            "discord4j",
            "org.reactivestreams",
            "org.checkerframework",
            "com.iwebpp.crypto",
            "com.google.errorprone",
            "com.github.benmanes.caffeine",
            "com.fasterxml.jackson",
            "com.discord4j.fsm",
            "com.austinv11.servicer",
            "com.vdurmont.emoji",
            "fr.denisd3d.config4j",
            "org.apache.commons.collections4",
            "org.immutables.encode",
            "org.json",
            "com.electronwill.nightconfig"
        )
        relocations.forEach {
            relocate(it, "$relocateLocation.$it")
        }
        exclude("META-INF/services/**") // Fix compatibility with geckolib
    }

    assemble {
        dependsOn(shadowJar)
    }

    reobf {
        create("shadowJar")
    }

    val verifyForgeMixins by registering {
        group = "verification"
        description = "Checks that the production Forge JAR contains the required mixin mappings."
        dependsOn("reobfShadowJar")
        val productionJar = shadowJar.flatMap { it.archiveFile }
        inputs.file(productionJar)

        doLast {
            ZipFile(productionJar.get().asFile).use { archive ->
                val referenceMapName = "${sharedProperties["modId"]}.refmap.json"
                val referenceMapEntry = archive.getEntry(referenceMapName)
                    ?: error("Production Forge JAR is missing $referenceMapName")
                val referenceMap = archive.getInputStream(referenceMapEntry).reader().use {
                    JsonSlurper().parse(it) as Map<*, *>
                }
                val mappings = referenceMap["mappings"] as? Map<*, *>
                    ?: error("$referenceMapName has no mappings")
                val requiredTargets = mapOf(
                    "PlayerListMixin" to listOf("broadcastMessage(", "canPlayerLogin("),
                    "CommandFunctionCommandEntryMixin" to listOf("execute(")
                )
                requiredTargets.forEach { (mixin, targets) ->
                    val className = "fr/denisd3d/mc2discord/minecraft/mixin/$mixin"
                    val classMappings = mappings[className] as? Map<*, *>
                        ?: error("$referenceMapName is missing $mixin mappings")
                    targets.forEach { target ->
                        check(classMappings.entries.any {
                            it.key.toString().startsWith(target) && it.value.toString().contains(";m_")
                        }) { "$referenceMapName is missing the production mapping for $mixin.$target" }
                    }
                }
            }
        }
    }

    check {
        dependsOn(verifyForgeMixins)
    }

    // Workaround for SpongePowered/MixinGradle#38
    afterEvaluate {
        getByName("configureReobfTaskForReobfShadowJar").mustRunAfter("compileJava")
        getByName("configureReobfTaskForReobfJar").mustRunAfter("compileJava")
    }
}
