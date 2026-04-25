import org.gradle.api.internal.plugins.MainClass

description = "library-api"



plugins {
    `java-library`
    id("org.openapi.generator") version "7.21.0"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc:4.1.0-M4")
    implementation("jakarta.validation:jakarta.validation-api:4.0.0-M1")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.46")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/library-api/src/main/resources/openapi/library-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated")
    apiPackage.set("ru.athena.library_demo.api.generated")
    modelPackage.set("ru.athena.library_demo.api.generated.model")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "useTags" to "true",
        "dateLibrary" to "java8",
        "openApiNullable" to "false",
        "skipDefaultInterface" to "false"
    ))
}

tasks.openApiGenerate {
    doLast {
        val generatedJava = file("${layout.buildDirectory.get()}/generated/src/main/java")
        fileTree(generatedJava).matching { include("**/*.java") }.forEach { file ->
            val original = file.readText()
            val patched = original
                .replace(Regex("(?m)^import org\\.springframework\\.lang\\.Nullable;\\r?\\n"), "")
                .replace("@Nullable ", "")
            if (patched != original) file.writeText(patched)
        }
    }
}


sourceSets {
    main {
        java.srcDir("${layout.buildDirectory.get()}/generated/src/main/java")
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
    dependsOn(tasks.openApiGenerate)
}
