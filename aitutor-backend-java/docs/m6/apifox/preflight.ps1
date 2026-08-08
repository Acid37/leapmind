param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3310
)

$ErrorActionPreference = "Stop"

function Test-TcpPort {
    param(
        [string]$TargetHost,
        [int]$Port
    )

    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $pending = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $connected = $pending.AsyncWaitHandle.WaitOne(1500, $false) -and $client.Connected
        if ($connected) {
            $client.EndConnect($pending)
        }
        $client.Dispose()
        return $connected
    }
    catch {
        return $false
    }
}

$collectionPath = Join-Path $PSScriptRoot "leapmind-m6-demo.postman_collection.json"
$openApiPath = Join-Path (Split-Path $PSScriptRoot -Parent) "user-profile-openapi.yaml"

$collection = Get-Content -LiteralPath $collectionPath -Raw -Encoding UTF8 | ConvertFrom-Json

$assetsOk = $collection.item.Count -eq 4 `
    -and (Test-Path -LiteralPath $openApiPath)

$backendOk = $false
try {
    $healthUrl = "$($BaseUrl.TrimEnd('/'))/api/auth/test1"
    $response = Invoke-WebRequest -Uri $healthUrl -Method Get -UseBasicParsing -TimeoutSec 3
    $backendOk = $response.StatusCode -eq 200
}
catch {
    $backendOk = $false
}

$mysqlOk = Test-TcpPort -TargetHost $MySqlHost -Port $MySqlPort

Write-Output ("[assets]  {0} - Apifox collection/OpenAPI" -f $(if ($assetsOk) { "READY" } else { "FAIL" }))
Write-Output ("[backend] {0} - {1}" -f $(if ($backendOk) { "READY" } else { "NOT READY" }), $BaseUrl)
Write-Output ("[mysql]   {0} - {1}:{2}" -f $(if ($mysqlOk) { "READY" } else { "NOT READY" }), $MySqlHost, $MySqlPort)

if (-not $assetsOk) {
    Write-Error "Apifox demo assets are incomplete or invalid."
    exit 1
}

if (-not ($backendOk -and $mysqlOk)) {
    Write-Warning "Live API mode is not ready. Use the offline response examples documented in README.md."
    exit 2
}

Write-Output "Live API process checks passed. Verify the demo account login in Apifox."
