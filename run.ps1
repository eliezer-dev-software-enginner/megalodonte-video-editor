param([switch]$Test)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source) }
    else {
        $jdk = Get-ChildItem -LiteralPath "$env:ProgramFiles/Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk-25*' } | Sort-Object Name -Descending | Select-Object -First 1
        if (-not $jdk) { throw 'Instale o JDK 25 ou configure JAVA_HOME para ele.' }
        $env:JAVA_HOME = $jdk.FullName
    }
}
Push-Location $PSScriptRoot
try {
    if ($Test) { & ./gradlew.bat test :megalodonte-jsonc-ui:test :megalodonte-script-app:test --console=plain }
    else { & ./gradlew.bat run --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "Gradle encerrou com código $LASTEXITCODE" }
} finally { Pop-Location }
