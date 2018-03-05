import { getParentPath, toLowerCaseAndCapitalize, fileSizeToString, castValueTo } from "../app/UtilityFunctions";
import SDUCloud from "../authentication/lib";
import initializeTestCloudObject from "./mock/TestCloudObject";


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

test("Undefined path", () => {
    expect(getParentPath()).toBe("");
});

// TO LOWER CASE AND CAPITALIZE

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

// FILE SIZE TO STRINGS

test("500 bytes to string", () => {
    expect(fileSizeToString(500)).toBe("500 B");
});

test("1500 bytes to string", () => {
    expect(fileSizeToString(1500)).toBe("1.5 KB");
});

test("1500 * 1000 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000)).toBe("1.5 MB")
});

test("1500 * 1000**2 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**2)).toBe("1.5 GB")
});

test("1500 * 1000**3 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**3)).toBe("1.5 TB")
});

test("1500 * 1000**4 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**4)).toBe("1.5 PB")
});

test("1500 * 1000**5 bytes to string ", () => {
    expect(fileSizeToString(1500 * 1000**5)).toBe("1.5 EB")
});

test("Null as input", () => {
    expect(fileSizeToString(null)).toBe("");
});

test("Undefined as input", () => {
    expect(fileSizeToString()).toBe("");
});

// Cast Value To

test("Cast string to floating point", () => {
    expect(castValueTo("floating_point", "3.15")).toBe(3.15);
});

test("Cast string to int", () => {
   expect(castValueTo("integer", "42")).toBe(42);
});

test("Unhandled case, boolean: True as string", () => {
    expect(castValueTo("Boolean", "true")).toBe("true");
});

test("true as value, return as is", () => {
    expect(castValueTo("boolean", true)).toBe(true);
});

test("Return type string as is.", () => {
    expect(castValueTo("string", "A string.")).toBe("A string.");
});

test("Type null, return value as is", () => {
    expect(castValueTo(null, null)).toBe(null);
});

test("Type null, return non-null value as is", () => {
    expect(castValueTo(null, "String")).toBe("String");
});

test("Undefined returns null",() => {
    expect(castValueTo()).toBe(null);
});

const cloud = initializeTestCloudObject();