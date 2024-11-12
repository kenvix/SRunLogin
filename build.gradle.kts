import org.gradle.internal.declarativedsl.parsing.main

plugins {
    kotlin("jvm") version "2.0.+"
    kotlin("plugin.serialization") version "2.0.+"
    application
    java
    `java-library`
    id("org.graalvm.buildtools.native") version "0.10.+"
}

group = "com.kenvix"
version = "1.0-SNAPSHOT"

val coroutinesVersion = "1.8.+"
val slf4jVersion = "2.0.+"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.+")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
    implementation("com.github.ajalt.clikt:clikt:5.0.+")
    // https://mvnrepository.com/artifact/org.json/json
    implementation("org.json:json:20240303")

    // https://mvnrepository.com/artifact/org.graalvm.js/js
    implementation("org.graalvm.js:js:24.1.+")
// https://mvnrepository.com/artifact/org.graalvm.js/js-scriptengine
    implementation("org.graalvm.js:js-scriptengine:24.1.+")




    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
//    implementation("com.squareup.okhttp3:okhttp:5.0.+")


    // https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.mqttv5.client
    // implementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.+")

    testImplementation(kotlin("test"))

    // slf4j is too fucking heavy
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
//    implementation("org.slf4j:slf4j-api:$slf4jVersion")
//    implementation("org.slf4j:jul-to-slf4j:$slf4jVersion")
//    implementation("ch.qos.logback:logback-classic:1.5.+")
//
//    // https://mvnrepository.com/artifact/org.slf4j/slf4j-jdk14
//    testImplementation("org.slf4j:slf4j-jdk14:$slf4jVersion")
//    // https://mvnrepository.com/artifact/org.slf4j/slf4j-reload4j
//    testImplementation("org.slf4j:slf4j-reload4j:$slf4jVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.kenvix.nwafunet.Main"
}

graalvmNative {
    toolchainDetection.set(true)

    binaries.all {
        fallback = false
        buildArgs.add("-H:-CheckToolchain")
        resources.autodetect()
    }

    binaries {
        named("main") {
            sharedLibrary = false
            fallback = false
            imageName.set("NwafuNetworkLogin")
            mainClass.set(application.mainClass.get())
            buildArgs.add("-O2")
//            buildArgs.add("--initialize-at-build-time")
            buildArgs.add("-H:+RemoveUnusedSymbols")
//            buildArgs.add("--initialize-at-run-time=jdk.internal.net.http.HttpClientFacade")
//            buildArgs.add("--initialize-at-run-time=jdk.internal.net.http.common.DebugLogger,jdk.internal.net.http.common.Utils,jdk.internal.net.http.common.DebugLogger\$LoggerConfig")
//            buildArgs.add("-H:ReflectionConfigurationFiles=${rootProject.projectDir}/src/main/resources/reflection.json")
//            buildArgs.addAll(
//                 "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
//                 "--initialize-at-build-time=org.slf4j.helpers.NOP_FallbackServiceProvider",
//                 "--initialize-at-build-time=org.slf4j.helpers.SubstituteServiceProvider",
//                 "--initialize-at-build-time=org.slf4j.helpers.SubstituteLoggerFactory",
//                "--initialize-at-build-time=java.util.logging.ConsoleHandler",
//                 "--initialize-at-build-time=java.util.logging.FileHandler"
//            )

            jvmArgs.addAll(
                "-Djava.net.useSystemProxies=true"
            )
        }
    }
}
