#!/usr/bin/env bash
# One-time setup: create S3 bucket, write SSM params, package Lambda, deploy stack.
# Run again at any time — all steps are idempotent.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"
BUCKET="$(s3_bucket)"
LAMBDA_DIR="$INFRA_DIR/lambda"
CF_TEMPLATE="$INFRA_DIR/cloudformation/template.yaml"

# ── S3 bucket ──────────────────────────────────────────────────────────────
echo "==> S3 bucket: $BUCKET"
if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "    Already exists — skipping"
else
  if [[ "$REGION" == "us-east-1" ]]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" --no-cli-pager
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
      --create-bucket-configuration LocationConstraint="$REGION" --no-cli-pager
  fi
  aws s3api put-bucket-versioning --bucket "$BUCKET" \
    --versioning-configuration Status=Enabled
  aws s3api put-public-access-block --bucket "$BUCKET" \
    --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  echo "    Created"
fi

# ── SSM parameters ─────────────────────────────────────────────────────────
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

# ── Package Lambda ─────────────────────────────────────────────────────────
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

# ── CloudFormation deploy ──────────────────────────────────────────────────
echo "==> Deploying CloudFormation stack (poarvault)"
aws cloudformation deploy \
  --template-file "$CF_TEMPLATE" \
  --stack-name poarvault \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    LambdaS3Bucket="$BUCKET" \
    LambdaS3Key="$ZIP_KEY" \
  --region "$REGION" \
  --no-cli-pager

echo ""
echo "==> Setup complete"
"$SCRIPT_DIR/get-outputs.sh"
