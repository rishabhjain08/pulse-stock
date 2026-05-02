#!/usr/bin/env bash
# Print the API URL and API key after a successful deploy.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"

API_URL="$(aws cloudformation describe-stacks \
  --stack-name poarvault \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiUrl`].OutputValue' \
  --output text \
  --region "$REGION")"

API_KEY="$(aws ssm get-parameter \
  --name /poarvault/api-key \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "$REGION")"

echo ""
echo "┌─ PoarVault API ──────────────────────────────────────────────────────┐"
echo "│  URL:     $API_URL"
echo "│  API Key: $API_KEY"
echo "└──────────────────────────────────────────────────────────────────────┘"
echo ""
echo "Add to local.properties:"
echo "  POARVAULT_API_URL=$API_URL"
echo "  POARVAULT_API_KEY=$API_KEY"
