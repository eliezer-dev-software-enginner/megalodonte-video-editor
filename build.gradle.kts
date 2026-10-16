plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}
group = "megalodonte"
version = "0.1.0"
repositories { mavenLocal(); mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
javafx { version = "25.0.1"; modules("javafx.controls", "javafx.graphics", "javafx.media") }
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("megalodonte:megalodonte-script-app:0.1.0")
    testImplementation("megalodonte:megalodonte-theme:1.0.0-beta")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass.set("megalodonte.script.ScriptLauncher")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
distributions {
    main {
        contents {
            from("tools") { into("tools") }
        }
    }
}
tasks.test { useJUnitPlatform(); jvmArgs("--enable-native-access=ALL-UNNAMED") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

// Explicit opt-in: this task calls the real, billable OpenAI Images API.
tasks.register<JavaExec>("imageIntegrationSmoke") {
    group = "verification"
    description = "Gera imagens reais para um roteiro curto e salva um projeto do editor"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("megalodonte.cliente.ImageIntegrationSmoke")
    args(providers.gradleProperty("scriptFile").orElse("examples/roteiro-curto.txt").get(),
         providers.gradleProperty("projectOutput").orElse("build/integration/openai-curto/projeto.mvideo.json").get())
}

tasks.test {
    val liveProject = providers.gradleProperty("liveProject").orElse("").get()
    systemProperty("megalodonte.live.project", liveProject)
    if (liveProject.isNotBlank()) outputs.upToDateWhen { false }
}
tasks.named<JavaExec>("run") {
    providers.gradleProperty("initialProject").orNull?.let { systemProperty("megalodonte.editor.initialProject", file(it).absolutePath) }
}
