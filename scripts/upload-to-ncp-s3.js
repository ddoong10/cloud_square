#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const AWS = require("aws-sdk");

function requireEnv(name) {
  const value = process.env[name];
  if (!value || value.trim() === "") {
    throw new Error(`${name} is required`);
  }
  return value;
}

async function putObject(s3, bucket, key, body, contentType) {
  await s3
    .putObject({
      Bucket: bucket,
      Key: key,
      Body: body,
      ContentType: contentType,
    })
    .promise();
}

async function deleteObject(s3, bucket, key) {
  await s3
    .deleteObject({
      Bucket: bucket,
      Key: key,
    })
    .promise();
}

async function main() {
  const accessKeyId = requireEnv("NCP_DEPLOY_ACCESS_KEY");
  const secretAccessKey = requireEnv("NCP_DEPLOY_SECRET_KEY");
  const endpoint = requireEnv("NCP_S3_ENDPOINT");
  const region = requireEnv("NCP_S3_REGION");
  const bucket = requireEnv("NCP_DEPLOY_BUCKET");
  const gitSha = requireEnv("GIT_SHA");
  const deployPrefix = (process.env.NCP_DEPLOY_PREFIX || "uploads/lms-deploy").trim();

  const version = gitSha.substring(0, 12);
  const publishedAt = new Date().toISOString();

  const wasArtifactPath = path.resolve("dist/lms-was-deploy.tar.gz");

  if (!fs.existsSync(wasArtifactPath)) {
    throw new Error(`artifact not found: ${wasArtifactPath}`);
  }

  const s3 = new AWS.S3({
    accessKeyId,
    secretAccessKey,
    endpoint,
    region,
    signatureVersion: "v4",
    s3ForcePathStyle: true,
    sslEnabled: true,
    maxRetries: 2,
  });

  const checkKey = `${deployPrefix}/_checks/write-check-${Date.now()}.txt`;
  await putObject(s3, bucket, checkKey, Buffer.from("ok\n", "utf-8"), "text/plain");
  await deleteObject(s3, bucket, checkKey);

  const wasReleaseKey = `${deployPrefix}/was/releases/${version}/lms-was-deploy.tar.gz`;
  const wasLatestKey = `${deployPrefix}/was/latest/lms-was-deploy.tar.gz`;
  const wasVersionKey = `${deployPrefix}/was/latest/version.txt`;
  const wasMetaKey = `${deployPrefix}/was/latest/metadata.json`;

  const wasArtifact = fs.readFileSync(wasArtifactPath);
  const wasMetadata = JSON.stringify(
    {
      version,
      publishedAt,
      artifact: wasReleaseKey,
    },
    null,
    0
  );

  await putObject(s3, bucket, wasReleaseKey, wasArtifact, "application/gzip");
  await putObject(s3, bucket, wasLatestKey, wasArtifact, "application/gzip");
  await putObject(s3, bucket, wasVersionKey, Buffer.from(`${version}\n`, "utf-8"), "text/plain");
  await putObject(s3, bucket, wasMetaKey, Buffer.from(wasMetadata, "utf-8"), "application/json");

  console.log(`Uploaded WAS deploy artifact to s3://${bucket}/${deployPrefix}`);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
