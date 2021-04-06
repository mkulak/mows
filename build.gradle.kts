import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("multiplatform") version "1.4.32"
    kotlin("plugin.serialization") version "1.4.32"
    application
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "me.xap4o"
version = "1.0"

repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlinx") }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        withJava()
    }
    js(LEGACY) {
        browser {
            binaries.executable()
            webpackTask {
                cssSupport.enabled = true
            }
            runTask {
                cssSupport.enabled = true
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.vertx:vertx-web:4.0.0")
                implementation("io.vertx:vertx-web-client:4.0.0")
                implementation("io.vertx:vertx-lang-kotlin:4.0.0")
                implementation("io.vertx:vertx-lang-kotlin-coroutines:4.0.0")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.2")
                implementation("com.fasterxml.jackson.module:jackson-modules-java8:2.11.2")
                implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.2")
                implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.2")

                implementation("org.apache.logging.log4j:log4j-core:2.13.3")
                implementation("org.apache.logging.log4j:log4j-api:2.13.3")
                implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.13.3")
                implementation("org.slf4j:slf4j-api:1.7.30")
                implementation("io.github.microutils:kotlin-logging:1.8.3")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
//                testImplementation(kotlin("test-junit5"))
//                testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
//                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
                implementation("org.assertj:assertj-core:3.16.1")
                implementation("com.jayway.jsonpath:json-path:2.4.0")
            }
        }
        val jsMain by getting {
            dependencies {
//                implementation("org.jetbrains:kotlin-react:16.13.1-pre.113-kotlin-1.4.0")
//                implementation("org.jetbrains:kotlin-react-dom:16.13.1-pre.113-kotlin-1.4.0")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

application {
    mainClassName = "server.MainKt"
}

tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
//    outputFileName = "output.js"
}

tasks.getByName<Jar>("jvmJar") {
    dependsOn(tasks.getByName("jsBrowserProductionWebpack"))
    val jsBrowserProductionWebpack = tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack")
    from(File(jsBrowserProductionWebpack.destinationDirectory, jsBrowserProductionWebpack.outputFileName))
}

tasks.getByName<JavaExec>("run") {
    dependsOn(tasks.getByName<Jar>("jvmJar"))
    classpath(tasks.getByName<Jar>("jvmJar"))
}

val buildJs by tasks.registering(Copy::class) {
    dependsOn(tasks.getByName<Task>("jsBrowserDevelopmentWebpack"))
    from("src/jvmMain/resources", "build/distributions")
    into("output")
}

tasks.getByName<ShadowJar>("shadowJar") {
    archiveBaseName.set("wonder")
    archiveClassifier.set("")
    archiveVersion.set("")
}
