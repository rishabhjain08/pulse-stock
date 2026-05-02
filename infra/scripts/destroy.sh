#!/usr/bin/env bash
# Delete the CloudFormation stack and all SSM parameters.
# The S3 bucket is intentionally NOT deleted — empty it manually if desired.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"

echo "WARNING: This deletes the PoarVault stack and all /poarvault SSM parameters."
read -rp "Type 'yes' to confirm: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 0; }

echo "==> Deleting CloudFormation stack"
aws cloudformation delete-stack --stack-name poarvault --region "$REGION" --no-cli-pager
aws cloudformation wait stack-delete-complete --stack-name poarvault --region "$REGION"
echo "    Done"

echo "==> Deleting SSM parameters"
for param in \
  /poarvault/plaid-client-id \
  /poarvault/plaid-secret \
  /poarvault/plaid-env \
  /poarvault/api-key; do
  if aws ssm delete-parameter --name "$param" --region "$REGION" --no-cli-pager 2>/dev/null; then
    echo "    Deleted $param"
  else
    echo "    $param not found — skipped"
  fi
done

echo ""
echo "==> Done. S3 bucket retained — delete it manually if you no longer need it."
echo "    Bucket: poarvault-lambda-$(account_id)-${REGION}"
