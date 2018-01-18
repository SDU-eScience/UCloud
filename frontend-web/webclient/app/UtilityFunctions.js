import React from "react";
import swal from "sweetalert";

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


function sortFiles(files) {
    files.sort((a, b) => {
        if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
            return -1;
        else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
            return 1;
        else {
            return a.path.name.localeCompare(b.path.name);
        }
    });
    return files;
}

function favourite(file) {
    // TODO Favourite file based on URI (file.path.uri);
}

function createFolder(currentPath) {
    swal({
        title: "Create folder",
        text: `The folder will be created in:\n${currentPath}`,
        content: {
            element: "input",
            attributes: {
                placeholder: "Folder name...",
                type: "text",
            },
        },
        placeholder: "Folder name...",
        buttons: {
            confirm: {
                text: "Create folder",
                closeModal: false,
            }
        }
    }).then(name => {
        // TODO: Connect to backend.
    })
}

export {
    NotificationIcon,
    WebSocketSupport,
    buildBreadCrumbs,
    sortFiles,
    createFolder,
    favourite
}
