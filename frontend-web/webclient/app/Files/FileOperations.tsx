import {Client} from "Authentication/HttpClientInstance";
import {File} from "Files/index";
import * as H from "history";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {AccessRight} from "Types";
import {IconName} from "ui-components/Icon";
import {Upload} from "Uploader";
import {UploadPolicy} from "Uploader/api";
import {newUpload} from "Uploader/Uploader";
import {
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
    isAnyFixedFolder,
    isAnyMockFile,
    isArchiveExtension, isPartOfProject, isPartOfSomePersonalFolder, isPersonalRootFolder,
    isTrashFolder,
    moveToTrash,
    shareFiles,
    updateSensitivity
} from "Utilities/FileUtilities";
import {addStandardDialog} from "UtilityComponents";
import * as UF from "UtilityFunctions";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import {
    explainPersonalRepo,
    promptDeleteRepository,
    repositoryName,
    updatePermissionsPrompt
} from "Utilities/ProjectUtilities";
import {FilePermissions} from "Files/permissions";
import {ProjectName} from "Project";

export interface FileOperationCallback {
    permissions: FilePermissions;
    invokeAsyncWork: (fn: () => Promise<void>) => void;
    requestFolderCreation: () => void;
    requestReload: () => void;
    requestFileUpload: () => void;
    startRenaming: (file: File) => void;
    createNewUpload: (newUpload: Upload) => void;
    projects: ProjectName[],
    requestFileSelector: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>;
    history: H.History;
}

export enum FileOperationRepositoryMode {
    REQUIRED,
    DISALLOW,
    ANY
}

export interface FileOperation {
    text: string;
    onClick: (selectedFiles: File[], cb: FileOperationCallback) => void;
    disabled: (selectedFiles: File[], cb: FileOperationCallback) => boolean;
    icon?: IconName;
    color?: string;
    outline?: boolean;
    currentDirectoryMode?: boolean;
    repositoryMode?: FileOperationRepositoryMode;
}

export const defaultFileOperations: FileOperation[] = [
    {
        text: "Upload Files",
        onClick: (_, cb) => cb.requestFileUpload(),
        disabled: dir => isTrashFolder(dir[0].path),
        color: "blue",
        currentDirectoryMode: true
    },
    {
        text: "New Folder",
        onClick: (_, cb) => cb.requestFolderCreation(),
        disabled: ([file]) => isTrashFolder(file.path),
        color: "blue",
        outline: true,
        currentDirectoryMode: true
    },
    {
        text: "Empty Trash",
        onClick: ([file], cb) => clearTrash({client: Client, trashPath: file.path, callback: () => cb.requestReload()}),
        disabled: ([dir]) => {
            return !isTrashFolder(dir.path);
        },
        color: "red",
        currentDirectoryMode: true
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files, cb) => {
            if (files.length !== 1) return true;
            else if (isAnyMockFile(files)) return true;
            else if (isAnyFixedFolder(files)) return true;
            else if (isPartOfSomePersonalFolder(files[0].path)) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.WRITE);
        },
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
        disabled: (files, cb) => {
            if (files.length !== 1) return true;
            else if (files[0].fileType !== "FILE") return true;
            else if (isAnyMockFile(files)) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.WRITE);
        },
        icon: "upload"
    },
    {
        text: "Download",
        onClick: files => downloadFiles(files, Client),
        disabled: (files, cb) => {
            if (!UF.downloadAllowed(files)) return true;
            else if (isAnyMockFile(files)) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.READ);
        },
        icon: "download"
    },
    {
        text: "Share",
        onClick: (files) => shareFiles({files, client: Client}),
        disabled: (files, cb) => {
            if (files.find(it => isPartOfProject(it.path)) !== undefined) return true;
            if (isAnyMockFile(files)) return true;
            else if (isAnyFixedFolder(files)) return true;
            else return !files.every(it => it.ownerName === Client.username);
        },
        icon: "share"
    },
    {
        text: "Sensitivity",
        onClick: (files, cb) =>
            updateSensitivity({files, client: Client, onSensitivityChange: () => cb.requestReload()}),
        disabled: (files, cb) => {
            if (isAnyMockFile(files)) return true;
            else if (isAnyFixedFolder(files)) return true;
            else if (files.find(it => isPartOfSomePersonalFolder(it.path)) !== undefined) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.WRITE);
        },
        icon: "sensitivity"
    },
    {
        text: "Copy",
        onClick: async (files, cb) => {
            const target = await cb.requestFileSelector(true, true);
            if (target === null) return;
            cb.invokeAsyncWork(async () => {
                try {
                    await copyOrMoveFilesNew(CopyOrMove.Copy, files, target, cb.projects);
                    cb.requestReload();
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files, cb) => {
            if (isAnyFixedFolder(files)) return true;
            else if (isAnyMockFile(files)) return true;
            else if (files.find(it => isPartOfSomePersonalFolder(it.path)) !== undefined) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.WRITE);
        },
        icon: "copy",
    },
    {
        text: "Move",
        onClick: async (files, cb) => {
            const target = await cb.requestFileSelector(true, true);
            if (target === null) return;
            cb.invokeAsyncWork(async () => {
                try {
                    await copyOrMoveFilesNew(CopyOrMove.Move, files, target, cb.projects);
                    cb.requestReload();
                } catch (e) {
                    console.warn(e);
                }
            });
        },
        disabled: (files, cb) => {
            if (isAnyMockFile(files)) return true;
            else if (isAnyFixedFolder(files)) return true;
            else if (files.find(it => isPartOfSomePersonalFolder(it.path)) !== undefined) return true;
            else return !cb.permissions.requireForAll(files, AccessRight.WRITE);
        },
        icon: "move",
    },
    {
        text: "Extract archive",
        onClick: (files, cb) => cb.invokeAsyncWork(() =>
            extractArchive({files, client: Client, onFinished: () => cb.requestReload()})
        ),
        disabled: (files) => !files.every(it => isArchiveExtension(it.path)) || isAnyMockFile(files),
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
        disabled: (files, cb) => {
            if (!UF.isExtPreviewSupported(UF.extensionFromPath(files[0].path))) return true;
            else if (!cb.permissions.requireForAll(files, AccessRight.READ)) return true;
            else if (isAnyMockFile(files)) return true;
            else if (!UF.inRange({status: files[0].size ?? 0, min: 1, max: PREVIEW_MAX_SIZE})) return true;
            return false;
        },
        icon: "preview"
    },
    {
        text: "Properties",
        onClick: ([file], cb) => cb.history.push(fileInfoPage(file.path)),
        disabled: (files) => files.length !== 1 || isAnyMockFile(files),
        icon: "properties"
    },
    {
        text: "Move to Trash",
        onClick: (files, cb) => {
            moveToTrash({files, client: Client, setLoading: () => 42, callback: () => cb.requestReload(), projects: cb.projects});
        },
        disabled: (files, cb) => {
            if (!cb.permissions.requireForAll(files, AccessRight.WRITE)) return true;
            else if (isAnyFixedFolder(files)) return true;
            else if (isAnyMockFile(files)) return true;
            else return files.every(({path}) => isTrashFolder(path) || isTrashFolder(getParentPath(path)));
        },
        icon: "trash",
        color: "red"
    },
    {
        text: "Delete Files",
        onClick: (files, cb) => {
            const paths = files.map(f => f.path);
            const message = paths.length > 1 ? `Delete ${paths.length} files?` :
                `Delete file ${getFilenameFromPath(paths[0], cb.projects)}`;

            addStandardDialog({
                title: "Delete files",
                message,
                confirmText: "Delete files",
                onConfirm: () => {
                    cb.invokeAsyncWork(async () => {
                        const promises: Array<{status?: number; response?: string}> =
                            await Promise.all(paths.map(path => Client.delete("/files", {path})))
                                .then(it => it).catch(it => it);
                        const failures = promises.filter(it => it.status).length;
                        if (failures > 0) {
                            snackbarStore.addFailure(promises.filter(it => it.response).map(it => it).join(", "), false);
                        } else {
                            snackbarStore.addSuccess("Files deleted", false);
                        }
                    });
                }
            });
        },
        disabled: (files) => !files.every(f => isTrashFolder(getParentPath(f.path))) || isAnyMockFile(files),
        icon: "trash",
        color: "red"
    },
    {
        /* Rename project repo */
        text: "Rename",
        disabled: files => files.length !== 1,
        onClick: ([file], cb) => {
            if (isPersonalRootFolder(file.path)) {
                explainPersonalRepo();
            } else {
                cb.startRenaming(file);
            }
        },
        icon: "rename",
        repositoryMode: FileOperationRepositoryMode.REQUIRED
    },
    {
        /* Update repo permissions */
        text: "Permissions",
        disabled: files => files.length !== 1,
        onClick: ([file], cb) => {
            if (isPersonalRootFolder(file.path)) {
                explainPersonalRepo();
            } else {
            updatePermissionsPrompt(Client, file, cb.requestReload);
            }
        },
        icon: "properties",
        repositoryMode: FileOperationRepositoryMode.REQUIRED
    },
    {
        /* Delete repo permission */
        text: "Delete",
        onClick: ([file], cb) => {
            if (isPersonalRootFolder(file.path)) {
                explainPersonalRepo();
            } else {
                promptDeleteRepository(repositoryName(file.path), Client, cb.requestReload);
            }
        },
        disabled: files => files.length !== 1,
        icon: "trash",
        color: "red",
        repositoryMode: FileOperationRepositoryMode.REQUIRED
    }
];
