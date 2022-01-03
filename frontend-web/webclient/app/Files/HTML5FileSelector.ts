/*
                               The MIT License

Project jumpstarted using kriasoft/babel-starter-kit, which is
Copyright (c) 2015-2016 Konstantin Tarkus, Kriasoft LLC. All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

 */

// NOTE: The following code snippet has been ported from JavaScript to TypeScript. The original code location was:
// https://github.com/quarklemotion/html5-file-selector/blob/e20a0422cd583dfb3b490c0b2ccc2da06de41a25/src/Html5FileSelector.js

// The code has since been, quite heavily, modified to improve performance.

import {doNothing} from "@/UtilityFunctions";

interface FileSystemEntry {
    readonly filesystem: unknown,
    readonly fullPath: string;
    readonly isDirectory: boolean;
    readonly isFile: boolean;
    readonly name: string;

    file(callback: (File) => void): File
}

interface FileSystemDirectoryEntry extends FileSystemEntry {
    readonly isDirectory: true

    createReader(): FileSystemDirectoryReader
}

interface FileSystemDirectoryReader {
    readEntries(successCallback: (batch: FileSystemEntry[]) => void, errorCallback: (error: unknown) => void): Promise<PackagedFile[]>;
}

type FileListFetcher = () => Promise<PackagedFile[]>;

export interface FileUploadEvent {
    rootEntry: {name: string, isDirectory: boolean};
    fetcher: FileListFetcher;
}

function traverseDirectory(entry: FileSystemDirectoryEntry): FileListFetcher {
    let reader: FileSystemDirectoryReader | null | undefined = entry.createReader();
    const stack: FileSystemDirectoryEntry[] = [];

    function fetchNewReader(): boolean {
        if (!reader) {
            reader = stack.pop()?.createReader();
            if (!reader) {
                return false;
            }
        }
        return true;
    }

    return function readEntries(): Promise<PackagedFile[]> {
        // According to the FileSystem API spec, readEntries() must be called until
        // it calls the callback with an empty array.
        if (!fetchNewReader()) return Promise.resolve([]);

        const promises: Promise<PackagedFile>[] = [];

        return new Promise((resolve) => {
            reader!.readEntries((batchEntries) => {
                if (!batchEntries.length) {
                    // Done iterating this particular directory
                } else {
                    for (const batchEntry of batchEntries) {
                        // NOTE(Dan): Performance starts getting bad once we have folders with more than 5000 files
                        //   We might need to consider chunking files from a single folder. It is a bit weird this isn't
                        //   done for us, given that this is clearly why the spec is laid out as it is.
                        if (batchEntry.isDirectory) {
                            stack.push(batchEntry as FileSystemDirectoryEntry);
                        } else {
                            promises.push(new Promise<PackagedFile>((resolveFile) => {
                                batchEntry.file((file) => {
                                    if (shouldIgnoreFile(file)) return;
                                    resolveFile(packageFile(file, entry));
                                });
                            }));
                        }
                    }
                }

                resolve(Promise.all(promises));
            }, doNothing);
        });
    }
}

interface PackagedFile {
    fullPath: string;
    size: number;
    fileObject: File;
    lastModifiedDate: any;
    name: string;
    webkitRelativePath: any;
    lastModified: number;
    type: string;
    isDirectory: boolean;
}

function packageFile(file: File, entry?: FileSystemEntry): PackagedFile {
    const fileTypeOverride = '';

    return {
        fileObject: file, // provide access to the raw File object (required for uploading)
        fullPath: entry ? copyString(entry.fullPath) : file.name,
        lastModified: file.lastModified,
        lastModifiedDate: file["lastModifiedDate"],
        name: file.name,
        size: file.size,
        type: file.type ? file.type : fileTypeOverride,
        webkitRelativePath: file["webkitRelativePath"],
        isDirectory: entry?.isDirectory ?? false
    };
}

/* What is this for? Copying and ensuring that modifying one string doesn't modify original? */
function copyString(aString: string): string {
    return ` ${aString}`.slice(1);
}

function getFile(entry: FileSystemEntry): Promise<PackagedFile> {
    return new Promise((resolve) => {
        entry.file((file) => {
            resolve(packageFile(file, entry));
        });
    });
}

const DEFAULT_FILES_TO_IGNORE = [
    '.DS_Store', // OSX indexing file
    'Thumbs.db'  // Windows indexing file
];

function shouldIgnoreFile(file: PackagedFile) {
    return DEFAULT_FILES_TO_IGNORE.indexOf(file.name) >= 0;
}

function once<T>(callback: () => T, defaultValue: T): () => T {
    let didFetch = false;
    return () => {
        if (didFetch) return defaultValue;
        didFetch = true;
        return callback();
    };
}

export function fetcherFromDataTransfer(dataTransfer: DataTransfer): FileUploadEvent[] {
    const fetchers: FileUploadEvent[] = [];

    [].slice.call(dataTransfer.items).forEach((listItem) => {
        if (typeof listItem.webkitGetAsEntry === 'function') {
            const entry: FileSystemEntry = listItem.webkitGetAsEntry();

            if (entry) {
                if (entry.isDirectory) {
                    fetchers.push({
                        rootEntry: {isDirectory: true, name: copyString(entry.name)},
                        fetcher: traverseDirectory(entry as FileSystemDirectoryEntry)
                    });
                } else {
                    fetchers.push({
                        rootEntry: {isDirectory: false, name: copyString(entry.name)},
                        fetcher: once(async () => [await getFile(entry)], Promise.resolve([]))
                    });
                }
            }
        } else {
            const file: File = listItem.getAsFile();
            fetchers.push({
                rootEntry: {isDirectory: false, name: file.name},
                fetcher: once(async () => [packageFile(file)], Promise.resolve([]))
            });
        }
    });

    return fetchers;
}

export function fetcherFromDropOrSelectEvent(event): FileUploadEvent[] {
    const dataTransfer = event.dataTransfer;
    if (dataTransfer && dataTransfer.items) {
        return fetcherFromDataTransfer(dataTransfer);
    }

    const files: PackagedFile[] = [];
    const dragDropFileList = dataTransfer && dataTransfer.files;
    const inputFieldFileList = event.target && event.target.files;
    const fileList = dragDropFileList || inputFieldFileList || [];
    for (let i = 0; i < fileList.length; i++) {
        files.push(packageFile(fileList[i], undefined));
    }

    return files.map(it => {
        return {
            rootEntry: {isDirectory: false, name: it.name},
            fetcher: once(() => Promise.resolve([it]), Promise.resolve([]))
        }
    });
}
