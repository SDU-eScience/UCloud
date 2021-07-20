import * as FileUtils from "../../app/Utilities/FileUtilities";

jest.mock("Authentication/HttpClientInstance", () => ({
    Client: {
        get: (path: string) => {
            switch (path) {
                case "/files/stat?path=%2Fthis%2Ffile%2Fdoesnt_exists":
                    throw {request: {status: 403}, response: {}};
            }
            return {request: {} as XMLHttpRequestUpload, response: {}};
        },
        homeFolder: "/home/test@test.dk/",
        projectId: "foo",
        username: "foo_mac_foo_face"
    }
}));

describe("File size to string", () => {
    test("0 bytes to string", () =>
        expect(FileUtils.sizeToString(0)).toBe("0 B")
    );

    test("500 bytes to string", () =>
        expect(FileUtils.sizeToString(500)).toBe("500 B")
    );

    test("1500 bytes to string", () =>
        expect(FileUtils.sizeToString(1500)).toBe("1.50 KB")
    );

    test("1500 * 1000 bytes to string ", () =>
        expect(FileUtils.sizeToString(1500 * 1000)).toBe("1.50 MB")
    );

    test("1500 * 1000**2 bytes to string ", () =>
        expect(FileUtils.sizeToString(1500 * 1000 ** 2)).toBe("1.50 GB")
    );

    test("1500 * 1000**3 bytes to string ", () =>
        expect(FileUtils.sizeToString(1500 * 1000 ** 3)).toBe("1.50 TB")
    );

    test("1500 * 1000**4 bytes to string ", () =>
        expect(FileUtils.sizeToString(1500 * 1000 ** 4)).toBe("1.50 PB")
    );

    test("1500 * 1000**5 bytes to string ", () =>
        expect(FileUtils.sizeToString(1500 * 1000 ** 5)).toBe("1.50 EB")
    );

    test("Invalid bytes size to string", () =>
        expect(FileUtils.sizeToString(-1)).toBe("Invalid size")
    );
});

describe("Get filename from path", () => {
    test("Filename from path", () =>
        expect(FileUtils.getFilenameFromPath("/Home/folder", [])).toBe("folder")
    );

    test("Filename from path with special character", () => {
        expect(FileUtils.getFilenameFromPath("/Home/folder_2 (1)", [])).toBe("folder_2 (1)");
    });
});

describe("resolvePath", () => {
    test("Already resolved path", () => {
        expect(FileUtils.resolvePath("/this/is/a/path")).toBe("/this/is/a/path");
    });

    test("With self-reference path", () => {
        expect(FileUtils.resolvePath("/this/is/./a/path")).toBe("/this/is/a/path");
    });

    test("With parent-reference path", () => {
        expect(FileUtils.resolvePath("/this/is/also/../a/path")).toBe("/this/is/a/path");
    });
});

describe("isInvalidPathName", () => {
    test("Contains ..", () => {
        expect(FileUtils.isInvalidPathName({path: "..Home", filePaths: []})).toBeTruthy();
    });

    test("Contains /", () => {
        expect(FileUtils.isInvalidPathName({path: "Home/", filePaths: []})).toBeTruthy();
    });

    test("Is empty", () => {
        expect(FileUtils.isInvalidPathName({path: "", filePaths: []})).toBeTruthy();
    });

    test("Is .", () => {
        expect(FileUtils.isInvalidPathName({path: ".", filePaths: []})).toBeTruthy();
    });

    test("Conflicts with existing", () => {
        expect(FileUtils.isInvalidPathName({path: "I'm colliding!", filePaths: ["I'm colliding!", "I'm safe!"]})).toBeTruthy();
    });

    test("Passes", () => {
        expect(FileUtils.isInvalidPathName({path: "Legal", filePaths: ["Other filename", "and another"]})).toBeFalsy();
    });
});
