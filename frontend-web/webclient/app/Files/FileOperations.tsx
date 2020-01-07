import {Client} from "Authentication/HttpClientInstance";
import {File} from "Files/index";
import * as H from "history";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {AccessRight} from "Types";
import {IconName} from "ui-components/Icon";
import {Upload} from "Uploader";
import {UploadPolicy} from "Uploader/api";
import {newUpload} from "Uploader/Uploader";
import {
    allFilesHasAccessRight,
    clearTrash,
    CopyOrMove,
    copyOrMoveFilesNew,
    downloadFiles,
    extractArchive,
    fileInfoPage,
    filePreviewPage,
    fileTablePage,
    getFilenameFromPath,
    getParentPath,
    inTrashDir,
    isAnyMockFile,
    isAnySharedFs,
    isArchiveExtension,
    isFixedFolder,
    moveToTrash,
    replaceHomeFolder,
    resolvePath,
    shareFiles,
    updateSensitivity
} from "Utilities/FileUtilities";
import {addStandardDialog} from "UtilityComponents";
import * as UF from "UtilityFunctions";

export interface FileOperationCallback {
    invokeAsyncWork: (fn: () => Promise<void>) => void;
    requestFolderCreation: () => void;
    requestReload: () => void;
    requestFileUpload: () => void;
    startRenaming: (file: File) => void;
    createNewUpload: (newUpload: Upload) => void;
    requestFileSelector: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>;
    history: H.History;
}

export interface FileOperation {
    text: string;
    onClick: (selectedFiles: File[], cb: FileOperationCallback) => void;
    disabled: (selectedFiles: File[]) => boolean;
    icon?: IconName;
    color?: string;
    outline?: boolean;
    currentDirectoryMode?: boolean;
}

export const defaultFileOperations: FileOperation[] = [
    {
        text: "Upload Files",
        onClick: (_, cb) => cb.requestFileUpload(),
        disabled: dir => resolvePath(dir[0].path) === resolvePath(Client.trashFolder),
        color: "blue",
        currentDirectoryMode: true
    },
    {
        text: "New Folder",
        onClick: (_, cb) => cb.requestFolderCreation(),
        disabled: dir => resolvePath(dir[0].path) === resolvePath(Client.trashFolder),
        color: "blue",
        outline: true,
        currentDirectoryMode: true
    },
    {
        text: "Empty Trash",
        onClick: (_, cb) => clearTrash({client: Client, callback: () => cb.requestReload()}),
        disabled: dir => resolvePath(dir[0].path) !== resolvePath(Client.trashFolder),
        color: "red",
        currentDirectoryMode: true
    },
    {
        text: "Copy Path",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Client.homeFolder)} copied to clipboard`
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
        text: "Upload",
        onClick: (files, cb) => {
            const input = document.createElement("input");
            input.type = "file";
            input.onchange = () => {
                const inputFiles = input.files;
                if (!inputFiles) return;

                const file = inputFiles.item(0);
                if (!file) return;

                const upload = newUpload(file, files[0].path);
                upload.resolution = UploadPolicy.OVERWRITE;
                upload.sensitivity = files[0].ownSensitivityLevel ?? "INHERIT";
                cb.createNewUpload(upload);
            };

            input.click();
        },
        disabled: (files) => files.length !== 1 || !allFilesHasAccessRight("WRITE", files) ||
            files[0].fileType !== "FILE" || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "upload"
    },
    {
        text: "Download",
        onClick: files => downloadFiles(files, Client),
        disabled: files => !UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files) ||
            isAnyMockFile(files) || isAnySharedFs(files),
        icon: "download"
    },
    {
        text: "Share",
        onClick: (files) => shareFiles({files, client: Client}),
        disabled: (files) => !allFilesHasAccessRight("WRITE", files) || !allFilesHasAccessRight("READ", files) ||
            isAnyMockFile(files) || isAnySharedFs(files),
        icon: "share"
    },
    {
        text: "Sensitivity",
        onClick: (files, cb) =>
            updateSensitivity({files, client: Client, onSensitivityChange: () => cb.requestReload()}),
        disabled: files => isAnyMockFile(files) || !allFilesHasAccessRight("WRITE", files) || isAnySharedFs(files),
        icon: "verified"
    },
    {
        text: "Copy",
        onClick: async (files, cb) => {
            const target = await cb.requestFileSelector(true, true);
            if (target === null) return;
            cb.invokeAsyncWork(async () => {
                try {
                    await copyOrMoveFilesNew(CopyOrMove.Copy, files, target);
                    cb.requestReload();
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files) => !allFilesHasAccessRight("WRITE", files) || isAnyMockFile(files) || isAnySharedFs(files),
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
                    cb.requestReload();
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files) => !allFilesHasAccessRight("WRITE", files) || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "move",
    },
    {
        text: "Extract archive",
        onClick: (files, cb) => cb.invokeAsyncWork(() =>
            extractArchive({files, client: Client, onFinished: () => cb.requestReload()})
        ),
        disabled: (files) => !files.every(it => isArchiveExtension(it.path)) || isAnyMockFile(files) ||
            isAnySharedFs(files),
        icon: "extract"
    },
    {
        text: "View Parent",
        onClick: (files, cb) => {
            cb.history.push(fileTablePage(getParentPath(files[0].path)));
        },
        disabled: files => files.length !== 1,
        icon: "open"
    },
    {
        text: "Preview",
        onClick: (files, cb) => cb.history.push(filePreviewPage(files[0].path)),
        disabled: (files) => !UF.isExtPreviewSupported(UF.extensionFromPath(files[0].path)) ||
            (!UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files) || isAnyMockFile(files) ||
                isAnySharedFs(files)),
        icon: "search"
    },
    {
        text: "Properties",
        onClick: (files, cb) => cb.history.push(fileInfoPage(files[0].path)),
        disabled: (files) => files.length !== 1 || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "properties"
    },
    {
        text: "Move to Trash",
        onClick: (files, cb) =>
            moveToTrash({files, client: Client, setLoading: () => 42, callback: () => cb.requestReload()}),
        disabled: (files) => (!allFilesHasAccessRight("WRITE", files) ||
            files.some(f => isFixedFolder(f.path, Client.homeFolder)) ||
            files.every(({path}) => inTrashDir(path, Client))) || isAnyMockFile(files) || isAnySharedFs(files),
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
                        const promises: Array<{status?: number, response?: string}> =
                            await Promise.all(paths.map(path => Client.delete("/files", {path})))
                                .then(it => it).catch(it => it);
                        const failures = promises.filter(it => it.status).length;
                        if (failures > 0) {
                            snackbarStore.addFailure(promises.filter(it => it.response).map(it => it).join(", "));
                        } else {
                            snackbarStore.addSnack({message: "Files deleted", type: SnackType.Success});
                        }
                    });
                }
            });
        },
        disabled: (files) => !files.every(f => getParentPath(f.path) === Client.trashFolder) || isAnyMockFile(files) ||
            isAnySharedFs(files),
        icon: "trash",
        color: "red"
    },

    // Shared File Systems
    {
        text: "Delete",
        onClick: (files, cb) => {
            addStandardDialog({
                title: "Delete application file systems",
                message: `Do you want to delete ${files.length} shared file systems? The files cannot be recovered.`,
                confirmText: "Delete",

                onConfirm: () => {
                    cb.invokeAsyncWork(async () => {
                        const promises: Array<{status?: number, response?: string}> =
                            await Promise
                                .all(files.map(it => Client.delete(`/app/fs/${it.fileId}`, {})))
                                .then(it => it)
                                .catch(it => it);

                        const failures = promises.filter(it => it.status).length;
                        if (failures > 0) {
                            snackbarStore.addFailure(promises.filter(it => it.response).map(it => it).join(", "));
                        } else {
                            snackbarStore.addSnack({message: "File systems deleted", type: SnackType.Success});
                        }

                        cb.requestReload();
                    });
                }
            });
        },
        disabled: files => files.some(it => it.fileType !== "SHARED_FS"),
        icon: "trash",
        color: "red"
    }
];
