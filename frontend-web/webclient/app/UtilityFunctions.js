import React from "react";
import swal from "sweetalert2";
import {RightsMap, SensitivityLevelMap} from "./DefaultObjects"
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

function sortFilesByFavourite(files, asc) {
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

function sortFilesBySensitivity(files, asc) {
    let order = asc ? 1 : -1;
    files.sort((a, b) => {
        return SensitivityLevelMap[a.sensitivityLevel] -  SensitivityLevelMap[b.sensitivityLevel] * order;
    });
    return files;
}

function favourite(file) {
    // TODO Favourite file based on URI (file.path.uri);
}

function getOwnerFromAcls(acls) {
    let userName = Cloud.username;
    let result = acls.filter(acl => acl.entity.displayName === userName);
    if (!result.length) {
        return "None"
    }
    return result[0].right;
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
            if (input.dismiss) {
                return;
            }
            const body = {
                toUser: input.value,
                onFile: filePath.uri,
                rights: type.value,
            };
            Cloud.put("/acl", {body: body}).then(response => {
                switch (response.statusCode) {
                    case 200: {
                        swal("Success!", `The file has been shared with ${name}`, "success");
                        break;
                    }
                    default: {
                        swal("Error", "An error occurred when sharing the file", "error");
                        break;
                    }
                }
            })
        });
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
            swal(`Not yet ${result.value}`);
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
        if (result.dismiss) { return; }
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

export {
    NotificationIcon,
    WebSocketSupport,
    buildBreadCrumbs,
    createFolder,
    favourite,
    sortFilesByTypeAndName,
    sortFilesByModified,
    sortFilesByFavourite,
    sortFilesByOwner,
    sortFilesBySensitivity,
    shareFile,
    getOwnerFromAcls,
    showFileDeletionPrompt,
    sendToAbacus,
    renameFile,
    getParentPath,
}
