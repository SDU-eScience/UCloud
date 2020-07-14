import * as FileUtils from "../../app/Utilities/FileUtilities";
import {UploadPolicy} from "../../app/Uploader/api";
import {Client} from "../../app/Authentication/HttpClientInstance";
import {SensitivityLevelMap} from "../../app/DefaultObjects";

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

describe("Replace homefolder and project folder", () => {
    test("Replace homefolder", () =>
        expect(FileUtils.replaceHomeOrProjectFolder("/home/test@test.dk/", Client, [])).toBe("Home/")
    );

    test("Replace homefolder subfolder", () =>
        expect(FileUtils.replaceHomeOrProjectFolder("/home/test@test.dk/subFolder/withSomething", Client, []))
            .toBe("Home/subFolder/withSomething")
    );

    const noHomeFolder = "NotHomeFolder/subfolder/";
    test("Replace homefolder, no homefolder", () =>
        expect(FileUtils.replaceHomeOrProjectFolder(noHomeFolder, Client, [])).toBe(`${noHomeFolder}`)
    );
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

test("filePreviewQuery", () => {
    expect(
        FileUtils.filePreviewQuery("/this/is/a/path")
    ).toBe("/files/preview?path=%2Fthis%2Fis%2Fa%2Fpath");
});

describe("checkIfFileExists", () => {
    test("Exists", async () => {
        const result = await FileUtils.checkIfFileExists("/this/file/exists", Client);
        expect(result).toBeTruthy();
    });

    test("Mocked doesn't exist", async () => {
        const result = await FileUtils.checkIfFileExists("/this/file/doesnt_exists", Client);
        expect(result).toBeFalsy();
    });
});

describe("moveFileQuery", () => {
    test("No policy", () => {
        expect(FileUtils.moveFileQuery(
            "/this/is/the/old/path", "/this/is/the/new/path"
        )).toBe("/files/move?path=%2Fthis%2Fis%2Fthe%2Fold%2Fpath&newPath=%2Fthis%2Fis%2Fthe%2Fnew%2Fpath");
    });

    test("With policy", () => {
        expect(FileUtils.moveFileQuery(
            "/this/is/the/old/path", "/this/is/the/new/path", UploadPolicy.RENAME
        )).toBe("/files/move?path=%2Fthis%2Fis%2Fthe%2Fold%2Fpath&newPath=%2Fthis%2Fis%2Fthe%2Fnew%2Fpath&policy=RENAME");
    });
});

describe("copyFileQuery", () => {
    test("With policy", () => {
        expect(FileUtils.copyFileQuery(
            "/this/is/the/old/path", "/this/is/the/new/path", UploadPolicy.RENAME
        )).toBe("/files/copy?path=%2Fthis%2Fis%2Fthe%2Fold%2Fpath&newPath=%2Fthis%2Fis%2Fthe%2Fnew%2Fpath&policy=RENAME");
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

describe("isFixedFolder", () => {
    test("Is home Trash Folder", () => {
        expect(FileUtils.isFixedFolder("/home/user@test.dk/Trash")).toBeTruthy();
    });

    test("Is project Trash Folder", () => {
        expect(FileUtils.isFixedFolder("/projects/_name_/Personal/content/Trash")).toBeTruthy();
    });

    test("Is home Jobs Folder", () => {
        expect(FileUtils.isFixedFolder("/home/user@test.dk/Jobs")).toBeTruthy();
    });

    test("Is project Jobs folder", () => {
        expect(FileUtils.isFixedFolder("/projects/_name_/Personal/content/Jobs")).toBeTruthy();
    });

    test("In home, and isn't fixed", () => {
        expect(FileUtils.isFixedFolder("/home/user@test.dk/just a folder")).toBeFalsy();
    });
    test("In project, and isn't fixed", () => {
        expect(FileUtils.isFixedFolder("/projects/_name_/Personal/content/just a folder")).toBeFalsy();
    });
});

describe("isDirectory", () => {
    test("FILE", () => {
        expect(FileUtils.isDirectory({fileType: "FILE"})).toBeFalsy();
    });

    test("DIRECTORY", () => {
        expect(FileUtils.isDirectory({fileType: "DIRECTORY"})).toBeTruthy();
    });

    test("FAVFOLDER", () => {
        expect(FileUtils.isDirectory({fileType: "FAVFOLDER"})).toBeFalsy();
    });

    test("SHARESFOLDER", () => {
        expect(FileUtils.isDirectory({fileType: "SHARESFOLDER"})).toBeFalsy();
    });

    test("TRASHFOLDER", () => {
        expect(FileUtils.isDirectory({fileType: "TRASHFOLDER"})).toBeFalsy();
    });

    test("FSFOLDER", () => {
        expect(FileUtils.isDirectory({fileType: "FSFOLDER"})).toBeFalsy();
    });

    test("RESULTFOLDER", () => {
        expect(FileUtils.isDirectory({fileType: "RESULTFOLDER"})).toBeFalsy();
    });
});

describe("expandHomeOrProjectFolder", () => {
    test("Don't expand", () => {
        const path = "/projects/p/this/is/a/path";
        expect(FileUtils.expandHomeOrProjectFolder(path, Client)).toBe(path);
    });

    test("Starts with home", () => {
        const path = "/Home/this/is/a/path";
        expect(FileUtils.expandHomeOrProjectFolder(path, Client)).toBe(`${Client.homeFolder}this/is/a/path`);
    });

    test("Side effect of adding projects", () => {
        const path = "/p/this/is/a/path";
        expect(FileUtils.expandHomeOrProjectFolder(path, Client)).toBe("/projects" + path);
    });
});

describe("isPreviewSupported", () => {
    test("Is supported", () => {
        const file = FileUtils.mockFile({path: "foo/bar.png", type: "FILE"});
        expect(FileUtils.isFilePreviewSupported(file)).toBeTruthy();
    });


    test("Is not supported, DIRECTORY", () => {
        const folder = FileUtils.mockFile({path: "foo/bar.png", type: "DIRECTORY"});
        expect(FileUtils.isFilePreviewSupported(folder)).toBeFalsy();
    });

    test("Is not supported, sensitivityLevel", () => {
        const file = FileUtils.mockFile({path: "foo/bar.png", type: "FILE"});
        file.sensitivityLevel = SensitivityLevelMap.SENSITIVE;
        expect(FileUtils.isFilePreviewSupported(file)).toBeFalsy();
    });

    test("Is not supported, extension", () => {
        const file = FileUtils.mockFile({path: "foo/bar.pnga", type: "FILE"});
        expect(FileUtils.isFilePreviewSupported(file)).toBeFalsy();
    });
});

describe("isProjectHome", () => {
    test("Starts with 'home'", () => {
        expect(FileUtils.projectIdFromPath("/home/foo/Project")).toBe("foo");
    });

    test("Starts with 'projects'", () => {
        expect(FileUtils.projectIdFromPath("/projects/foo/proj")).toBe("foo");
    });

    test("Doesn't fit", () => {
        expect(FileUtils.projectIdFromPath("/home/foo/Project/pre")).toBe(null);
    });
});

describe("isAnyMockFile", () => {
    test("Single mock file", () => {
        const mockFile = [FileUtils.mockFile({path: "mocky", type: "DIRECTORY", tag: "MOCKED!"})];
        expect(FileUtils.isAnyMockFile(mockFile)).toBeTruthy();
    });

    test("Single non-mock file", () => {
        const mockFile = [FileUtils.mockFile({path: "mocky", type: "DIRECTORY", tag: "MOCKED!"})];
        expect(FileUtils.isAnyMockFile(mockFile)).toBeTruthy();
    });

    test("Multiple files, only one mock file", () => {
        const file = FileUtils.mockFile({path: "mocky", type: "DIRECTORY", tag: "MOCKED!"});
        const files = [file, file, {...file, tag: "mock"}, file, file, file];
        expect(FileUtils.isAnyMockFile(files)).toBeTruthy();
    });
});

describe("isAnyFixedFolder", () => {
    test("Trash folder", () => {
        const trash = [FileUtils.mockFile({path: "/home/foo/Trash", type: "DIRECTORY"})];
        expect(FileUtils.isAnyFixedFolder(trash)).toBeTruthy();
    });

    test("Jobs folder", () => {
        const jobs = [FileUtils.mockFile({path: "/home/foo/Jobs", type: "DIRECTORY"})];
        expect(FileUtils.isAnyFixedFolder(jobs)).toBeTruthy();
    });

    test("Multiple files, only one mock file", () => {
        const files = [FileUtils.mockFile({path: "mocky", type: "DIRECTORY"}), FileUtils.mockFile({path: "mocky_balboa", type: "DIRECTORY"})];
        expect(FileUtils.isAnyFixedFolder(files)).toBeFalsy();
    });
});

test("fileInfoPage", () => {
    expect(FileUtils.fileInfoPage("/foo/bar/..")).toBe("/files/info?path=%2Ffoo");
});

test("filePreviewPage", () => {
    expect(FileUtils.filePreviewPage("/foo/bar/..")).toBe("/files/preview?path=%2Ffoo");
});

test("fileTablePage", () => {
    expect(FileUtils.fileTablePage("/foo/bar/..")).toBe("/files?path=%2Ffoo");
});

describe("isArchiveExtension", () => {
    test("Is not", () => {
        expect(FileUtils.isArchiveExtension("foo_bar.png")).toBeFalsy();
    });

    test("Is .zip", () => {
        expect(FileUtils.isArchiveExtension("foo_bar.png.zip")).toBeTruthy();
    });

    test("Is .tar.gz", () => {
        expect(FileUtils.isArchiveExtension("foo_bar.png.tar.gz")).toBeTruthy();
    });
});

describe("isFavoritesFolder", () => {
    test("Isn't", () => {
        expect(FileUtils.isFavoritesFolder("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isFavoritesFolder("/home/foo/Favorites")).toBeTruthy();
    });
});

describe("isSharesFolder", () => {
    test("Isn't", () => {
        expect(FileUtils.isSharesFolder("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isSharesFolder("/home/foo/Shares")).toBeTruthy();
    });
});

describe("isMyPersonalFolder", () => {
    test("Isn't", () => {
        expect(FileUtils.isMyPersonalFolder("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isMyPersonalFolder(`/projects/foo/Personal/${Client.username}`)).toBeTruthy();
    });
});

describe("isPartOfSomePersonalFolder", () => {
    test("Isn't", () => {
        expect(FileUtils.isPartOfSomePersonalFolder("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isPartOfSomePersonalFolder(`/projects/foo/Personal/${Client.username}`)).toBeTruthy();
    });
});

describe("isPersonalRootFolder", () => {
    test("Isn't", () => {
        expect(FileUtils.isPersonalRootFolder("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isPersonalRootFolder(`/projects/foo/Personal/`)).toBeTruthy();
    });
});


describe("isPartOfProject", () => {
    test("Isn't", () => {
        expect(FileUtils.isPartOfProject("/home/foo/baz")).toBeFalsy();
    });

    test("Is", () => {
        expect(FileUtils.isPartOfProject(`/projects/foo/Personal/${Client.username}`)).toBeTruthy();
    });
});

describe("isProjectHome", () => {
    test("Isn't", () => {
        expect(FileUtils.isProjectHome("/home/foo/baz")).toBeFalsy();
    });

    test("Is through home", () => {
        expect(FileUtils.isProjectHome("/home/foo/Project")).toBeTruthy();
    });

    test("Is through projects", () => {
        expect(FileUtils.isProjectHome("/projects/foo/")).toBeTruthy();
    });
});