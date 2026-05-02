#!/usr/bin/env bash
# Delete both CloudFormation stacks and all SSM parameters.
# The S3 artifact bucket has DeletionPolicy: Retain — empty and delete it manually.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

load_env

REGION="$AWS_REGION"

echo "WARNING: This deletes the poarvault and poarvault-bootstrap stacks and all /poarvault SSM parameters."
read -rp "Type 'yes' to confirm: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 0; }

# Read bucket name before deleting the bootstrap stack
BUCKET="$(artifact_bucket 2>/dev/null || echo "")"

echo "==> Deleting main stack (poarvault)"
aws cloudformation delete-stack --stack-name poarvault --region "$REGION" --no-cli-pager
aws cloudformation wait stack-delete-complete --stack-name poarvault --region "$REGION"
echo "    Done"

echo "==> Deleting bootstrap stack (poarvault-bootstrap)"
aws cloudformation delete-stack --stack-name poarvault-bootstrap --region "$REGION" --no-cli-pager
aws cloudformation wait stack-delete-complete --stack-name poarvault-bootstrap --region "$REGION"
echo "    Done (bucket retained by DeletionPolicy: Retain)"

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
echo "==> Done."
if [[ -n "$BUCKET" ]]; then
  echo "    S3 bucket retained: $BUCKET"
  echo "    Empty it and delete manually: aws s3 rb s3://$BUCKET --force"
fi
