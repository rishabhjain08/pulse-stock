#!/usr/bin/env bash
# One-time setup: write SSM params, deploy bootstrap (S3 bucket), package Lambda, deploy main stack.
# All AWS resources are created/updated exclusively via CloudFormation.
# Safe to re-run — all steps are idempotent.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"
LAMBDA_DIR="$INFRA_DIR/lambda"

# ── SSM parameters (secrets — managed outside CF to avoid plaintext in templates) ──
echo "==> SSM parameters"
put_param() {
  local name="$1" value="$2" type="${3:-SecureString}"
  aws ssm put-parameter --name "$name" --value "$value" \
    --type "$type" --overwrite --region "$REGION" --no-cli-pager > /dev/null
  echo "    $name"
}
put_param "/poarvault/plaid-client-id" "${PLAID_CLIENT_ID:?PLAID_CLIENT_ID must be set in infra/.env}"
put_param "/poarvault/plaid-secret"    "${PLAID_SECRET:?PLAID_SECRET must be set in infra/.env}"
put_param "/poarvault/plaid-env"       "${PLAID_ENV:-sandbox}" String

# Generate a random API key on first run; preserve it on subsequent runs.
EXISTING_KEY="$(aws ssm get-parameter --name "/poarvault/api-key" \
  --with-decryption --query Parameter.Value --output text \
  --region "$REGION" 2>/dev/null || echo "")"
if [[ -z "$EXISTING_KEY" ]]; then
  API_KEY="$(openssl rand -hex 32)"
  put_param "/poarvault/api-key" "$API_KEY"
  echo ""
  echo "    >>> Generated API key — save this now:"
  echo "        POARVAULT_API_KEY=$API_KEY"
  echo "    Add it to local.properties (and to GitHub Secrets for CI)."
  echo ""
else
  echo "    /poarvault/api-key already exists"
fi

# ── Bootstrap stack — creates the S3 artifact bucket via CloudFormation ────
echo "==> Deploying bootstrap stack (poarvault-bootstrap)"
aws cloudformation deploy \
  --template-file "$INFRA_DIR/cloudformation/bootstrap.yaml" \
  --stack-name poarvault-bootstrap \
  --region "$REGION" \
  --no-cli-pager
BUCKET="$(artifact_bucket)"
echo "    Artifact bucket: $BUCKET"

# ── Package Lambda ──────────────────────────────────────────────────────────
echo "==> Packaging Lambda"
cd "$LAMBDA_DIR"
npm install --omit=dev --silent
TIMESTAMP="$(date +%s)"
ZIP_KEY="lambda-${TIMESTAMP}.zip"
ZIP_PATH="/tmp/$ZIP_KEY"
zip -r "$ZIP_PATH" . -x "*.zip" > /dev/null
aws s3 cp "$ZIP_PATH" "s3://${BUCKET}/${ZIP_KEY}" --region "$REGION" --no-cli-pager
echo "    Uploaded s3://${BUCKET}/${ZIP_KEY}"
rm -f "$ZIP_PATH"

# ── Main stack — Lambda functions + HTTP API Gateway ───────────────────────
echo "==> Deploying main stack (poarvault)"
aws cloudformation deploy \
  --template-file "$INFRA_DIR/cloudformation/template.yaml" \
  --stack-name poarvault \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides LambdaS3Key="$ZIP_KEY" \
  --region "$REGION" \
  --no-cli-pager

echo ""
echo "==> Setup complete"
"$SCRIPT_DIR/get-outputs.sh"
