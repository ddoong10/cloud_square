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

const CONTENT_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".eot": "application/vnd.ms-fontobject",
  ".map": "application/json",
  ".txt": "text/plain; charset=utf-8",
};

function getContentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  return CONTENT_TYPES[ext] || "application/octet-stream";
}

function collectFiles(dir, base) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    const relativePath = path.join(base, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectFiles(fullPath, relativePath));
    } else if (entry.isFile()) {
      results.push({ fullPath, relativePath: relativePath.replace(/\\/g, "/") });
    }
  }
  return results;
}

async function main() {
  const accessKeyId = requireEnv("NCP_DEPLOY_ACCESS_KEY");
  const secretAccessKey = requireEnv("NCP_DEPLOY_SECRET_KEY");
  const endpoint = requireEnv("NCP_S3_ENDPOINT");
  const region = requireEnv("NCP_S3_REGION");
  const cdnBucket = requireEnv("NCP_CDN_BUCKET");
  const cdnPrefix = (process.env.NCP_CDN_PREFIX || "").trim();

  const frontendDir = path.resolve(__dirname, "../frontend");
  if (!fs.existsSync(frontendDir)) {
    throw new Error(`frontend directory not found: ${frontendDir}`);
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

  const files = collectFiles(frontendDir, "");
  console.log(`Uploading ${files.length} frontend files to s3://${cdnBucket}/${cdnPrefix || "(root)"}`);

  for (const file of files) {
    const key = cdnPrefix ? `${cdnPrefix}/${file.relativePath}` : file.relativePath;
    const contentType = getContentType(file.fullPath);
    const body = fs.readFileSync(file.fullPath);

    const ext = path.extname(file.relativePath).toLowerCase();
    const isStatic = [".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot"].includes(ext);
    const cacheControl =
      file.relativePath === "index.html"
        ? "no-cache, no-store, must-revalidate"
        : isStatic
          ? "public, max-age=2592000"
          : "public, max-age=0, must-revalidate";

    await s3
      .putObject({
        Bucket: cdnBucket,
        Key: key,
        Body: body,
        ContentType: contentType,
        CacheControl: cacheControl,
        ACL: "public-read",
      })
      .promise();

    console.log(`  ${key} (${contentType})`);
  }

  console.log(`Frontend CDN upload complete: ${files.length} files`);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
