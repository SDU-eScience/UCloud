import {
    filepathQuery,
    getFilenameFromPath,
    replaceHomeFolder,
    sizeToString,
    toFileText,
} from "../../app/Utilities/FileUtilities";
import { mockFilesSensitivityConfidential } from "../mock/Files";

describe("To file text", () => {
    test("Single file", () => {
        const firstFile = mockFilesSensitivityConfidential.items[0];
        expect(toFileText([firstFile])).toBe("1 file selected");
    });
});


describe("File size to string", () => {
    test("0 bytes to string", () =>
        expect(sizeToString(0)).toBe("0 B")
    );

    test("500 bytes to string", () =>
        expect(sizeToString(500)).toBe("500 B")
    );

    test("1500 bytes to string", () =>
        expect(sizeToString(1500)).toBe("1.50 KB")
    );

    test("1500 * 1000 bytes to string ", () =>
        expect(sizeToString(1500 * 1000)).toBe("1.50 MB")
    );

    test("1500 * 1000**2 bytes to string ", () =>
        expect(sizeToString(1500 * 1000 ** 2)).toBe("1.50 GB")
    );

    test("1500 * 1000**3 bytes to string ", () =>
        expect(sizeToString(1500 * 1000 ** 3)).toBe("1.50 TB")
    );

    test("1500 * 1000**4 bytes to string ", () =>
        expect(sizeToString(1500 * 1000 ** 4)).toBe("1.50 PB")
    );

    test("1500 * 1000**5 bytes to string ", () =>
        expect(sizeToString(1500 * 1000 ** 5)).toBe("1.50 EB")
    );

    test("Invalid bytes size to string", () =>
        expect(sizeToString(-1)).toBe("Invalid size")
    );
});

describe("Get filename from path", () => {
    test("Filename from path", () =>
        expect(getFilenameFromPath("/Home/folder")).toBe("folder")
    );

    test("Filename from path with special character", () => {
        expect(getFilenameFromPath("/Home/folder_2 (1)")).toBe("folder_2 (1)");
    });
});

describe("Replace homefolder", () => {

    const mockHomeFolder = "/home/user@mail.co.uk/";

    test("Replace homefolder", () =>
        expect(replaceHomeFolder("/home/user@mail.co.uk/", mockHomeFolder)).toBe("Home/")
    );

    test("Replace homefolder subfolder", () =>
        expect(replaceHomeFolder("/home/user@mail.co.uk/subFolder/withSomething", mockHomeFolder))
         .toBe("Home/subFolder/withSomething")
    );

    const noHomeFolder = "NotHomeFolder/subfolder/";
    test("Replace homefolder, no homefolder", () =>
        expect(replaceHomeFolder(noHomeFolder, mockHomeFolder)).toBe(`${noHomeFolder}`)
    );
});


describe("Filepath query", () => {
    test("Defaults", () =>
        expect(filepathQuery("/path", 0, 25)).toBe(
            "files?path=%2Fpath&itemsPerPage=25&page=0&order=ASCENDING&sortBy=path"
        )
    );
});
