#!/bin/bash
export POSTGRES_USER=scadmin
export POSTGRES_PASSWORD=scadminpass
export POSTGRES_DB=selection-committee
export PGPORT=5432
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=password
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=scrabbit
export RABBITMQ_PASSWORD=scrabbitpass
export RABBIT_HOST=localhost
export RABBIT_PORT=5672
export RABBIT_USER=scrabbit
export RABBIT_PASS=scrabbitpass
export IDENTITY_SERVICE_URL=http://localhost:8081
export NOTIFICATIONS_ENABLED=true
export MINIO_INTERNAL_ENDPOINT=http://localhost:9000
export MINIO_EXTERNAL_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=scminio
export MINIO_SECRET_KEY=scminiopass
export MINIO_BUCKET=docs
export MINIO_REGION=eu-south-1
export MINIO_PATH_STYLE=true
export MINIO_SSE=none
export MINIO_KMS_KEY_ID=""
export MINIO_LAYOUT_PREFIX=documents/
export WEBHOOK_SECRET=e2e-test-secret
export MAILPIT_SMTP=1025
export MAILPIT_HOST=localhost
export MAIL_USER_EMAIL=test@test.com
export MAIL_PASSWORD=testpassword

AUTH="-Dsc.auth.identity-service-url=http://localhost:8081"
RATE='-Dsecurity.rate-limit.endpoints./api/v1/auth/login.max-attempts=1000 -Dsecurity.rate-limit.endpoints./api/v1/auth/login.window-seconds=60 -Dsecurity.rate-limit.endpoints./api/v1/auth/register.max-attempts=1000 -Dsecurity.rate-limit.endpoints./api/v1/auth/register.window-seconds=60 -Dsecurity.rate-limit.endpoints./api/v1/auth/resend-verification.max-attempts=1000 -Dsecurity.rate-limit.endpoints./api/v1/auth/resend-verification.window-seconds=60'
BASE=D:/develop/fice-selection-committee/server/services

cd $BASE/selection-committee-identity-service
java $AUTH $RATE -Dapp.token.jwt.privateKey=file:./private_key.pem -Dapp.token.jwt.publicKey=file:./public_key.pem -jar build/libs/selection-committee-identity-service-0.0.1-SNAPSHOT.jar > /tmp/identity-service.log 2>&1 &
echo "identity-service PID=$!"

cd $BASE/selection-committee-admission-service
java $AUTH -jar build/libs/selection-committee-admission-service-0.0.1-SNAPSHOT.jar > /tmp/admission-service.log 2>&1 &
echo "admission-service PID=$!"

cd $BASE/selection-committee-documents-service
java $AUTH -jar build/libs/selection-committee-documents-service-0.0.1-SNAPSHOT.jar > /tmp/documents-service.log 2>&1 &
echo "documents-service PID=$!"

cd $BASE/selection-committee-environment-service
java $AUTH -Duser.timezone=UTC -jar build/libs/selection-committee-environment-service-0.0.1-SNAPSHOT.jar > /tmp/environment-service.log 2>&1 &
echo "environment-service PID=$!"

cd $BASE/selection-committee-notifications-service
java $AUTH -Dspring.mail.host=localhost -Dspring.mail.port=1025 -Dspring.mail.properties.mail.smtp.auth=false -Dspring.mail.properties.mail.smtp.starttls.enable=false -jar build/libs/selection-committee-notifications-service-0.0.1-SNAPSHOT.jar > /tmp/notifications-service.log 2>&1 &
echo "notifications-service PID=$!"

cd $BASE/selection-committee-gateway
java $AUTH \
  -DIDENTITY_SERVICE_HOST=localhost -DIDENTITY_SERVICE_PORT=8081 \
  -DADMISSION_SERVICE_HOST=localhost -DADMISSION_SERVICE_PORT=8083 \
  -DDOCUMENTS_SERVICE_HOST=localhost -DDOCUMENTS_SERVICE_PORT=8084 \
  -DENVIRONMENT_SERVICE_HOST=localhost -DENVIRONMENT_SERVICE_PORT=8085 \
  -DNOTIFICATIONS_SERVICE_HOST=localhost -DNOTIFICATIONS_SERVICE_PORT=8086 \
  -jar build/libs/selection-committee-gateway-0.0.1-SNAPSHOT.jar > /tmp/gateway-service.log 2>&1 &
echo "gateway PID=$!"

echo "All services starting..."
