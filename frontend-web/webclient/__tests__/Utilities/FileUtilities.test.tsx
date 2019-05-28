import {
    allFileOperations,
    sizeToString,
    toFileText,
    getFilenameFromPath,
    replaceHomeFolder,
    filepathQuery,
    fileLookupQuery,
    isInvalidPathName,
    copyOrMoveFiles,
    StateLessOperations,
    FileSelectorOperations,
    MoveFileToTrashOperation,
    HistoryFilesOperations,
    fileTablePage
} from "../../app/Utilities/FileUtilities";
import { mockFiles_SensitivityConfidential } from "../mock/Files";

test("silencer", () => { });

describe("File Operations", () => {
    test("No file operations", () =>
        expect(allFileOperations({
            setLoading: () => undefined
        })).toEqual([])
    );
})

describe("To file text", () => {
    test("Single file", () => {
        const firstFile = mockFiles_SensitivityConfidential.items[0];
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
        expect(replaceHomeFolder("/home/user@mail.co.uk/subFolder/withSomething", mockHomeFolder)).toBe("Home/subFolder/withSomething")
    );

    const noHomeFolder = "NotHomeFolder/subfolder/";
    test("Replace homefolder, no homefolder", () =>
        expect(replaceHomeFolder(noHomeFolder, mockHomeFolder)).toBe(`${noHomeFolder}`)
    );
});


describe("Filepath query", () => {
    test("Defaults", () =>
        expect(filepathQuery("path", 0, 25)).toBe(
            "files?path=path&itemsPerPage=25&page=0&order=ASCENDING&sortBy=path"
        )
    )
});

/*

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
        move([firstFile], ops, new Cloud())
    });

    test("Move multiple files file", () => {
        move(firstThreeFiles, ops, new Cloud())
    });
});
*/

/*
describe("File Operations", () => {
    describe("are disabled", () => {
        describe("Not", () => {
            describe("FileStateLess", () => {
                const ops = StateLessOperations();
                const share = (ops[0]);
                const download = (ops[1]);
                const files = mockFiles_SensitivityConfidential.items;

                test.skip("Share", () => {
                    expect(share.disabled(files, new Cloud())).toBe(false)
                });

                test.skip("Download", () => {
                    expect(download.disabled(files, new Cloud())).toBe(true)
                });

                test.skip("Download", () => {
                    expect(download.disabled([files[0]], new Cloud())).toBe(false)
                });
            });

            describe("FileSelectorOperations", () => {
                const ops = FileSelectorOperations({
                    showFileSelector: (show) => undefined,
                    setDisallowedPaths: (paths) => undefined,
                    setFileSelectorCallback: (callback) => undefined,
                    fetchPageFromPath: (path) => undefined
                });

                const copy = ops[0];
                const move = ops[1];

                const files = mockFiles_SensitivityConfidential.items;

                test.skip("Copy", () => {
                    expect(copy.disabled(files, new Cloud())).toBe(false)
                });

                test.skip("Move", () => {
                    expect(move.disabled(files, new Cloud())).toBe(false)
                });
            });

            describe("DeleteFileOperation", () => {
                const deleteOp = MoveFileToTrashOperation(() => undefined)[0];
                const files = mockFiles_SensitivityConfidential.items;


                test("Delete", () => {
                    expect(deleteOp.disabled(files, new Cloud())).toBe(true);
                });

                test.skip("Move to trash", () => false)
            });

            describe("HistoryFilesOperations", () => {
                const ops = HistoryFilesOperations(createMemoryHistory())
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

                test.skip("Predicated Operation project, onFalse, not disabled", () => {
                    expect(predicatedProjects.onFalse.disabled([files[1]], new Cloud())).toBe(false);
                });
            });
        });
    });
}); */