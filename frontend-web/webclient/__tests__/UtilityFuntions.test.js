const getParentPath = require("../app/UtilityFunctions.js").getParentPath;

test("Get parent path for file", () => {
    expect(getParentPath("/path/to/home/file")).toBe("/path/to/home/");
});
