module.exports = {
  name: "eyes_selenium_java",
  emitter: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/emitter.js",
  overrides: [
    "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/overrides.js",
  ],
  template: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/java/template.hbs",
  tests: "https://raw.githubusercontent.com/applitools/sdk.coverage.tests/master/coverage-tests.js",
  ext: ".java",
  outPath: "./src/test/java/coverage/generic",
};
