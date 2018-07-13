import { getParentPath, toLowerCaseAndCapitalize, fileSizeToString, getFilenameFromPath } from "UtilityFunctions";
//import initializeTestCloudObject from "./mock/TestCloudObject";

// GET PARENT PATH
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

// TO LOWER CASE AND CAPITALIZE

test("All upper case and numbers", () => {
    expect(toLowerCaseAndCapitalize("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(toLowerCaseAndCapitalize("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(toLowerCaseAndCapitalize("aBaCuS 2.0 !@#$%^&*()")).toBe("Abacus 2.0 !@#$%^&*()")
});

test("Empty string", () => {
    expect(toLowerCaseAndCapitalize("")).toBe("");
});

test("Null string", () => {
   expect(toLowerCaseAndCapitalize(null)).toBe("");
});

// FILE SIZE TO STRINGS

test("500 bytes to string", () => {
    expect(fileSizeToString(500)).toBe("500 B");
});

test("1500 bytes to string", () => {
    expect(fileSizeToString(1500)).toBe("1.50 KB");
});

test("1500 * 1000 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000)).toBe("1.50 MB")
});

test("1500 * 1000**2 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**2)).toBe("1.50 GB")
});

test("1500 * 1000**3 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**3)).toBe("1.50 TB")
});

test("1500 * 1000**4 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**4)).toBe("1.50 PB")
});

test("1500 * 1000**5 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**5)).toBe("1.50 EB")
});

test("Null as input", () => {
    expect(fileSizeToString(null)).toBe("");
});

// Get filename from path

test("Filename from path", () => {
    expect(getFilenameFromPath("/Home/folder")).toBe("folder");
});