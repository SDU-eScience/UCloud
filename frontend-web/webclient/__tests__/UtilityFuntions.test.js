const uf = require("../app/UtilityFunctions");

test("Get parent path for file", () => {
    expect(uf.getParentPath("/path/to/home/file")).toBe("/path/to/home/");
});