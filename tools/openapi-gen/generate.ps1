# Generates TypeScript API clients from running service OpenAPI specs
# Prerequisites: npx (Node.js), or docker with openapitools/openapi-generator-cli
# Usage: .\generate.ps1 [-Mode gateway] [-Mode direct]
#   gateway: fetch specs via gateway (default, requires only gateway running)
#   direct: fetch specs directly from each service

param(
    [ValidateSet("gateway", "direct")]
    [string]$Mode = "gateway"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputDir = Join-Path $ScriptDir "..\..\generated-api"
$SpecsDir = Join-Path $ScriptDir "specs"

# Service definitions
$Services = @{
    "identity"    = "8081"
    "admission"   = "8083"
    "documents"   = "8084"
    "environment" = "8085"
}

$GatewayPort = 8080

# Create directories
New-Item -ItemType Directory -Force -Path $SpecsDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Host "=== Fetching OpenAPI specs (mode: $Mode) ==="

foreach ($service in $Services.Keys) {
    if ($Mode -eq "direct") {
        $url = "http://localhost:$($Services[$service])/v3/api-docs"
    } else {
        $url = "http://localhost:${GatewayPort}/v3/api-docs/${service}"
    }

    Write-Host "Fetching $service from $url..."
    try {
        Invoke-RestMethod -Uri $url -OutFile (Join-Path $SpecsDir "${service}.json")
    } catch {
        Write-Error "ERROR: Failed to fetch $service spec from $url"
        Write-Error "  Make sure the service is running."
        exit 1
    }
}

Write-Host ""
Write-Host "=== Generating TypeScript clients ==="

foreach ($service in $Services.Keys) {
    Write-Host "Generating client for $service..."
    $specFile = Join-Path $SpecsDir "${service}.json"
    $outDir = Join-Path $OutputDir "src\${service}"
    $configFile = Join-Path $ScriptDir "config.yml"

    npx @openapitools/openapi-generator-cli generate `
        -i $specFile `
        -g typescript-axios `
        -o $outDir `
        -c $configFile `
        --additional-properties=npmName=@fice-sc/${service}-api `
        --additional-properties=supportsES6=true `
        --additional-properties=withSeparateModelsAndApi=true `
        --additional-properties=modelPackage=models `
        --additional-properties=apiPackage=api `
        2>$null

    if ($LASTEXITCODE -ne 0) {
        Write-Error "ERROR: Failed to generate client for $service"
        exit 1
    }
}

# Generate barrel exports
$indexContent = @"
export * as IdentityApi from './identity';
export * as AdmissionApi from './admission';
export * as DocumentsApi from './documents';
export * as EnvironmentApi from './environment';
"@

$indexFile = Join-Path $OutputDir "src\index.ts"
New-Item -ItemType Directory -Force -Path (Join-Path $OutputDir "src") | Out-Null
Set-Content -Path $indexFile -Value $indexContent -Encoding UTF8

Write-Host ""
Write-Host "=== Generation complete ==="
Write-Host "Output: $OutputDir\src\"
Write-Host ""
Write-Host "To use in your Next.js project:"
Write-Host "  1. Copy generated-api\src\ to your frontend project"
Write-Host "  2. npm install axios"
Write-Host "  3. Import: import { IdentityApi } from './generated-api'"
