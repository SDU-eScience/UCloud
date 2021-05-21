const UPLOAD_LOCALSTORAGE_PREFIX = "file-upload"

export function createLocalStorageUploadKey(path: string): string {
    return `${UPLOAD_LOCALSTORAGE_PREFIX}:${path}`;
}

export function removeUploadFromStorage(path: string): void {
    localStorage.removeItem(`${UPLOAD_LOCALSTORAGE_PREFIX}:${path}`);
}

export class ChunkedFileReader {
    public offset = 0;

    constructor(private file: File) {
    }

    public isEof(): boolean {
        return this.offset >= this.file.size;
    }

    public fileSize(): number {
        return this.file.size;
    }

    public readChunk(chunkSize: number): Promise<ArrayBuffer> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            const chunk = this.file.slice(this.offset, this.offset + chunkSize);
            reader.onloadend = (ev) => {
                const target = (ev.target as FileReader);
                if (target.error === null) {
                    const arrayBuffer = target.result as ArrayBuffer;
                    this.offset += arrayBuffer.byteLength;
                    resolve(arrayBuffer);
                } else {
                    reject(target.error);
                }
            };

            reader.readAsArrayBuffer(chunk);
        });
    }
}
