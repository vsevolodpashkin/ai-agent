plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter:4.1.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.1.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator:4.1.1")
    implementation("dev.langchain4j:langchain4j:1.0.0-beta1")
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta1")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:1.0.0-beta1")
    implementation("dev.langchain4j:langchain4j-open-ai-spring-boot-starter:1.0.0-beta1")
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.1.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.example.ApplicationKt")
}
