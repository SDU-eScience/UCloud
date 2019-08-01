import {APICallParameters} from "Authentication/DataHook";
import {File} from "Files/index";
import * as UF from "UtilityFunctions";
import {
    allFilesHasAccessRight,
    batchDeleteFiles,
    CopyOrMove,
    copyOrMoveFiles,
    downloadFiles,
    extractArchive,
    fileInfoPage,
    getParentPath,
    inTrashDir,
    isArchiveExtension,
    isFixedFolder,
    moveToTrash, newMockFolder,
    replaceHomeFolder,
    shareFiles, startRenamingFiles,
    updateSensitivity
} from "Utilities/FileUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import SDUCloud from "Authentication/lib";
import {AccessRight} from "Types";

export interface FileOperationCallback {
    invokeCommand: (params: APICallParameters) => void
    requestFolderCreation: () => void
    requestReload: () => void
    startRenaming: (file: File) => void
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

// TODO We do not currently show loading status for any of these!

export const defaultFileOperations: FileOperation[] = [
    {
        text: "Upload Files",
        onClick: () => 42,
        disabled: () => false,
        color: "blue",
        currentDirectoryMode: true
    },
    {
        text: "New Folder",
        onClick: (_, cb) => cb.requestFolderCreation(),
        disabled: () => false,
        color: "blue",
        outline: true,
        currentDirectoryMode: true
    },
    {
        text: "Share",
        onClick: (files) => shareFiles({files, cloud: Cloud}),
        disabled: (files) => !allFilesHasAccessRight("WRITE", files) || !allFilesHasAccessRight("READ", files),
        icon: "share",
        color: undefined
    },
    {
        text: "Download",
        onClick: files => downloadFiles(files, () => 42, Cloud),
        disabled: files => !UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files),
        icon: "download",
        color: undefined
    },
    {
        text: "Sensitivity",
        onClick: (files, cb) => {
            updateSensitivity({files, cloud: Cloud, onSensitivityChange: () => cb.requestReload()})
        },
        disabled: files => false,
        icon: "verified",
        color: undefined
    },
    {
        text: "Copy Path",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Cloud.homeFolder)} copied to clipboard`
        }),
        disabled: files => !UF.inDevEnvironment() || files.length !== 1,
        icon: "chat",
        color: undefined
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
        onClick: (files, cb) => batchDeleteFiles({
            files,
            cloud: Cloud,
            setLoading: () => 42,
            callback: () => cb.requestReload()
        }),
        disabled: (files) => !files.every(f => getParentPath(f.path) === Cloud.trashFolder),
        icon: "trash",
        color: "red"
    },
    {
        text: "Properties",
        onClick: (files) => 42, //history.push(fileInfoPage(files[0].path)), // TODO!!!
        disabled: (files) => files.length !== 1,
        icon: "properties", color: "blue"
    },
    {
        text: "Extract archive",
        onClick: (files, cb) => extractArchive({files, cloud: Cloud, onFinished: () => cb.requestReload()}),
        disabled: (files) => !files.every(it => isArchiveExtension(it.path)),
        icon: "open",
        color: undefined
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files: File[]) => files.length === 1 && !allFilesHasAccessRight(AccessRight.WRITE, files),
        icon: "rename",
        color: undefined
    }
    /*
    {
        text: "Copy",
        onClick: (files) => copyOrMoveFiles({
            operation: CopyOrMove.Copy,
            files,
            copyMoveOps: fileSelectorOperations, // This requirement wil disappear later
            cloud: Cloud,
            setLoading: () => 42
        }),
        disabled: (files) => !allFilesHasAccessRight("WRITE", files),
        icon: "copy",
        color: undefined
    },
    {
        text: "Move",
        onClick: (files) => copyOrMoveFiles({
            operation: CopyOrMove.Move,
            files,
            copyMoveOps: fileSelectorOperations, // This requirement will disappear later
            cloud: Cloud,
            setLoading: () => 42
        }),

        disabled: (files) =>
            !allFilesHasAccessRight("WRITE", files) ||
            files.some(f => isFixedFolder(f.path, Cloud.homeFolder)),

        icon: "move",
        color: undefined
    }
     */
];
