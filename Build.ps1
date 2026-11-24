param(
    [string]$BaseJar,
    [string]$OriginalJar,
    [string]$OutputJar,
    [string]$Profile
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (!$BaseJar) { $BaseJar = Join-Path $root 'HealthIndicators-2.5.7-1.20.1-players-only-clearance2.jar' }
if (!$OutputJar) { $OutputJar = Join-Path $root 'HealthIndicators-2.5.7-1.20.1-visibility.jar' }
if (!$Profile) { $Profile = Join-Path $env:APPDATA 'ModrinthApp/profiles/1.20.1' }
if (!$OriginalJar -and (Get-FileHash -LiteralPath $BaseJar).Hash -ne '8D9CD9F61A233ED107F33A4BA258A013CC25BBCA5E426A082917CCE6305D9A6F') {
    throw 'The patch requires the 1.20.1 clearance2 base JAR.'
}

function Extract-Jars([string]$from, [string]$into, [string]$pattern) {
    $zip = [IO.Compression.ZipFile]::OpenRead($from)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like $pattern) {
                $target = Join-Path $into $entry.Name
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target)
                $target
            }
        }
    } finally { $zip.Dispose() }
}

$work = Join-Path $env:TEMP ('HealthBuild-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$toolsDir = Join-Path $work 'tools'
New-Item -ItemType Directory -Path $work,$classes,$toolsDir | Out-Null
$meta = Join-Path $env:APPDATA 'ModrinthApp/meta'
$version = '1.20.1-0.20.1'
$manifest = Get-Content -LiteralPath (Join-Path $meta "versions/$version/$version.json") -Raw | ConvertFrom-Json
$libs = foreach ($lib in $manifest.libraries) {
    $path = $lib.downloads.artifact.path
    if (!$path) {
        $parts = $lib.name.Split(':')
        $path = "$($parts[0].Replace('.','/'))/$($parts[1])/$($parts[2])/$($parts[1])-$($parts[2]).jar"
    }
    $full = Join-Path $meta "libraries/$path"
    if (Test-Path -LiteralPath $full) { $full }
}
$mcCache = Join-Path $env:USERPROFILE '.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary'
$mc = Get-ChildItem -LiteralPath $mcCache -File -Recurse -Filter '*1.20.1+build.10-v2.jar' | Select-Object -First 1
if (!$mc) { throw 'Required intermediary Minecraft 1.20.1 build.10 JAR is missing.' }
$loader = $libs | Where-Object { $_ -like '*fabric-loader-0.20.1.jar' } | Select-Object -First 1
$mods = Get-ChildItem -LiteralPath (Join-Path $Profile 'mods') -File
$fabric = $mods | Where-Object Name -like 'fabric-api-*.jar' | Select-Object -First 1
$yacl = $mods | Where-Object Name -match '(?i)yet.another.config.lib|yet_another_config_lib|yacl|yetanotherconfiglib' | Select-Object -First 1
$extras = @(Extract-Jars $loader $work 'META-INF/jars/mixinextras-*.jar')
$api = @(Extract-Jars $fabric.FullName $work 'META-INF/jars/*.jar')
$asm = @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.ow2.asm') -Recurse -File -Filter '*-9.8.jar' | ForEach-Object FullName)
if ($OriginalJar) {
    if ((Get-FileHash -LiteralPath $OriginalJar).Hash -ne '0868FB6D660AFB558478C531C1AF598C43AD0678A92DA280C2A414D8BB57A90F') {
        throw 'An unmodified Health Indicators 2.5.7 JAR for Minecraft 1.20.1 is required.'
    }
    $BaseJar = Join-Path $work 'clearance2.jar'
    java -cp ($asm -join ';') (Join-Path $root 'tools/PlayerOnlyHealthIndicatorsTwoBlocks.java') $OriginalJar $BaseJar
    if ($LASTEXITCODE -or !(Test-Path -LiteralPath $BaseJar)) { throw 'Base patch failed.' }
}
$classpath = (@($mc.FullName,$BaseJar,$yacl.FullName) + $extras + $api + $asm + $libs) -join ';'
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$toolFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'tools') -Filter '*.java' | ForEach-Object FullName)
# An argument file avoids the Windows command-line size limit.
$compileArgs = @('-proc:none','--release','17','-encoding','UTF-8','-cp',$classpath,'-d',$classes) + $sourceFiles
$compileFile = Join-Path $work 'compile.args'
Set-Content -LiteralPath $compileFile -Value ($compileArgs | ForEach-Object { '"' + $_.Replace('\','/') + '"' }) -Encoding utf8NoBOM
javac "@$compileFile"
if ($LASTEXITCODE) { throw 'Mod compilation failed' }
javac --release 17 -encoding UTF-8 -cp ($asm -join ';') -d $toolsDir $toolFiles
if ($LASTEXITCODE) { throw 'Build tool compilation failed' }
$staged = Join-Path $work 'candidate.jar'
java -cp ((@($toolsDir) + $asm + $libs) -join ';') BuildSafeHealth $BaseJar $classes $staged (Join-Path $root 'LICENSE')
if ($LASTEXITCODE -or !(Test-Path -LiteralPath $staged)) { throw 'Packaging failed' }
Copy-Item -LiteralPath $staged -Destination $OutputJar -Force
Get-FileHash -LiteralPath $OutputJar
