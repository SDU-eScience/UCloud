import {callAPI} from "@/Authentication/DataHook";
import {PageV2} from "@/UCloud";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import metadataApi from "@/UCloud/MetadataDocumentApi";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";

export const sidebarFavoriteCache = new class extends ExternalStoreBase {
    private cache: PageV2<FileMetadataAttached> = {items: [], itemsPerPage: 100}
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