import * as FileUtils from "Utilities/FileUtilities";
import { mockFiles_SensitivityConfidential } from "../mock/Files";

describe("File Operations", () => {
    test("No file operations", () =>
        expect(FileUtils.AllFileOperations(false, false, false, false)).toEqual([])
    );
})

describe("To file text", () => {
    test("Single file", () => {
        const firstFile = mockFiles_SensitivityConfidential.items[0];
        const path = firstFile.path.split("/").pop();
        expect(FileUtils.toFileText([firstFile])).toBe(path);
    });

    test("Multiple files", () => {
        expect(FileUtils.toFileText(mockFiles_SensitivityConfidential.items)).toBe("10 files selected.")
    });
});

describe("GET PARENT PATH", () => {
    test("Get parent path for file", () => {
        expect(FileUtils.getParentPath("/path/to/home/file")).toBe("/path/to/home/");
    });

    test("Ignore additional backslashes", () => {
        expect(FileUtils.getParentPath("//path///////to///home//////////////file////////////")).toBe("/path/to/home/")
    });

    test("Empty path", () => {
        expect(FileUtils.getParentPath("")).toBe("");
    });
});

describe("File size to string", () => {
    test("0 bytes to string", () =>
        expect(FileUtils.fileSizeToString(0)).toBe("0 B")
    );

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
        expect(FileUtils.fileSizeToString(1500 * 1000 ** 2)).toBe("1.50 GB")
    );

    test("1500 * 1000**3 bytes to string ", () =>
        expect(FileUtils.fileSizeToString(1500 * 1000 ** 3)).toBe("1.50 TB")
    );

    test("1500 * 1000**4 bytes to string ", () =>
        expect(FileUtils.fileSizeToString(1500 * 1000 ** 4)).toBe("1.50 PB")
    );

    test("1500 * 1000**5 bytes to string ", () =>
        expect(FileUtils.fileSizeToString(1500 * 1000 ** 5)).toBe("1.50 EB")
    );

    test("Invalid bytes size to string", () =>
        expect(FileUtils.fileSizeToString(-1)).toBe("Invalid size")
    );
});


describe("Get filename from path", () => {
    test("Filename from path", () =>
        expect(FileUtils.getFilenameFromPath("/Home/folder")).toBe("folder")
    );

    test("Filename from path with special character", () => {
        expect(FileUtils.getFilenameFromPath("/Home/folder_2 (1)")).toBe("folder_2 (1)");
    });
});

describe("Replace homefolder", () => {

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
});

describe("Filepath query", () =>
    test("Defaults", () =>
        expect(FileUtils.filepathQuery("path", 0, 25)).toBe(
            "files?path=path&itemsPerPage=25&page=0&order=ASCENDING&sortBy=PATH"
        )
    )
);

describe("Filelookup query", () =>
    test("Defaults", () => 
        expect(FileUtils.fileLookupQuery("path")).toBe(
            "files/lookup?path=path&itemsPerPage=25&order=DESCENDING&sortBy=PATH"
        )
    )
);