plugins {
    id("java-library")
    id("maven-publish")
}

group = "su.nightexpress.excellenteconomy"
version = "2.8.0"

java {
    toolchain {
         languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven/") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nightexpressdev.com/releases")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("maven.modrinth:nightcore:2.16.6")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.black_ixx:playerpoints:3.0.0")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("maven.modrinth:nightcore:2.16.6")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    processResources {
        // Replicates maven <filtering>true</filtering> for plugin.yml
        // Replaces ${version} with the project version.
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)
        filesMatching("*plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar")) {
                classifier = "sources"
            }
            artifactId = project.name
        }
    }
    repositories {
        maven {
                name = "nightexpress"
                url = uri("https://repo.nightexpressdev.com/releases")
                credentials {
                    username = System.getenv("REPOSILITE_USER")
                    password = System.getenv("REPOSILITE_PASSWORD")
                }
        }
    }
}
