import React from "react";
import swal from "sweetalert2";
import {RightsMap, RightsNameMap, SensitivityLevelMap, AnalysesStatusMap} from "./DefaultObjects"

export const NotificationIcon = (props) => {
    if (props.type === "Complete") {
        return (<div className="initial32 bg-green-500">✓</div>)
    } else if (props.type === "In Progress") {
        return (<div className="initial32 bg-blue-500">...</div>)
    } else if (props.type === "Pending") {
        return (<div className="initial32 bg-blue-500"/>)
    } else if (props.type === "Failed") {
        return (<div className="initial32 bg-red-500">&times;</div>)
    } else {
        return (<div>Unknown type</div>)
    }
};

export const toLowerCaseAndCapitalize = (str) => !str ? "" : str.charAt(0).toUpperCase() + str.toLowerCase().slice(1);

export const WebSocketSupport = () =>
    !("WebSocket" in window) ?
        (<h3>
            <small>WebSockets are not supported in this browser. Notifications won't be updated automatically.
            </small>
        </h3>) : null;

export const sortFilesByFavorite = (files, asc) => {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.favorited - b.favorited) * order
    });
    return files;
};

export const sortFilesByModified = (files, asc) => {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.modifiedAt - b.modifiedAt) * order;
    });
    return files;
};

export const sortFilesByTypeAndName = (files, asc) => {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
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

export function sortFilesByOwner(files, asc) { // FIXME Should sort based on the value inside the acl (OWN, READ, READ/WRITE)
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.acl.length - b.acl.length) * order;
    });
    return files;
}

export function sortByStatus(analyses, asc) {
    let order = asc ? 1 : -1;
    analyses.sort((a, b) => {
        return (AnalysesStatusMap[a.status] - AnalysesStatusMap[b.status]) * order;
    });
    return analyses;
}

export function sortFilesBySensitivity(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return SensitivityLevelMap[a.sensitivityLevel] - SensitivityLevelMap[b.sensitivityLevel] * order;
    });
    return files;
}

export function favorite(files, path, cloud) {
    let file = files.find(file => file.path.path === path);
    file.favorited = !file.favorited;
    if (file.favorited) {
        cloud.post(`/files/favorite?path=${file.path.path}`);
    } else {
        cloud.delete(`/files/favorite?path=${file.path.path}`);
    }
    return files;
}

export function getOwnerFromAcls(acls, cloud) {
    let userName = cloud.username;
    let result = acls.find(acl => acl.entity.displayName === userName);
    if (!result) {
        return "None"
    }
    return result.right;
}

export function updateSharingOfFile(filePath, user, currentRights, cloud) {
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
        inputValidator: value => {
            return currentRights === value && `${user} already has ${RightsNameMap[value]} access.`
        }
    }).then(type => {
        if (type.dismiss) {
            return;
        }
        const body = {
            entity: user,
            onFile: filePath.path,
            rights: type.value,
            type: "grant",
        };
        cloud.put("/acl", body).then(response => {
            swal("Success!", `The file has been shared with ${user}`, "success");
        });
    });
}

export function shareFile(filePath, cloud) {
    swal({
        title: "Share file",
        text: `Enter a username to share ${filePath.name} with.`,
        input: "text",
        confirmButtonText: "Next",
        showCancelButton: true,
        showCloseButton: true,
        inputValidator: value => {
            return !value && 'Please enter a username'
        }
    }).then(input => {
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
            }).then(type => {
                if (type.dismiss) {
                    return;
                }
                const body = {
                    entity: input.value,
                    onFile: filePath.path,
                    rights: type.value,
                    type: "grant",
                };
                cloud.put("/acl", body).then(response => {
                    swal("Success!", `The file has been shared with ${input.value}`, "success");
                });
            });
        }
    );
}

export function revokeSharing(filePath, person, rightsLevel, cloud) {
    swal({
        title: "Revoke access",
        text: `Revoke ${rightsLevel} access for ${person}`,
    }).then(input => {
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
}

export function createFolder(currentPath) {
    swal({
        title: "Create folder",
        text: `The folder will be created in:\n${currentPath}`,
        confirmButtonText: "Create folder",
        input: "text",
        showCancelButton: true,
        showCloseButton: true,
    }).then(result => {
        if (result.dismiss) {
            return;
        } else {
            swal(`Not yet implemented: ${result.value}`);
        }
    })
}

export function renameFile(filePath) {
    swal({
        title: "Rename file",
        text: `The file ${filePath.name} will be renamed`,
        confirmButtonText: "Rename",
        input: "text",
        showCancelButton: true,
        showCloseButton: true,
    }).then(result => {
        if (result.dismiss) {
            return;
        }
    })
}

export function showFileDeletionPrompt(filePath) {
    swal({
        title: "Delete file",
        text: `Delete file ${filePath.name}`,
        confirmButtonText: "Delete file",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    }).then(result => {
        if (result.dismiss) {
            return;
        } else {
            // DELETE FILE
        }
    });
}

export function getParentPath(path) {
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
}

export function downloadFile(path, cloud) {
    cloud.createOneTimeTokenWithPermission("downloadFile,irods").then(token => {
        let link = document.createElement("a");
        window.location.href = "/api/files/download?path=" + encodeURI(path) + "&token=" + encodeURI(token);
        link.setAttribute("download", "");
        link.click();
    });
}

export const fileSizeToString = (bytes) => {
    if (!bytes) {
        return "";
    }
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

export const castValueTo = (parameterType = null, value = null) => {
    if (!parameterType) {
        return value;
    }
    switch (parameterType) {
        case "integer": {
            return parseInt(value);
        }
        case "floating_point": {
            return parseFloat(value)
        }
        default: {
            return value;
        }
    }
};

export const getCurrentRights = (files, cloud) => {
    let lowestPrivilegeOptions = RightsMap["OWN"];
    files.forEach((it) => {
        it.acl.filter(acl => acl.entity.displayName === cloud.username).forEach((acl) => {
            lowestPrivilegeOptions = Math.min(RightsMap[acl.right], lowestPrivilegeOptions);
        });
    });
    return {
        rightsName: Object.keys(RightsMap)[lowestPrivilegeOptions],
        rightsLevel: lowestPrivilegeOptions
    }
};