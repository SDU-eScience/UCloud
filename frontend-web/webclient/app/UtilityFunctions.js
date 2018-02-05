import React from "react";
import swal from "sweetalert2";
import {RightsMap, RightsNameMap, SensitivityLevelMap, AnalysesStatusMap} from "./DefaultObjects"
import {Cloud} from "../authentication/SDUCloudObject";

function NotificationIcon(props) {
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
}

function toLowerCaseAndCapitalize(str) {
    return str.charAt(0).toUpperCase() + str.toLowerCase().slice(1);
}

function WebSocketSupport() {
    let hasWebSocketSupport = "WebSocket" in window;
    if (!hasWebSocketSupport) {
        return (
            <h3>
                <small>WebSockets are not supported in this browser. Notifications won't be updated automatically.
                </small>
            </h3>);
    }
    return (null);
}

function buildBreadCrumbs(path) {
    let paths = path.split("/");
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({actualPath: actualPath, local: paths[i],})
    }
    return pathsMapping;
}

function sortFilesByFavorite(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.favorited - b.favorited) * order
    });
    return files;
}

function sortFilesByModified(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.modifiedAt - b.modifiedAt) * order;
    });
    return files;
}

function sortFilesByTypeAndName(files, asc) {
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
}

function sortFilesByOwner(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return (a.acl.length - b.acl.length) * order;
    });
    return files;
}

function sortByStatus(analyses, asc) {
    let order = asc ? 1 : -1;
    analyses.sort((a, b) => {
        return (AnalysesStatusMap[a.status] - AnalysesStatusMap[b.status]) * order;
    });
    return analyses;
}

function sortFilesBySensitivity(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return SensitivityLevelMap[a.sensitivityLevel] - SensitivityLevelMap[b.sensitivityLevel] * order;
    });
    return files;
}

function favorite(files, uri) {
    let file = files.find(file => file.path.uri === uri);
    file.favorited = !file.favorited;
    if (file.favorited) {
        Cloud.post(`/files/favorite?path=${file.path.path}`);
    } else {
        Cloud.delete(`/files/favorite?path=${file.path.path}`);
    }
    return files;
}

function getOwnerFromAcls(acls) {
    let userName = Cloud.username;
    let result = acls.find(acl => acl.entity.displayName === userName);
    if (!result) {
        return "None"
    }
    return result.right;
}

function updateSharingOfFile(filePath, user, currentRights) {
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
        Cloud.put("/acl", body).then(response => {
            swal("Success!", `The file has been shared with ${user}`, "success");
        });
    });
}

function shareFile(filePath) {
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
                Cloud.put("/acl", body).then(response => {
                    swal("Success!", `The file has been shared with ${input.value}`, "success");
                });
            });
        }
    );
}

function revokeSharing(filePath, person, rightsLevel) {
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

        return Cloud.delete("/acl", body);//.then(response => {

        //});
    });
}

function createFolder(currentPath) {
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

function renameFile(filePath) {
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

function sendToAbacus(filePath) {

}

function showFileDeletionPrompt(filePath) {
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

function getParentPath(path) {
    let splitPath = path.split("/");
    let parentPath = "";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
}

const makeCancelable = (promise) => {
    let hasCanceled_ = false;

    const wrappedPromise = new Promise((resolve, reject) => {
        promise.then(
            val => hasCanceled_ ? reject({isCanceled: true}) : resolve(val),
            error => hasCanceled_ ? reject({isCanceled: true}) : reject(error)
        );
    });

    return {
        promise: wrappedPromise,
        cancel() {
            hasCanceled_ = true;
        },
    };
};

function downloadFile(path) {
    console.log("Doing something");
    Cloud.createOneTimeTokenWithPermission("downloadFile,irods").then(token => {
        let link = document.createElement("a");
        window.location.href = "/api/files/download?path=" + encodeURI(path) + "&token=" + encodeURI(token);
        link.setAttribute("download", "");
        link.click();
    });
}

function getCurrentRights(files) {
    let lowestPrivilegeOptions = RightsMap["OWN"];
    files.forEach((it) => {
        it.acl.filter(acl => acl.entity.displayName === Cloud.username).forEach((acl) => {
            lowestPrivilegeOptions = Math.min(RightsMap[acl.right], lowestPrivilegeOptions);
        });
    });
    return {
        rightsName: Object.keys(RightsMap)[lowestPrivilegeOptions],
        rightsLevel: lowestPrivilegeOptions
    }
}

export {
    NotificationIcon,
    WebSocketSupport,
    buildBreadCrumbs,
    createFolder,
    favorite,
    sortFilesByTypeAndName,
    sortFilesByModified,
    sortFilesByFavorite,
    sortFilesByOwner,
    sortFilesBySensitivity,
    shareFile,
    getOwnerFromAcls,
    showFileDeletionPrompt,
    sendToAbacus,
    renameFile,
    getParentPath,
    updateSharingOfFile,
    revokeSharing,
    makeCancelable,
    downloadFile,
    getCurrentRights,
    toLowerCaseAndCapitalize,
}
