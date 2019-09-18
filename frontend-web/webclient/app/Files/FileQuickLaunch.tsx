import {File} from "Files/index";
import * as H from "history";
import {
    allFilesHasAccessRight,
    isAnyMockFile,
    isAnySharedFs,
    replaceHomeFolder,
    resolvePath
} from "Utilities/FileUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {FileOperation} from "Files/FileOperations";
import * as UF from "UtilityFunctions";
import {AccessRight} from "Types";

export interface FileQuickLaunchCallback {
    invokeAsyncWork: (fn: () => Promise<void>) => void;
    requestFolderCreation: () => void;
    requestReload: () => void;
    requestFileUpload: () => void;
    startRenaming: (file: File) => void;
    requestFileSelector: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>;
    history: H.History;
}


export interface FileQuickLaunchApp {
    text: string;
    onClick: (selectedFiles: File[], cb: FileQuickLaunchCallback) => void;
    disabled: (selectedFiles: File[]) => boolean;
    icon?: string;
    color?: string;
    outline?: boolean;
    currentDirectoryMode?: boolean;
}

export const defaultFileQuickLaunchApps: FileQuickLaunchApp[] = [
    {
        text: "Copy Path",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Cloud.homeFolder)} copied to clipboard`
        }),
        disabled: files => !UF.inDevEnvironment() || files.length !== 1 || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "chat"
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files: File[]) => files.length === 1 && !allFilesHasAccessRight(AccessRight.WRITE, files) ||
            isAnyMockFile(files) || isAnySharedFs(files),
        icon: "rename"
    },
    {
        text: "Upload Files",
        onClick: (_, cb) => cb.requestFileUpload(),
        disabled: dir => resolvePath(dir[0].path) === resolvePath(Cloud.trashFolder),
        color: "blue",
        currentDirectoryMode: true
    },
    {
        text: "New Folder",
        onClick: (_, cb) => cb.requestFolderCreation(),
        disabled: dir => resolvePath(dir[0].path) === resolvePath(Cloud.trashFolder),
        color: "blue",
        outline: true,
        currentDirectoryMode: true
    }
];
