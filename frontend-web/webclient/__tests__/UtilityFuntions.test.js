import { getParentPath } from "../app/UtilityFunctions";

test("Get parent path for file", () => {
    expect(getParentPath("/path/to/home/file")).toBe("/path/to/home/");
});