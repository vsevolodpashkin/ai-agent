plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ядро LangChain4j
    implementation("dev.langchain4j:langchain4j:1.0.0-beta1")
    // Интеграция с OpenAI
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.1.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
mainClass.set("MainKt")
}
