import * as React from "react";
import * as swal from "sweetalert2";
import { RightsMap, RightsNameMap, SensitivityLevelMap } from "./DefaultObjects";
import { File, Path, Acl } from "./types/types";
import Cloud from "../authentication/lib";

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
            return a.path.name.localeCompare(b.path.name) * order;
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
    let file = files.find((file: File) => file.path.path === path);
    file.favorited = !file.favorited;
    if (file.favorited) {
        cloud.post(`/files/favorite?path=${file.path.path}`);
    } else {
        cloud.delete(`/files/favorite?path=${file.path.path}`);
    }
    return files;
};

export const getOwnerFromAcls = (acls: Acl[], cloud: Cloud) => {
    const userName: string = cloud.username;
    const result: Acl = acls.find((acl: Acl) => acl.entity.displayName === userName);
    if (!result) {
        return "None"
    }
    return result.right;
};

export const updateSharingOfFile = (filePath: Path, user: string, currentRights: string, cloud: Cloud, callback: Function) => {
    swal({
        title: "Please specify access level",
        text: `The file ${filePath.name} is to be shared with ${user}.`,
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
            onFile: filePath.path,
            rights: type.value,
            type: "grant",
        };
        cloud.put("/acl", body).then(() => {
            swal("Success!", `The file has been shared with ${user}`, "success").then(() => callback ? callback() : null);
        });
    });
};

export const shareFile = (filePath: Path, cloud: Cloud, callback: Function) => {
    swal({
        title: "Share file",
        text: `Enter a username to share ${filePath.name} with.`,
        input: "text",
        confirmButtonText: "Next",
        showCancelButton: true,
        showCloseButton: true,
        inputValidator: (value: string) => {
            return !value && 'Please enter a username'
        }
    }).then((input: any) => {
        if (input.dismiss) {
            return;
        }
        swal({
            title: "Please specify access level",
            text: `The file ${filePath.name} is to be shared with ${input.value}.`,
            input: "select",
            showCancelButton: true,
            showCloseButton: true,
            inputOptions: {
                "READ": "Read Access",
                "READ_WRITE": "Read/Write Access",
                //"OWN": "Own the file"
            },
        }).then((type: any) => {
            if (type.dismiss) {
                return;
            }
            const body = {
                entity: input.value,
                onFile: filePath.path,
                rights: type.value,
                type: "grant",
            };
            cloud.put("/acl", body).then((response: any) => {
                swal("Success!", `The file has been shared with ${input.value}`, "success").then(() => callback ? callback() : null);
            });
        });
    }
    );
}

export const revokeSharing = (filePath: Path, person: string, rightsLevel: string, cloud: Cloud) =>
    swal({
        title: "Revoke access",
        text: `Revoke ${rightsLevel} access for ${person}`,
    }).then((input: any) => {
        if (input.dismiss) {
            return;
        }
        const body = {
            onFile: filePath,
            entity: person,
            type: "revoke",
        };

        return cloud.delete("/acl", body);//.then(response => {

        //});
    });

export const createFolder = (currentPath: string) => {
    swal({
        title: "Create folder",
        text: `The folder will be created in:\n${currentPath}`,
        confirmButtonText: "Create folder",
        input: "text",
        showCancelButton: true,
        showCloseButton: true,
    }).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            swal(`Not yet implemented: ${result.value}`);
        }
    })
}

export const renameFile = (filePath: Path) =>
    swal({
        title: "Rename file",
        text: `The file ${filePath.name} will be renamed`,
        confirmButtonText: "Rename",
        input: "text",
        showCancelButton: true,
        showCloseButton: true,
    }).then((result: any) => {
        if (result.dismiss) {
            return;
        }
    });

export const showFileDeletionPrompt = (filePath: Path) =>
    swal({
        title: "Delete file",
        text: `Delete file ${filePath.name}`,
        confirmButtonText: "Delete file",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    }).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            // DELETE FILE
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
        return lastSorting.asc ? "ion-chevron-down" : "ion-chevron-up";
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


export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();
