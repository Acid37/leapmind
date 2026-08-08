const fs = require("fs");
const path = require("path");

const collection = JSON.parse(
  fs.readFileSync(path.join(__dirname, "leapmind-m6-demo.postman_collection.json"), "utf8"),
);
const definedVariables = new Set([
  "baseUrl",
  "username",
  "password",
  "accessToken",
  "userId",
  "otherUserId",
  "kpId",
  "kpId2",
]);
const generatedVariables = new Set([
  "eventId",
  "occurredAt",
  "sensitiveEventId",
  "batchEventId1",
  "batchEventId2",
  "mediaEventId",
]);
const usedVariables = new Set();

const counts = {
  requests: 0,
  jsonBodies: 0,
  responseExamples: 0,
  scripts: 0,
};

for (const match of JSON.stringify(collection.auth || {}).matchAll(/\{\{([A-Za-z0-9_]+)\}\}/g)) {
  usedVariables.add(match[1]);
}

function substituteVariables(raw) {
  return raw
    .replace(/\{\{(userId|otherUserId|kpId|kpId2)\}\}/g, "1")
    .replace(/\{\{[A-Za-z0-9_]+\}\}/g, "demo");
}

function validateItems(container) {
  for (const item of container.item || []) {
    if (item.request) {
      counts.requests += 1;

      const requestText = JSON.stringify(item.request);
      for (const match of requestText.matchAll(/\{\{([A-Za-z0-9_]+)\}\}/g)) {
        usedVariables.add(match[1]);
      }

      const isJson = (item.request.header || []).some(
        (header) => header.key === "Content-Type" && header.value === "application/json",
      );
      const raw = item.request.body?.mode === "raw" ? item.request.body.raw : null;
      if (isJson && raw) {
        JSON.parse(substituteVariables(raw));
        counts.jsonBodies += 1;
      }

      for (const response of item.response || []) {
        if (response.body) {
          JSON.parse(response.body);
          counts.responseExamples += 1;
        }
      }
    }

    for (const event of item.event || []) {
      const source = (event.script?.exec || []).join("\n");
      new Function(source);
      counts.scripts += 1;
    }

    validateItems(item);
  }
}

validateItems(collection);

const missingVariables = [...usedVariables].filter(
  (name) => !definedVariables.has(name) && !generatedVariables.has(name),
);
if (missingVariables.length > 0) {
  throw new Error(`Undefined variables: ${missingVariables.join(", ")}`);
}

console.log(
  JSON.stringify({
    ...counts,
    variables: [...usedVariables].sort(),
  }),
);
