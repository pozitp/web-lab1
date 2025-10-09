plugins {
    id("java")
}


group = "ru.pozitp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(files("lib/fastcgi-lib.jar"))
//    implementation("com.google.code.gson:gson:2.13.1")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "ru.pozitp.Main"
    }
}


tasks.test {
    useJUnitPlatform()
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "ru.pozitp.Main"
    }
    from(
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("labwork1.jar")
}
