#!/usr/bin/env bash
# Sourced by all other scripts — never executed directly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$INFRA_DIR/.env"

load_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: $ENV_FILE not found." >&2
    echo "       Copy infra/.env.template to infra/.env and fill in values." >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  : "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID must be set in infra/.env}"
  : "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY must be set in infra/.env}"
  : "${AWS_REGION:?AWS_REGION must be set in infra/.env}"
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION
}

# Read the artifact bucket name from the bootstrap stack output.
artifact_bucket() {
  aws cloudformation describe-stacks \
    --stack-name poarvault-bootstrap \
    --query 'Stacks[0].Outputs[?OutputKey==`ArtifactBucketName`].OutputValue' \
    --output text \
    --region "$AWS_REGION"
}
