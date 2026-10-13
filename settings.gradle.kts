rootProject.name = "megalodonte-video-editor"
val scriptApp = file("../../framework/megalodonte-declarative/megalodonte-script-app")
if (providers.gradleProperty("useLocalSources").orNull != "false" && scriptApp.isDirectory) {
    includeBuild(scriptApp)
}
