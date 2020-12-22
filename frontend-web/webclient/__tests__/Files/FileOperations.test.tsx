import {defaultFileOperations, FileOperationCallback} from "../../app/Files/FileOperations";
import {File} from "../../app/Files";
import {SensitivityLevelMap} from "../../app/DefaultObjects";
const UPLOAD_FILES = "Upload Files";
const NEW_FOLDER = "New Folder";
const EMPTY_TRASH = "Empty Trash";
const RENAME = "Rename";
const DOWNLOAD = "Download";
const SHARE = "Share";
const SENSITIVITY = "Sensitivity";
const COPY = "Copy";
const MOVE = "Move";
const EXTRACT_ARCHIVE = "Extract archive";
const VIEW_PARENT = "View Parent";
const PREVIEW = "Preview";
const PROPERTIES = "Properties";
const MOVE_TO_TRASH = "Move to Trash";
const DELETE_FILES = "Delete Files";
const RENAME_FOR_PROJECTS = "Rename";
const PERMISIONS = "Permissions";
const DELETE = "Delete";

const deleteOperation = defaultFileOperations.find(it => it.text === DELETE);
const moveToTrash = defaultFileOperations.find(it => it.text === MOVE_TO_TRASH);

const cb: FileOperationCallback = {
    permissions: {
        require: () => true,
        requireForAll: () => true
    },
    projectMembers: [],
    projects: [],
    invokeAsyncWork: () => undefined,
    requestFileSelector: () => new Promise<string>(resolve => resolve("foo")),
    requestFileUpload: () => undefined,
    requestFolderCreation: () => undefined,
    requestReload: () => undefined,
    createNewUpload: () => undefined,
    history: {} as any,
    startRenaming: () => undefined
};

const repoFiles: File[] = [
    {
        fileType: "DIRECTORY",
        path: "/projects/09d49df7-3d86-4f76-bd95-4839d7cbe4cf/Members' Files/user",
        modifiedAt: 1608213006704,
        ownerName: "user",
        size: 128,
        acl: [],
        sensitivityLevel: SensitivityLevelMap.PRIVATE,
        ownSensitivityLevel: null,
        permissionAlert: false
    },
    {
        fileType: "DIRECTORY",
        path: "/projects/09d49df7-3d86-4f76-bd95-4839d7cbe4cf/Members' Files",
        modifiedAt: 1607941638486,
        ownerName: "user",
        size: 96,
        acl: [],
        sensitivityLevel: SensitivityLevelMap.PRIVATE,
        ownSensitivityLevel: null,
        permissionAlert: false
    }
];

describe("Delete-operation", () => {
    if (!deleteOperation) throw Error("Delete operation is undefined");
    test("Is NOT disabled", () => {
        expect(deleteOperation.disabled([repoFiles[0]], cb)).toBeFalsy();
    });

    test("Is disabled because file is member's file", () => {
        expect(deleteOperation.disabled([repoFiles[1]], cb)).toBeTruthy();
    });

    test("Is disabled because files.length > 1", () => {
        expect(deleteOperation.disabled(repoFiles, cb)).toBeTruthy();
    });
});

const files: File[] = [{
    "fileType": "FILE",
    "path": "/home/user/astronaut.png",
    "modifiedAt": 1607942099598,
    "ownerName": "user",
    "size": 820,
    "acl": [],
    "sensitivityLevel": SensitivityLevelMap.PRIVATE,
    "ownSensitivityLevel": null,
    "permissionAlert": false
},
{
    "fileType": "DIRECTORY",
    "path": "/home/user/Jobs",
    "modifiedAt": 1607942116998,
    "ownerName": "user",
    "size": 96,
    "acl": [],
    "sensitivityLevel": SensitivityLevelMap.PRIVATE,
    "ownSensitivityLevel": null,
    "permissionAlert": false
},
{
    "fileType": "DIRECTORY",
    "path": "/home/user/Trash",
    "modifiedAt": 1607941637310,
    "ownerName": "user",
    "size": 64,
    "acl": [],
    "sensitivityLevel": SensitivityLevelMap.PRIVATE,
    "ownSensitivityLevel": null,
    "permissionAlert": false
}];

describe("MoveToTrash-operation", () => {
    const [file, directory, trashfolder] = files;

    if (!moveToTrash) throw Error("moveToTrash not defined");

     test("Is not disabled", () => {
        expect(moveToTrash.disabled([file], cb)).toBeFalsy();
    })

/*    test("Is disabled due to permissions TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    });

    test("Is disabled because file is fixed TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    });

    test("Is disabled because file is mock file TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    });

    test("Is disabled as file is owned by user in project TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    });

    test("Is NOT disabled as file is owned by user in project, but user is not part of project anymore TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    }); */

    test("Is disabled because file is trash folder", () => {
        expect(moveToTrash.disabled(files, cb)).toBeTruthy();
    });
/* 
    test("Is disabled because file is in trash folder TODO", () => {
        expect(moveToTrash.disabled).toBeTruthy();
    });

    test("Is disabled because file is in folder in trash folder TODO", () => {
        expect(moveToTrash.disabled).toBeFalsy();
    }); */
});

// if (!cb.permissions.requireForAll(files, AccessRight.WRITE)) return true;
// else if (isAnyFixedFolder(files)) return true;
// else if (isAnyMockFile(files)) return true;
// else if (
//     files.find(it => isAUserPersonalFolder(it.path) ||
//         /* if user is still part of project, files cannot be deleted - UNLESS it is current user's files  */
//         (cb.projectMembers.includes(pathComponents(it.path)[3]) && pathComponents(it.path)[3] !== Client.username)
//     )
// ) return true;
// else return files.every(({path}) => isTrashFolder(path) || isTrashFolder(getParentPath(path)));