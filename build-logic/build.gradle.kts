plugins {
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("net.fabricmc:fabric-filament:0.10.1")
    implementation("cuchaz:enigma:2.5.3")
    implementation("cuchaz:enigma-cli:2.5.3")
    implementation("net.fabricmc:mapping-io:0.7.1")
    implementation("net.fabricmc:tiny-remapper:0.11.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("net.fabricmc:stitch:0.4.6+build.74")
    implementation("commons-io:commons-io:2.11.0")
}

gradlePlugin {
    plugins.register("potential-lamp") {
        id = "potential-lamp"
        implementationClass = "juuxel.potentiallamp.gradle.PotentialLampPlugin"
    }
}
