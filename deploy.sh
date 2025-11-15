#!/bin/bash

# Check if tools are installed
if ! command -v aws &> /dev/null
then
    printf "⚠️ AWS CLI could not be found. Please install it and try again. \n"
    exit 1
fi

if ! command -v jq &>/dev/null; then
  printf "⚠️ 'jq' is not installed. Please install it and try again. \n"
  exit 1
fi

if ! command -v cdk &>/dev/null; then
  printf "⚠️ 'cdk' is not installed. Please install it and try again. \n"
  exit 1
fi

# Load environment variables
if [ -f .env ]; then
  printf "💾 Loading environment variables ... \n"
  # Method 1: Using `export` and `source`
  export $(grep -v '^#' .env | xargs)
else
  printf "⚠️ .env file not found. Please create one with the required variables. \n"
  exit 1
fi

# package
printf "🔄 Building application ... \n"
./mvnw package


# Assume the role using base SSO profile
printf "🔐 Assuming role: $ROLE_ARN ...\n"
CREDS=$(aws sts assume-role \
  --profile "$PROFILE_NAME" \
  --role-arn "$ROLE_ARN" \
  --role-session-name "$SESSION_NAME" \
  --output json)

# Export temporary credentials
export AWS_ACCESS_KEY_ID=$(printf "$CREDS" | jq -r '.Credentials.AccessKeyId')
export AWS_SECRET_ACCESS_KEY=$(printf "$CREDS" | jq -r '.Credentials.SecretAccessKey')
export AWS_SESSION_TOKEN=$(printf "$CREDS" | jq -r '.Credentials.SessionToken')
export AWS_DEFAULT_REGION

printf "✅ Temporary credentials acquired \n"

printf "🚀 Starting deployment ... \n"

cdk deploy

printf "🎉 Deployment complete \n"
