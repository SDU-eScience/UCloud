import * as React from "react";
import swal from "sweetalert2";
import { RightsMap, RightsNameMap, SensitivityLevelMap } from "./DefaultObjects";
import { File, Acl } from "./types/types";
import Cloud from "../authentication/lib";
import { AccessRight } from "./types/types";

interface Type { type: string }
export const NotificationIcon = ({ type }: Type) => {
    if (type === "Complete") {
        return (<div className="initial32 bg-green-500">✓</div>)
    } else if (type === "In Progress") {
        return (<div className="initial32 bg-blue-500">...</div>)
    } else if (type === "Pending") {
        return (<div className="initial32 bg-blue-500" />)
    } else if (type === "Failed") {
        return (<div className="initial32 bg-red-500">&times;</div>)
    } else {
        return (<div>Unknown type</div>)
    }
};

export const toLowerCaseAndCapitalize = (str: string): string => !str ? "" : str.charAt(0).toUpperCase() + str.toLowerCase().slice(1);

export const WebSocketSupport = () =>
    !("WebSocket" in window) ?
        (<h3>
            <small>WebSockets are not supported in this browser. Notifications won't be updated automatically.
            </small>
        </h3>) : null;

export const isInvalidPathName = (path: string, filePaths: string[]): string => {
    const disallowedName = ["..", ".", "/"].some((it) => it === path);
    if (disallowedName) return "Folder name cannot be '.', '..' or '/'";
    const existingName = filePaths.some((it) => it === path);
    if (existingName) return "File with that name already exists";
    return "";
}

export const isFixedFolder = (filePath, homeFolder) => {
    return [
        `${homeFolder}/Favorites`,
        `${homeFolder}/Uploads`,
        `${homeFolder}/Trash bin`
    ].some((it) => it === filePath)
};

export function sortByNumber<T>(list: T[], name: string, asc: boolean): T[] {
    list.sort((a: any, b: any) => (Number(a[name]) - (Number(b[name]))) * (asc ? -1 : 1));
    return list;
}

export function sortByString<T>(list: T[], name: string, asc: boolean): T[] {
    list.sort((a: any, b: any) => ((a[name] as string).localeCompare(b[name] as string)) * (asc ? 1 : -1));
    return list;
}

export const sortFilesByTypeAndName = (files: File[], asc: boolean) => {
    const order = asc ? 1 : -1;
    files.sort((a: File, b: File) => {
        if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
            return -1 * order;
        else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
            return 1 * order;
        else {
            return getFilenameFromPath(a.path).localeCompare(getFilenameFromPath(b.path)) * order;
        }
    });
    return files;
};

export const sortFilesBySensitivity = (files: File[], asc: boolean) => {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return SensitivityLevelMap[a.sensitivityLevel] - SensitivityLevelMap[b.sensitivityLevel] * order;
    });
    return files;
};

export const favorite = (files: File[], path: string, cloud: Cloud) => {
    let file = files.find((file: File) => file.path === path);
    file.favorited = !file.favorited;
    if (file.favorited) {
        cloud.post(`/files/favorite?path=${file.path}`);
    } else {
        cloud.delete(`/files/favorite?path=${file.path}`);
    }
    return files;
};

export const getOwnerFromAcls = (acls: Acl[], cloud: Cloud) => {
    const userName: string = cloud.username;
    const result = acls.find((acl: Acl) => acl.entity.displayName === userName);
    if (!result) {
        return "None"
    } else if (acls.length > 1) {
        return `${acls.length} members`;
    } else {
        return "You";
    }
};

export const failureNotification = (title: string) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 3000,
    type: "error",
    title
});

export const genericFailureNotification = (title: string) =>
    failureNotification("An error occurred, please try again later.")

export const successNotification = (title: string) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 3000,
    type: "success",
    title
});

export const shareFile = (path: string, cloud: Cloud, callback: Function) => swal({
    title: "Share",
    input: "text",
    html: `<form class="ui form">
            <div class="three fields">
                <div class="field"><div class="ui checkbox">
                    <input id="read-swal" type="checkbox" /><label>Read</label>
                </div></div>
                <div class="field"><div class="ui checkbox">
                    <input id="write-swal" type="checkbox" /><label>Write</label>
                </div></div>
                <div class="field"><div class="ui checkbox">
                    <input id="execute-swal" type="checkbox" /><label>Execute</label>
                </div></div>
            </div>
          </form>`,
    showCloseButton: true,
    showCancelButton: true,
    inputPlaceholder: "Enter username...",
    focusConfirm: false,
    inputValidator: (value) =>
        (!value && "Username missing") ||
        !((document.getElementById("read-swal") as HTMLInputElement).checked ||
        (document.getElementById("write-swal") as HTMLInputElement).checked ||
        (document.getElementById("execute-swal") as HTMLInputElement).checked) && "Select at least one access right",
}).then((i) => {
    if (i.dismiss) return;
    const rights = [];
    (document.getElementById("read-swal") as HTMLInputElement).checked ? rights.push(AccessRight.READ) : null;
    (document.getElementById("write-swal") as HTMLInputElement).checked ? rights.push(AccessRight.WRITE) : null;
    (document.getElementById("execute-swal") as HTMLInputElement).checked ? rights.push(AccessRight.EXECUTE) : null;
    const body = {
        sharedWith: i.value,
        path,
        rights
    };
    cloud.put(`/shares/`, body).then(() => successNotification(`${getFilenameFromPath(path)} shared with ${i.value}`))
        .catch(() => failureNotification(`The file could not be shared at this time. Please try again later.`));
});


export const updateSharingOfFile = (filePath: string, user: string, currentRights: string, cloud: Cloud, callback: () => any) => {
    swal({
        title: "Please specify access level",
        text: `The file ${getFilenameFromPath(filePath)} is to be shared with ${user}.`,
        input: "select",
        showCancelButton: true,
        showCloseButton: true,
        inputOptions: {
            "READ": "Read Access",
            "READ_WRITE": "Read/Write Access",
            //"OWN": "Own the file"
        },
        inputValidator: (value: string) => {
            return currentRights === value && `${user} already has ${RightsNameMap[value]} access.`
        }
    }).then((type: any) => {
        if (type.dismiss) {
            return;
        }
        const body = {
            entity: user,
            onFile: filePath,
            rights: type.value,
            type: "grant",
        };
        cloud.put("/acl", body).then(() => {
            swal("Success!", `The file has been shared with ${user}`, "success").then(() => callback ? callback() : null);
        });
    });
};


export const renameFile = (filePath: string) =>
    swal({
        title: "Rename file",
        text: `The file ${getFilenameFromPath(filePath)} will be renamed`,
        confirmButtonText: "Rename",
        input: "text",
        showCancelButton: true,
        showCloseButton: true,
    }).then((result: any) => {
        if (result.dismiss) {
            return;
        }
    });


const deletionSwal = (filePaths: string[]) =>
    filePaths.length > 1 ?
        swal({
            title: "Delete files",
            text: `Delete ${filePaths.length} files?`,
            confirmButtonText: "Delete files",
            type: "warning",
            showCancelButton: true,
            showCloseButton: true,
        })
        : swal({
            title: "Delete file",
            text: `Delete file ${getFilenameFromPath(filePaths[0])}`,
            confirmButtonText: "Delete file",
            type: "warning",
            showCancelButton: true,
            showCloseButton: true,
        });

export const batchDeleteFiles = (filePaths: string[], cloud: Cloud, callback: () => void) => {
    deletionSwal(filePaths).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            let i = 0;
            filePaths.forEach((it) => {
                cloud.delete("/files", { path: it }).then(() => { ++i === filePaths.length ? callback() : null })
                    .catch(() => i++);
            });
        }
    })
};

export const showFileDeletionPrompt = (filePath: string, cloud: Cloud, callback: () => void) =>
    deletionSwal([filePath]).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.delete("/files", { path: filePath }).then(() => callback ? callback() : null);
        }
    });

export const getParentPath = (path: string): string => {
    if (!path) {
        return "";
    }
    let splitPath = path.split("/");
    splitPath = splitPath.filter(path => path);
    let parentPath = "/";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
};

export const getFilenameFromPath = (path: string): string =>
    !path ? "" : path.split("/").pop();


export const downloadFile = (path: string, cloud: Cloud) =>
    cloud.createOneTimeTokenWithPermission("downloadFile,irods").then((token: string) => {
        let link = document.createElement("a");
        window.location.href = "/api/files/download?path=" + encodeURI(path) + "&token=" + encodeURI(token);
        link.setAttribute("download", "");
        link.click();
    });

export const fileSizeToString = (bytes: number): string => {
    if (!bytes) { return ""; }
    if (bytes < 1000) {
        return `${bytes} B`;
    } else if (bytes < 1000 ** 2) {
        return `${bytes / 1000} KB`;
    } else if (bytes < 1000 ** 3) {
        return `${bytes / 1000 ** 2} MB`;
    } else if (bytes < 1000 ** 4) {
        return `${bytes / 1000 ** 3} GB`;
    } else if (bytes < 1000 ** 5) {
        return `${bytes / 1000 ** 4} TB`;
    } else if (bytes < 1000 ** 6) {
        return `${bytes / 1000 ** 5} PB`;
    } else if (bytes < 1000 ** 7) {
        return `${bytes / 1000 ** 6} EB`;
    } else {
        return `${bytes} B`;
    }
};

export const getCurrentRights = (files: File[], cloud: Cloud) => {
    let lowestPrivilegeOptions = RightsMap["OWN"];
    files.forEach((it) => {
        it.acl.filter((acl: Acl) => acl.entity.displayName === cloud.username).forEach((acl: Acl) => {
            lowestPrivilegeOptions = Math.min(RightsMap[acl.right], lowestPrivilegeOptions);
        });
    });
    return {
        rightsName: Object.keys(RightsMap)[lowestPrivilegeOptions],
        rightsLevel: lowestPrivilegeOptions
    }
};

interface LastSorting { name: string, asc: boolean }
export const getSortingIcon = (lastSorting: LastSorting, name: string): string => {
    if (lastSorting.name === name) {
        return lastSorting.asc ? "chevron down" : "chevron up";
    }
    return "";
};

export const createRange = (count: number): number[] => {
    let range = [];
    for (let i = 0; i < count; i++) {
        range.push(i);
    }
    return range;
};

export const createRangeInclusive = (count: number): number[] => {
    let range = [];
    for (let i = 0; i <= count; i++) {
        range.push(i);
    }
    return range;
};

export const getTypeFromFile = (filePath: string): string => {
    const filename = getFilenameFromPath(filePath);
    if (!filename.includes(".")) {
        return "file text outline";
    }
    const extension = filename.split(".").pop();
    switch (extension) {
        case "kt":
        case "js":
        case "jsx":
        case "ts":
        case "tsx":
        case "java":
        case "py":
        case "tex":
        case "r":
        case "c":
        case "cc":
        case "c++":
        case "h++":
        case "cpp":
        case "h":
        case "hh":
        case "hpp":
        case "html":
        case "sql":
        case "sh":
        case "iol":
        case "ol":
        case "col":
        case "bib":
        case "toc":
        case "jar":
            return "file code outline";
        case "png":
        case "gif":
        case "tiff":
        case "eps":
        case "ppm":
            return "image";
        case "txt":
        case "pdf":
        case "xml":
        case "json":
        case "csv":
        case "yml":
            return "file text outline";
        case "wav":
        case "mp3":
            return "ion-android-volume-up";
        default:
            if (getFilenameFromPath(filePath).split(".").length > 1)
                console.warn(`Unhandled extension "${filePath}" for file ${filePath}`)
            return "file text outline";
    }
}

export const toFileText = (selectedFiles: File[]): string => {
    if (selectedFiles.length > 1) {
        return `${selectedFiles.length} files selected.`;
    } else {
        const filename = getFilenameFromPath(selectedFiles[0].path);
        filename.length > 10 ? filename.slice(0, 17) + "..." : filename;
    }
}

export const inRange = (status: number, min: number, max: number): boolean => status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange(status, 200, 299);
export const removeTrailingSlash = (path) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();
