module.exports = {
  name: "eyes_selenium_java",
  emitter: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/emitter.js",
  overrides: [
    "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/overrides.js",
    "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/eg.overrides.js"
  ],
  template: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/template.hbs",
  tests: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/java_emitter_workaround/coverage-tests.js",
  ext: ".java",
  outPath: "./src/test/java/coverage/generic",
};
