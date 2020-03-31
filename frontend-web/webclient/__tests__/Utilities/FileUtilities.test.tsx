import {
    getFilenameFromPath,
    replaceHomeOrProjectFolder,
    sizeToString
} from "../../app/Utilities/FileUtilities";
import {Client} from "../../app/Authentication/HttpClientInstance";

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

describe("Replace homefolder and project folder", () => {
    test("Replace homefolder", () =>
        expect(replaceHomeOrProjectFolder("/home/test@test.dk/", Client)).toBe("Home/")
    );

    test("Replace homefolder subfolder", () =>
        expect(replaceHomeOrProjectFolder("/home/test@test.dk/subFolder/withSomething", Client))
            .toBe("Home/subFolder/withSomething")
    );

    const noHomeFolder = "NotHomeFolder/subfolder/";
    test("Replace homefolder, no homefolder", () =>
        expect(replaceHomeOrProjectFolder(noHomeFolder, Client)).toBe(`${noHomeFolder}`)
    );
});
