"use strict";

const path = require("node:path");
const projectRoot = path.resolve(__dirname, "..");
const pkg = require(path.join(projectRoot, "package.json"));

function resolveFromProject(name) {
  try {
    return require.resolve(name, { paths: [projectRoot] });
  } catch (error) {
    throw new Error(
      "Cannot resolve " + name + ". Remove node_modules and run npm ci. " +
      "Original error: " + error.message
    );
  }
}

function installedVersion(name) {
  return require(resolveFromProject(name + "/package.json")).version;
}

const nextVersion = installedVersion("next");
const eslintConfigVersion = installedVersion("eslint-config-next");
const eslintVersion = installedVersion("eslint");

resolveFromProject("next/babel");

if (nextVersion !== pkg.dependencies.next) {
  throw new Error("Installed next " + nextVersion + " differs from pinned " + pkg.dependencies.next);
}
if (eslintConfigVersion !== pkg.devDependencies["eslint-config-next"]) {
  throw new Error("Installed eslint-config-next " + eslintConfigVersion +
    " differs from pinned " + pkg.devDependencies["eslint-config-next"]);
}
if (eslintVersion !== pkg.devDependencies.eslint) {
  throw new Error("Installed eslint " + eslintVersion + " differs from pinned " + pkg.devDependencies.eslint);
}
if (nextVersion !== eslintConfigVersion) {
  throw new Error("next and eslint-config-next versions must match exactly.");
}

console.log("Frontend dependency verification passed.");
console.log("next=" + nextVersion);
console.log("eslint-config-next=" + eslintConfigVersion);
console.log("eslint=" + eslintVersion);
console.log("next/babel=" + resolveFromProject("next/babel"));
