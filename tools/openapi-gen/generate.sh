#!/usr/bin/env bash
# Generates TypeScript API clients from running service OpenAPI specs
# Prerequisites: npx (Node.js), or docker with openapitools/openapi-generator-cli
# Usage: ./generate.sh [--gateway] [--direct]
#   --gateway: fetch specs via gateway (default, requires only gateway running)
#   --direct: fetch specs directly from each service

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../../generated-api"
SPECS_DIR="${SCRIPT_DIR}/specs"

# Service definitions
declare -A SERVICES=(
  ["identity"]="8081"
  ["admission"]="8083"
  ["documents"]="8084"
  ["environment"]="8085"
)

GATEWAY_PORT=8080
MODE="${1:---gateway}"

mkdir -p "$SPECS_DIR" "$OUTPUT_DIR"

echo "=== Fetching OpenAPI specs (mode: $MODE) ==="

for service in "${!SERVICES[@]}"; do
  if [ "$MODE" = "--direct" ]; then
    url="http://localhost:${SERVICES[$service]}/v3/api-docs"
  else
    url="http://localhost:${GATEWAY_PORT}/v3/api-docs/${service}"
  fi

  echo "Fetching $service from $url..."
  curl -sf "$url" -o "$SPECS_DIR/${service}.json" || {
    echo "ERROR: Failed to fetch $service spec from $url"
    echo "  Make sure the service is running."
    exit 1
  }
done

echo ""
echo "=== Generating TypeScript clients ==="

for service in "${!SERVICES[@]}"; do
  echo "Generating client for $service..."
  npx @openapitools/openapi-generator-cli generate \
    -i "$SPECS_DIR/${service}.json" \
    -g typescript-axios \
    -o "$OUTPUT_DIR/src/${service}" \
    -c "$SCRIPT_DIR/config.yml" \
    --additional-properties=npmName=@fice-sc/${service}-api \
    --additional-properties=supportsES6=true \
    --additional-properties=withSeparateModelsAndApi=true \
    --additional-properties=modelPackage=models \
    --additional-properties=apiPackage=api \
    2>/dev/null
done

# Generate barrel exports
cat > "$OUTPUT_DIR/src/index.ts" << 'TSEOF'
export * as IdentityApi from './identity';
export * as AdmissionApi from './admission';
export * as DocumentsApi from './documents';
export * as EnvironmentApi from './environment';
TSEOF

echo ""
echo "=== Generation complete ==="
echo "Output: $OUTPUT_DIR/src/"
echo ""
echo "To use in your Next.js project:"
echo "  1. Copy generated-api/src/ to your frontend project"
echo "  2. npm install axios"
echo "  3. Import: import { IdentityApi } from './generated-api'"
