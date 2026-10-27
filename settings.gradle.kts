rootProject.name = "megalodonte-video-editor"
val core = file("../../framework/libraries/megalodonte-core")
if (providers.gradleProperty("useLocalSources").orNull != "false" && core.isDirectory) includeBuild(core)
val scriptApp = file("../../framework/megalodonte-declarative/megalodonte-script-app")
if (providers.gradleProperty("useLocalSources").orNull != "false" && scriptApp.isDirectory) {
    includeBuild(scriptApp)
}
