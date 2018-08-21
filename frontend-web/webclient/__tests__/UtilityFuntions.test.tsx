import * as FileUtils from "Utilities/FileUtilities"
import * as UF from "UtilityFunctions";
import initializeTestCloudObject from "./mock/MockCloudObject";

// GET PARENT PATH
test("Get parent path for file", () => {
    expect(FileUtils.getParentPath("/path/to/home/file")).toBe("/path/to/home/");
});

test("Ignore additional backslashes", () => {
   expect(FileUtils.getParentPath("//path///////to///home//////////////file////////////")).toBe("/path/to/home/")
});

test("Empty path", () => {
   expect(FileUtils.getParentPath("")).toBe("");
});

test("Null path", () => {
    expect(FileUtils.getParentPath(null)).toBe("");
});

// TO LOWER CASE AND CAPITALIZE

test("All upper case and numbers", () => {
    expect(UF.toLowerCaseAndCapitalize("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(UF.toLowerCaseAndCapitalize("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(UF.toLowerCaseAndCapitalize("aBaCuS 2.0 !@#$%^&*()")).toBe("Abacus 2.0 !@#$%^&*()")
});

test("Empty string", () => {
    expect(UF.toLowerCaseAndCapitalize("")).toBe("");
});

// FILE SIZE TO STRINGS

test("500 bytes to string", () =>
    expect(FileUtils.fileSizeToString(500)).toBe("500 B")
);

test("1500 bytes to string", () => 
    expect(FileUtils.fileSizeToString(1500)).toBe("1.50 KB")
);

test("1500 * 1000 bytes to string ", () => 
    expect(FileUtils.fileSizeToString(1500 * 1000)).toBe("1.50 MB")
);

test("1500 * 1000**2 bytes to string ", () =>
    expect(FileUtils.fileSizeToString(1500 * 1000**2)).toBe("1.50 GB")
);

test("1500 * 1000**3 bytes to string ", () =>
    expect(FileUtils.fileSizeToString(1500 * 1000**3)).toBe("1.50 TB")
);

test("1500 * 1000**4 bytes to string ", () => 
    expect(FileUtils.fileSizeToString(1500 * 1000**4)).toBe("1.50 PB")
);

test("1500 * 1000**5 bytes to string ", () => 
    expect(FileUtils.fileSizeToString(1500 * 1000**5)).toBe("1.50 EB")
);

// Get filename from path

test("Filename from path", () =>
    expect(FileUtils.getFilenameFromPath("/Home/folder")).toBe("folder")
);

test("Filename from path with special character", () => {
    expect(FileUtils.getFilenameFromPath("/Home/folder_2 (1)")).toBe("folder_2 (1)");
});

// Replace homefolder

const mockHomeFolder = "/home/user@mail.co.uk/";

test("Replace homefolder", () =>
    expect(FileUtils.replaceHomeFolder("/home/user@mail.co.uk/", mockHomeFolder)).toBe("Home/")
);

test("Replace homefolder subfolder", () =>
    expect(FileUtils.replaceHomeFolder("/home/user@mail.co.uk/subFolder/withSomething", mockHomeFolder)).toBe("Home/subFolder/withSomething/")
);

const noHomeFolder = "NotHomeFolder/subfolder/";
test("Replace homefolder, no homefolder", () =>
    expect(FileUtils.replaceHomeFolder(noHomeFolder, mockHomeFolder)).toBe(`${noHomeFolder}`)
);