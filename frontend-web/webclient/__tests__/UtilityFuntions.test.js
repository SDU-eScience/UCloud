import { getParentPath, toLowerCaseAndCapitalize } from "../app/UtilityFunctions";

test("Get parent path for file", () => {
    expect(getParentPath("/path/to/home/file")).toBe("/path/to/home/");
});

test("Ignore additional backslashes", () => {
   expect(getParentPath("//path///////to///home//////////////file////////////")).toBe("/path/to/home/")
});

test("Empty path", () => {
   expect(getParentPath("")).toBe("");
});

test("Null path", () => {
    expect(getParentPath(null)).toBe("");
});

test("Undefined path", () => {
    expect(getParentPath()).toBe("");
});

test("All upper case and numbers", () => {
    expect(toLowerCaseAndCapitalize("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(toLowerCaseAndCapitalize("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(toLowerCaseAndCapitalize("aBaCuS 2.0 !@#$%^&*()"))
});

test("Empty string", () => {
    expect(toLowerCaseAndCapitalize("")).toBe("");
});

test("Null string", () => {
   expect(toLowerCaseAndCapitalize(null)).toBe("");
});

test("Undefined string", () => {
   expect(toLowerCaseAndCapitalize()).toBe("");
});
