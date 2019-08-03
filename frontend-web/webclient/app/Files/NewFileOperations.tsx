import {File} from "Files/index";
import * as UF from "UtilityFunctions";
import {delay} from "UtilityFunctions";
import {
    allFilesHasAccessRight, clearTrash,
    CopyOrMove,
    copyOrMoveFilesNew,
    downloadFiles,
    extractArchive, fileInfoPage,
    getFilenameFromPath,
    getParentPath,
    inTrashDir,
    isArchiveExtension,
    isFixedFolder,
    moveToTrash,
    replaceHomeFolder, resolvePath,
    shareFiles,
    updateSensitivity
} from "Utilities/FileUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {AccessRight} from "Types";
import {addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import * as H from 'history';

export interface FileOperationCallback {
    invokeAsyncWork: (fn: () => Promise<void>) => void
    requestFolderCreation: () => void
    requestReload: () => void
    requestFileUpload: () => void
    startRenaming: (file: File) => void
    requestFileSelector: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>
    history: H.History
}

export interface FileOperation {
    text: string
    onClick: (selectedFiles: File[], cb: FileOperationCallback) => void
    disabled: (selectedFiles: File[]) => boolean
    icon?: string
    color?: string
    outline?: boolean
    currentDirectoryMode?: boolean
}

export const defaultFileOperations: FileOperation[] = [
    {
        text: "Upload Files",
        onClick: () => 42,
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
    },
    {
        text: "Empty Trash",
        onClick: (_, cb) => clearTrash({cloud: Cloud, callback: () => cb.requestReload()}),
        disabled: dir => resolvePath(dir[0].path) !== resolvePath(Cloud.trashFolder),
        color: "red",
        currentDirectoryMode: true
    },
    {
        text: "Share",
        onClick: (files) => shareFiles({files, cloud: Cloud}),
        disabled: (files) => !allFilesHasAccessRight("WRITE", files) || !allFilesHasAccessRight("READ", files),
        icon: "share"
    },
    {
        text: "Download",
        onClick: files => downloadFiles(files, () => 42, Cloud),
        disabled: files => !UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files),
        icon: "download"
    },
    {
        text: "Sensitivity",
        onClick: (files, cb) => {
            updateSensitivity({files, cloud: Cloud, onSensitivityChange: () => cb.requestReload()})
        },
        disabled: files => false,
        icon: "verified"
    },
    {
        text: "Copy Path",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Cloud.homeFolder)} copied to clipboard`
        }),
        disabled: files => !UF.inDevEnvironment() || files.length !== 1,
        icon: "chat"
    },
    {
        text: "Move to Trash",
        onClick: (files, cb) =>
            moveToTrash({files, cloud: Cloud, setLoading: () => 42, callback: () => cb.requestReload()}),
        disabled: (files) => (!allFilesHasAccessRight("WRITE", files) ||
            files.some(f => isFixedFolder(f.path, Cloud.homeFolder)) ||
            files.every(({path}) => inTrashDir(path, Cloud))),
        icon: "trash",
        color: "red"
    },
    {
        text: "Delete Files",
        onClick: (files, cb) => {
            const paths = files.map(f => f.path);
            const message = paths.length > 1 ? `Delete ${paths.length} files?` :
                `Delete file ${getFilenameFromPath(paths[0])}`;

            addStandardDialog({
                title: "Delete files",
                message,
                confirmText: "Delete files",
                onConfirm: () => {
                    cb.invokeAsyncWork(async () => {
                        const promises: { status?: number, response?: string }[] =
                            await Promise.all(paths.map(path => Cloud.delete("/files", {path}))).then(it => it).catch(it => it);
                        const failures = promises.filter(it => it.status).length;
                        if (failures > 0) {
                            snackbarStore.addSnack({
                                message: promises.filter(it => it.response).map(it => it).join(", "),
                                type: SnackType.Failure
                            });
                        } else {
                            snackbarStore.addSnack({message: "Files deleted", type: SnackType.Success});
                        }
                    });
                }
            });
        },
        disabled: (files) => !files.every(f => getParentPath(f.path) === Cloud.trashFolder),
        icon: "trash",
        color: "red"
    },
    {
        text: "Properties",
        onClick: (files, cb) => cb.history.push(fileInfoPage(files[0].path)),
        disabled: (files) => files.length !== 1,
        icon: "properties", color: "blue"
    },
    {
        text: "Extract archive",
        onClick: (files, cb) => cb.invokeAsyncWork(() =>
            extractArchive({files, cloud: Cloud, onFinished: () => cb.requestReload()})
        ),
        disabled: (files) => !files.every(it => isArchiveExtension(it.path)),
        icon: "open"
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files: File[]) => files.length === 1 && !allFilesHasAccessRight(AccessRight.WRITE, files),
        icon: "rename"
    },
    {
        text: "Random Work",
        onClick: (_, cb) => cb.invokeAsyncWork(async () => {
            await delay(2000);
            console.log("Work is done!");
        }),
        disabled: () => false,
        currentDirectoryMode: true,
        color: "green"
    },
    {
        text: "Copy",
        onClick: async (files, cb) => {
            const target = await cb.requestFileSelector(true, true);
            if (target === null) return;
            cb.invokeAsyncWork(async () => {
                try {
                    await copyOrMoveFilesNew(CopyOrMove.Copy, files, target);
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files) => !allFilesHasAccessRight("WRITE", files),
        icon: "copy",
    },
    {
        text: "Move",
        onClick: async (files, cb) => {
            const target = await cb.requestFileSelector(true, true);
            if (target === null) return;
            cb.invokeAsyncWork(async () => {
                try {
                    await copyOrMoveFilesNew(CopyOrMove.Move, files, target);
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files) => !allFilesHasAccessRight("WRITE", files),
        icon: "move",
    }
];
