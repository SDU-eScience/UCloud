export const UPLOAD_LOCALSTORAGE_PREFIX = "file-upload"
export const FOLDER_UPLOAD_LOCALSTORAGE_PREFIX = "folder-upload"

// NOTE(Dan): This used to have a more specific type but it did not compile for me. I am not sure why but it doesn't
// appear that this specific type was really needed.
export function createLocalStorageUploadKey(path: string): string {
    return `${UPLOAD_LOCALSTORAGE_PREFIX}:${path}`;
}

export function createLocalStorageFolderUploadKey(path: string): string {
    return `${FOLDER_UPLOAD_LOCALSTORAGE_PREFIX}:${path}`;
}

export function removeUploadFromStorage(path: string): void {
    localStorage.removeItem(createLocalStorageUploadKey(path));
    localStorage.removeItem(createLocalStorageFolderUploadKey(path));
}

export class ChunkedFileReader {
    public offset = 0;
    private reader: FileReader;

    constructor(private file: File) {
        this.reader = new FileReader();
    }

    public isEof(): boolean {
        return this.offset >= this.file.size;
    }

    public fileSize(): number {
        return this.file.size;
    }

    public readChunk(chunkSize: number): Promise<ArrayBuffer> {
        return new Promise((resolve, reject) => {
            const chunk = this.file.slice(this.offset, this.offset + chunkSize);
            this.reader.onloadend = (ev) => {
                const target = (ev.target as FileReader);
                if (target.error === null) {
                    const arrayBuffer = target.result as ArrayBuffer;
                    this.offset += arrayBuffer.byteLength;
                    resolve(arrayBuffer);
                } else {
                    reject(target.error);
                }
            };

            this.reader.readAsArrayBuffer(chunk);
        });
    }
}