import {dialogStore} from "@/Dialog/DialogStore";
import * as React from "react";
import {FileType} from ".";
import {UFile} from "@/UCloud/UFile";
import FilesApi, {ExtraFileCallbacks} from "@/UCloud/FilesApi";
import {callAPIWithErrorHandler} from "@/Authentication/DataHook";
import FavoritesBrowse from "./FavoritesBrowse";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";



type FileOperation = Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks>
export function addFavoriteSelect(onSelect: (file: UFile) => void, isFileAllowed: (file: UFile) => boolean | string, allowedTypes: FileType[], text: string, navigateToFolder: (path: string) => void) {
    dialogStore.addDialog(<FavoritesBrowse navigateToFolder={navigateToFolder} selection={{
        text,
        async onClick(res) {
            const result = "path" in res ? await callAPIWithErrorHandler(FilesApi.retrieve({id: res.path})) : res;
            if (result) {
                if (allowedTypes.includes(result.status.type)) {
                    const allowed = isFileAllowed(result);

                    if (typeof allowed === "string") {
                        snackbarStore.addFailure(allowed, false);
                    } else if (allowed) {
                        onSelect(result);
                    } else {
                        // TODO(Jonas): Handle? This would be file-type, but we already check that in `allowedTypes`.
                    }
                }
            }
        },
        show(res) {
            if ("path" in res) return true;
            else return isFileAllowed(res);
        }
    }} />, () => void 0);
}

export function folderFavoriteSelection(
    onSelect: (file: UFile) => void,
    isFileAllowed: (file: UFile) => boolean | string,
    navigateToFolder: (path: string, projectId?: string) => void
): FileOperation {
    return {
        text: "Favorites",
        enabled: () => true,
        onClick: () => {
            dialogStore.failure();
            addFavoriteSelect(onSelect, isFileAllowed, ["DIRECTORY"], "Use", navigateToFolder);
        },
        shortcut: ShortcutKey.F,
        icon: "starFilled"
    }
}

export function fileFavoriteSelection(
    onSelect: (file: UFile) => void,
    isFileAllowed: (file: UFile) => boolean | string,
    navigateToFolder: (path: string, projectId?: string) => void
): FileOperation {
    return {
        text: "Favorites",
        enabled: () => true,
        onClick: () => {
            dialogStore.failure();
            addFavoriteSelect(onSelect, isFileAllowed, ["FILE"], "Use", navigateToFolder);
        },
        shortcut: ShortcutKey.F,
        icon: "starFilled"
    }
}
