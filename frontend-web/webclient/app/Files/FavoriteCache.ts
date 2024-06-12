import {callAPI} from "@/Authentication/DataHook";
import {PageV2} from "@/UCloud";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import metadataApi from "@/UCloud/MetadataDocumentApi";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {UFile} from "@/UCloud/UFile";
import FilesApi from "@/UCloud/FilesApi";

export const sidebarFavoriteCache = new class extends ExternalStoreBase {
    private cache: PageV2<FileMetadataAttached> = {items: [], itemsPerPage: 100};
    private fileInfoCache: Record<string, UFile> = {};
    private isDirty: boolean = false;
    public loading = false;
    public error = "";

    public async fetch() {
        this.loading = true;
        try {
            this.setCache(await callAPI(metadataApi.browse({
                filterActive: true,
                filterTemplate: "Favorite",
                itemsPerPage: 10
            })));
        } catch (error) {
            this.error = errorMessageOrDefault(error, "Failed to fetch favorite files.");
        }
        this.loading = false;
    }

    public async fetchFileInfo(paths: string[]): Promise<void> {
        const promises: Promise<UFile>[] = [];
        for (const p of paths) {
            if (!this.fileInfoCache[p]) {
                promises.push(callAPI(FilesApi.retrieve({id: p})));
            }
        }

        await Promise.all(promises);

        for (const promise of promises) {
            const result = await promise;
            this.fileInfoCache[result.id] = result;
        }
    }

    public fileInfoIfPresent(path: string): UFile | undefined {
        return this.fileInfoCache[path];
    }

    public fileInfosIfPresent(paths: string[]): UFile[] {
        const result: UFile[] = [];
        for (const p of paths) {
            if (this.fileInfoCache[p]) result.push(this.fileInfoCache[p]);
        }
        return result;
    }

    public renameInCached(oldPath: string, newPath: string): void {
        const file = this.cache.items.find(it => it.path === oldPath);
        if (!file) return;

        file.path = newPath;
        this.isDirty = true;
        this.emitChange();
    }

    public add(file: FileMetadataAttached) {
        this.isDirty = true;
        this.cache.items.unshift(file);

        this.emitChange();
    }

    public remove(filePath: string) {
        this.isDirty = true;
        this.cache.items = this.cache.items.filter(it => it.path !== filePath);

        this.emitChange();
    }

    public setCache(page: PageV2<FileMetadataAttached>) {
        this.isDirty = true;
        this.cache = page;

        this.emitChange();
    }

    public getSnapshot(): Readonly<PageV2<FileMetadataAttached>> {
        if (this.isDirty) {
            this.isDirty = false;
            return this.cache = {items: this.cache.items, itemsPerPage: this.cache.itemsPerPage};
        }
        return this.cache;
    }
}



/* 
const testCases: [string, boolean][] = [[".hidden", true], [".hidden.thing", false], ["visible", true], ["visible.thing", false]];
for (const [path, expectedResult] of testCases) {
    console.log(path, expectedResult, "did match:", isLikelyFolder(path) === expectedResult);
} */
export function isLikelyFolder(path: string): boolean {
    const dotCount = path.split(".").length - 1;
    if (dotCount === 0) return true;
    const isHidden = path[0] === ".";
    const isLikelyHiddenFolder = isHidden && dotCount === 1;
    if (isLikelyHiddenFolder) return true;
    return false;
}