import * as React from "react";
import swal from "sweetalert2";
import { RightsMap, RightsNameMap, SensitivityLevelMap } from "./DefaultObjects";
import { File, Acl } from "./types/types";
import Cloud from "../authentication/lib";
import { AccessRight } from "./types/types";
import { SemanticICONS } from "semantic-ui-react";

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

export const isInvalidPathName = (path: string, filePaths: string[]): boolean => {
    const disallowedName = ["..", ".", "/"].some((it) => it === path);
    if (disallowedName) { failureNotification("Folder name cannot be '.', '..' or '/'"); return true; }
    const existingName = filePaths.some((it) => it === path);
    if (existingName) { failureNotification("File with that name already exists"); return true; }
    return false;
};

export const isFixedFolder = (filePath: string, homeFolder: string) => {
    return [
        `${homeFolder}/Favorites`,
        `${homeFolder}/Uploads`,
        `${homeFolder}/Jobs`,
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
            return order;
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

export const getOwnerFromAcls = (acls: Acl[]) => {
    if (acls.length > 0) {
        return `${acls.length + 1} members`;
    } else {
        return "Only You";
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

export const genericFailureNotification = () => failureNotification("An error occurred, please try again later.");

export const successNotification = (title: string) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 3000,
    type: "success",
    title
});

export const shareSwal = () => swal({
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
        !(isElementChecked("read-swal") ||
            isElementChecked("write-swal") ||
            isElementChecked("execute-swal")) && "Select at least one access right",
});

function isElementChecked(id: string): boolean {
    return (document.getElementById(id) as HTMLInputElement).checked;
}

export const shareFiles = (paths: string[], cloud: Cloud) =>
    shareSwal().then((input) => {
        if (input.dismiss) return;
        const rights = [] as string[];
        if (isElementChecked("read-swal")) rights.push(AccessRight.READ);
        if (isElementChecked("write-swal")) rights.push(AccessRight.WRITE);
        if (isElementChecked("execute-swal")) rights.push(AccessRight.EXECUTE);
        let i = 0;
        paths.forEach(path => {
            const body = {
                sharedWith: input.value,
                path,
                rights
            };
            cloud.put(`/shares/`, body).then(() => ++i === paths.length ? successNotification("Files shared successfully") : null)
                .catch(() => failureNotification(`${getFilenameFromPath(path)} could not be shared at this time. Please try again later.`));
        });
    });

export const inputSwal = (inputName: string) => ({
    title: "Share",
    input: "text",
    showCloseButton: true,
    showCancelButton: true,
    inputPlaceholder: `Enter ${inputName}...`,
    focusConfirm: false,
    inputValidator: (value) =>
        (!value && `${toLowerCaseAndCapitalize(inputName)} missing`)
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


export const downloadFiles = (paths: string[], cloud: Cloud) => {
    paths.forEach(p =>
        cloud.createOneTimeTokenWithPermission("downloadFile,irods").then((token: string) => {
            let link = document.createElement("a");
            window.location.href = "/api/files/download?path=" + encodeURI(p) + "&token=" + encodeURI(token);
            link.download = "";
            link.click();
        }));
}

export const fileSizeToString = (bytes: number): string => {
    if (bytes === 0) return "0 B";
    if (!bytes) { return ""; }
    if (bytes < 1000) {
        return `${bytes} B`;
    } else if (bytes < 1000 ** 2) {
        return `${(bytes / 1000).toFixed(2)} KB`;
    } else if (bytes < 1000 ** 3) {
        return `${(bytes / 1000 ** 2).toFixed(2)} MB`;
    } else if (bytes < 1000 ** 4) {
        return `${(bytes / 1000 ** 3).toFixed(2)} GB`;
    } else if (bytes < 1000 ** 5) {
        return `${(bytes / 1000 ** 4).toFixed(2)} TB`;
    } else if (bytes < 1000 ** 6) {
        return `${(bytes / 1000 ** 5).toFixed(2)} PB`;
    } else if (bytes < 1000 ** 7) {
        return `${(bytes / 1000 ** 6).toFixed(2)} EB`;
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

export const iconFromFilePath = (filePath: string): SemanticICONS => {
    const filename = getFilenameFromPath(filePath);
    if (!filename.includes(".")) {
        return "file outline";
    }
    const extension = filename.split(".").pop();
    switch (extension) {
        case "md":
        case "swift":
        case "kt":
        case "js":
        case "jsx":
        case "ts":
        case "tsx":
        case "java":
        case "py":
        case "python":
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
        case "exe":
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
        case "plist":
            return "file outline";
        case "wav":
        case "mp3":
            return "volume up";
        case "gz":
        case "zip":
            return "file archive outline";
        default:
            if (getFilenameFromPath(filePath).split(".").length > 1)
                console.warn(`Unhandled extension "${filePath}" for file ${filePath}`);
            return "file outline";
    }
};

export const createProject = (filePath: string, cloud: Cloud) =>
    cloud.put("/projects", { fsRoot: filePath }).then(() =>
        successNotification(`${filePath} project creation started.`)
    ).catch(() => genericFailureNotification());

export const isProject = (file: File) => file.type === "DIRECTORY" && file.annotations.some(it => it === "P");

export const toFileText = (selectedFiles: File[]): string =>
    selectedFiles.length > 1 ? `${selectedFiles.length} files selected.` : getFilenameFromPath(selectedFiles[0].path);

export const isLink = (file: File) => file.link;
export const isDirectory = (file: File) => file.type === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string) => path.replace(homeFolder, "Home");
export const inRange = (status: number, min: number, max: number): boolean => status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange(status, 200, 299);
export const removeTrailingSlash = (path: string) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const addTrailingSlash = (path: string) => path.endsWith("/") ? path : `${path}/`;
export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();

export const blankOrNull = (value: string): boolean => {
    return value == null || value.length == 0 || /^\s*$/.test(value);
};

export const ifPresent = (f, handler: (f: any) => void) => {
    if (f) handler(f);
};
