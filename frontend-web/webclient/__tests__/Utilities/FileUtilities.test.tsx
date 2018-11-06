import * as FileUtils from "Utilities/FileUtilities";
import { mockFiles_SensitivityConfidential } from "../mock/Files";
import Cloud from "Authentication/lib";
import { createMemoryHistory } from "history";

describe("File Operations", () => {
    test("No file operations", () =>
        expect(FileUtils.AllFileOperations(false, false, false, false)).toEqual([])
    );
})

describe("To file text", () => {
    test("Single file", () => {
        const firstFile = mockFiles_SensitivityConfidential.items[0];
        expect(FileUtils.toFileText([firstFile])).toBe("1 file selected.");
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
        expect(FileUtils.replaceHomeFolder("/home/user@mail.co.uk/subFolder/withSomething", mockHomeFolder)).toBe("Home/subFolder/withSomething")
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

describe("Is Invalid Path Name", () => {
    test("Valid path with no provided filePaths", () => {
        expect(FileUtils.isInvalidPathName("valid_path", [])).toBe(false);
    });

    test("Valid path with provided filePaths", () => {
        expect(FileUtils.isInvalidPathName("valid_path", ["path1", "path2"])).toBe(false);
    });

    test("Valid path but with existing filePath", () => {
        expect(FileUtils.isInvalidPathName("valid_path", ["valid_path", "path2"])).toBe(true);
    });

    test("Invalid path, consisting of .", () => {
        expect(FileUtils.isInvalidPathName(".", [])).toBe(true);
    });

    test("Invalid path, consisting of empty string", () => {
        expect(FileUtils.isInvalidPathName("", [])).toBe(true);
    });

    test("Invalid path, containing \"..\"", () => {
        expect(FileUtils.isInvalidPathName("..", [])).toBe(true);
    });

    test("Invalid path, containing \"/\"", () => {
        expect(FileUtils.isInvalidPathName("/", [])).toBe(true);
    });
});

// Doesn't return a value, so missing something we can test for
describe.skip("Move copy operations", () => {
    const ops = {
        showFileSelector: (show: boolean) => undefined,
        setDisallowedPaths: (paths: string[]) => undefined,
        setFileSelectorCallback: (callback?: Function) => undefined,
        fetchPageFromPath: (path: string) => undefined
    };

    const firstFile = mockFiles_SensitivityConfidential.items[0];
    const firstThreeFiles = mockFiles_SensitivityConfidential.items.slice(0, 3);

    test("Move single file", () => {
        FileUtils.move([firstFile], ops, new Cloud())
    });

    test("Move multiple files file", () => {
        FileUtils.move(firstThreeFiles, ops, new Cloud())
    });
});

describe("File Operations", () => {
    describe("are disabled", () => {
        describe("Not", () => {
            describe("FileStateLess", () => {
                const ops = FileUtils.StateLessOperations();
                const share = (ops[0]);
                const download = (ops[1]);
                const files = mockFiles_SensitivityConfidential.items;

                test("Share", () => {
                    expect(share.disabled(files, new Cloud())).toBe(false)
                });

                test("Download", () => {
                    expect(download.disabled(files, new Cloud())).toBe(true)
                });

                test("Download", () => {
                    expect(download.disabled([files[0]], new Cloud())).toBe(false)
                });
            });

            describe("FileSelectorOperations", () => {
                const ops = FileUtils.FileSelectorOperations({
                    showFileSelector: (show: boolean) => undefined,
                    setDisallowedPaths: (paths: string[]) => undefined,
                    setFileSelectorCallback: (callback?: Function) => undefined,
                    fetchPageFromPath: (path: string) => undefined
                });

                const copy = ops[0];
                const move = ops[1];

                const files = mockFiles_SensitivityConfidential.items;

                test("Copy", () => {
                    expect(copy.disabled(files, new Cloud())).toBe(false)
                });

                test("Move", () => {
                    expect(move.disabled(files, new Cloud())).toBe(false)
                });
            });

            describe("DeleteFileOperation", () => {
                const deleteOp = FileUtils.DeleteFileOperation(() => undefined)[0];
                const files = mockFiles_SensitivityConfidential.items;


                test("Delete", () => {
                    expect(deleteOp.disabled(files, new Cloud())).toBe(false);
                });
            });

            describe("HistoryFilesOperations", () => {
                const ops = FileUtils.HistoryFilesOperations(createMemoryHistory())
                const properties = ops[0];
                const predicatedProjects = ops[1];
                const files = mockFiles_SensitivityConfidential.items;
                const projectFile = files[9];

                test("Properties", () => {
                    expect(properties.disabled(files.slice(0, 1), new Cloud())).toBe(false);
                });

                test("Predicated Operation project, false", () => {
                    expect(predicatedProjects.predicate([files[0]], new Cloud())).toBe(false);
                });

                test("Predicated Operation project, true", () => {
                    expect(predicatedProjects.predicate([projectFile, projectFile], new Cloud())).toBe(true);
                });

                test("Predicated Operation project, onTrue, not disabled", () => {
                    expect(predicatedProjects.onTrue.disabled([projectFile], new Cloud())).toBe(false);
                });

                test("Predicated Operation project, onFalse, disabled", () => {
                    expect(predicatedProjects.onFalse.disabled(files.slice(0, 2), new Cloud())).toBe(true);
                });

                test("Predicated Operation project, onFalse, not disabled", () => {
                    expect(predicatedProjects.onFalse.disabled([files[1] /* Directory */], new Cloud())).toBe(false);
                });
            });
        });
    });
});

test("Annotation to string", () => {
    expect(FileUtils.annotationToString("P")).toBe("Project");
});