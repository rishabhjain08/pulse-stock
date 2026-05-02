#!/usr/bin/env bash
# Re-package Lambda code and update the main CloudFormation stack.
# Run after any change to infra/lambda/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"
LAMBDA_DIR="$INFRA_DIR/lambda"

BUCKET="$(artifact_bucket)"

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

echo "==> Updating main stack (poarvault)"
aws cloudformation deploy \
  --template-file "$INFRA_DIR/cloudformation/template.yaml" \
  --stack-name poarvault \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides LambdaS3Key="$ZIP_KEY" \
  --region "$REGION" \
  --no-cli-pager

echo ""
echo "==> Deploy complete"
"$SCRIPT_DIR/get-outputs.sh"
