// Originally inspired from, but has been rewritten
// https://github.com/quarklemotion/html5-file-selector/blob/e20a0422cd583dfb3b490c0b2ccc2da06de41a25/src/Html5FileSelector.js

interface FileSystemEntry {
    readonly filesystem: unknown,
    readonly fullPath: string;
    readonly isDirectory: boolean;
    readonly isFile: boolean;
    readonly name: string;

    file(callback: (f: File) => void): void
}

interface FileSystemDirectoryEntry extends FileSystemEntry {
    readonly isDirectory: true

    createReader(): FileSystemDirectoryReader
}

interface FileSystemDirectoryReader {
    readEntries(successCallback: (batch: FileSystemEntry[]) => void, errorCallback: (error: unknown) => void): Promise<PackagedFile[]>;
}

type FileListFetcher = () => Promise<PackagedFile[]>;

/*export interface FileUploadEvent {
    rootEntry: {name: string, isDirectory: boolean};
    fetcher: FileListFetcher;
}*/

class FileTraverser {
    private root: FileSystemEntry;
    private stack: FileSystemDirectoryEntry[] = [];
    private didReturnRoot: boolean = false;
    private reader: FileSystemDirectoryReader | null = null;

    constructor(root: FileSystemEntry) {
        this.root = root;
        if (root.isDirectory) {
            this.stack.push(root as FileSystemDirectoryEntry);
        }
    }

    async fetchFiles(): Promise<PackagedFile[] | null> {
        if (!this.root.isDirectory) {
            if (this.didReturnRoot) return null;
            this.didReturnRoot = true;
            return [await getFile(this.root)];
        }

        if (this.reader) {
            const batch = await getDirectoryListing(this.reader);
            if (batch.length === 0) {
                this.reader = null;
            } else {
                const result: PackagedFile[] = [];
                for (const entry of batch) {
                    if (entry.isDirectory) {
                        this.stack.push(entry as FileSystemDirectoryEntry);
                    } else {
                        result.push(await getFile(entry));
                    }
                }
                return result;
            }
        }

        const next = this.stack.pop();
        if (!next) return null;

        this.reader = next.createReader();
        return this.fetchFiles();
    }
}

export interface PackagedFile {
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

function getDirectoryListing(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
    return new Promise((resolve) => {
        reader.readEntries(
            batch => {
                resolve(batch)
            },
            error => {
                resolve([]);
            }
        );
    });
}

const DEFAULT_FILES_TO_IGNORE = [
    '.DS_Store', // OSX indexing file
    'Thumbs.db'  // Windows indexing file
];

function shouldIgnoreFile(file: PackagedFile) {
    return DEFAULT_FILES_TO_IGNORE.indexOf(file.name) >= 0;
}

export async function filesFromDropOrSelectEvent(event): Promise<PackagedFile[]> {
    const dataTransfer = event.dataTransfer;
    if (!dataTransfer) return [];

    const entries: FileSystemEntry[] = [];
    [].slice.call(dataTransfer.items).forEach((listItem) => {
        if (typeof listItem.webkitGetAsEntry === 'function') {
            const entry: FileSystemEntry = listItem.webkitGetAsEntry();
            entries.push(entry);
        } else {
            const theFile: File = listItem.getAsFile();

            const entry: FileSystemEntry = {
                filesystem: 1,
                isDirectory: false,
                isFile: true,
                fullPath: theFile.name,
                name: theFile.name,
                file: (callback: ((f: File) => void)) => {
                    callback(theFile);
                },
            };

            entries.push(entry);
        }
    });

    return await (async () => {
        let result: PackagedFile[] = [];
        for (const entry of entries) {
            const traverser = new FileTraverser(entry);
            while (true) {
                const chunk = await traverser.fetchFiles();
                if (!chunk) break;
                result = [...result, ...chunk];

                // TODO(Brian)
                if (result.length > 10000) break;
            }
        }

        return result;
    })();
}
