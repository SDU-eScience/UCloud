import swal from "sweetalert2";
import { RightsMap } from "./DefaultObjects";
import Cloud from "Authentication/lib";
import { SemanticICONS } from "semantic-ui-react";
import { SortBy, SortOrder } from "./Files";
import { Page, AccessRight } from "./Types";
import { File, Acl } from "./Files"
import { Application, ApplicationInformation } from "Applications";

export const toLowerCaseAndCapitalize = (str: string): string => !str ? "" : str.charAt(0).toUpperCase() + str.toLowerCase().slice(1);

/**
 * Checks if a pathname is legal/already in use
 * @param {string} path The path being tested
 * @param {string[]} filePaths the other file paths path is being compared against
 * @returns whether or not the path is invalid
 */
export const isInvalidPathName = (path: string, filePaths: string[]): boolean => {
    const disallowedName = ["..", ".", "/", ""].some((it) => it === path);
    if (disallowedName) { failureNotification("Folder name cannot be '.', '..' or '/' or empty"); return true; }
    const existingName = filePaths.some((it) => it === path);
    if (existingName) { failureNotification("File with that name already exists"); return true; }
    return false;
};

/**
 * Checks if the specific folder is a fixed folder, meaning it can not be removed, renamed, deleted, etc.
 * @param {string} filePath the path of the file to be checked
 * @param {string} homeFolder the path for the homefolder of the current user
 */
export const isFixedFolder = (filePath: string, homeFolder: string): boolean => {
    return [ // homeFolder contains trailing slash
        `${homeFolder}Favorites`,
        `${homeFolder}Jobs`,
        `${homeFolder}Trash bin`
    ].some((it) => removeTrailingSlash(it) === filePath)
};

// FIXME rename favorite lambda. Favorite doesn't make sense as a name
/**
 * Used for favoriting a file based on a path and page consisting of files.
 * @param {Page<File>} page The page of files to be searched through
 * @param {string} path The path of the file to be favorited
 * @param {Cloud} cloud The instance of a Cloud object used for requests
 * @returns {Page<File>} The page of files with the file favorited
 */
export const favorite = (page: Page<File>, path: string, cloud: Cloud): Page<File> => {
    let file = page.items.find((file: File) => file.path === path);
    favoriteFile(file, cloud);
    return page;
};

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFile = (file: File, cloud: Cloud): void => {
    file.favorited = !file.favorited;
    if (file.favorited)
        cloud.post(`/files/favorite?path=${file.path}`, {});
    else
        cloud.delete(`/files/favorite?path=${file.path}`, {});
}

/**
 * Returns a string based on the amount of users associated with the ACL
 * @param {Acl[]} acls - the list of access controls
 * @return {string}
 */
export const getOwnerFromAcls = (acls: Acl[]): string => {
    if (acls.length > 0) {
        return `${acls.length + 1} members`;
    } else {
        return "Only You";
    }
};

export const failureNotification = (title: string, seconds: number = 3) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: seconds * 1_000,
    type: "error",
    backdrop: false,
    title
});

export const successNotification = (title: string) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 3000,
    type: "success",
    backdrop: false,
    title
});

export const infoNotification = (title: string) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 3000,
    type: "info",
    backdrop: false,
    title
});

// FIXME React Semantic UI Forms doesn't seem to allow checkboxes with labels, unless browser native checkboxes
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

const deletionSwal = (filePaths: string[]) => {
    const deletionText = filePaths.length > 1 ? `Delete ${filePaths.length} files?` :
        `Delete file ${getFilenameFromPath(filePaths[0])}`;
    return swal({
        title: "Delete files",
        text: deletionText,
        confirmButtonText: "Delete files",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    })
};

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
    !path ? "" : path.split("/").filter(p => p).pop();


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

export const getSortingIcon = (sortBy: SortBy, sortOrder: SortOrder, name: SortBy): SemanticICONS => {
    if (sortBy === name) {
        return sortOrder === SortOrder.DESCENDING ? "chevron down" : "chevron up";
    }
    return null;
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
        case "svg":
        case "jpg":
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
        case "tar":
            return "file archive outline";
        default:
            if (getFilenameFromPath(filePath).split(".").length > 1)
                console.warn(`Unhandled extension "${filePath}" for file ${filePath}`);
            return "file outline";
    }
};

// TODO Remove navigation when backend support comes.
export const createProject = (filePath: string, cloud: Cloud, navigate: (path: string) => void) =>
    cloud.put("/projects", { fsRoot: filePath }).then(() => {
        redirectToProject(filePath, cloud, navigate, 5);
    }).catch(() => failureNotification(`An error occurred creating project ${filePath}`));

const redirectToProject = (path: string, cloud: Cloud, navigate: (path: string) => void, remainingTries: number) => {
    cloud.get(`/metadata/by-path?path=${path}`).then(() => navigate(path)).catch(() => {
        if (remainingTries > 0) {
            setTimeout(redirectToProject(path, cloud, navigate, remainingTries - 1), 400);
        } else {
            successNotification(`Project ${path} is being created.`)
        }
    });
};

export const canBeProject = (file: File, homeFolder: string) => isDirectory(file) && !isFixedFolder(file.path, homeFolder) && !isLink(file);
export const isProject = (file: File) => file.type === "DIRECTORY" && file.annotations.some(it => it === "P");

export const toFileText = (selectedFiles: File[]): string =>
    selectedFiles.length > 1 ? `${selectedFiles.length} files selected.` : getFilenameFromPath(selectedFiles[0].path);

export const isLink = (file: File) => file.link;
export const isDirectory = (file: File) => file.type === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string) => addTrailingSlash(path).replace(addTrailingSlash(homeFolder), "Home/");
export const inRange = (status: number, min: number, max: number): boolean => status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange(status, 200, 299);
export const removeTrailingSlash = (path: string) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const addTrailingSlash = (path: string) => path.endsWith("/") ? path : `${path}/`;
export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();

/**
 * Shortens the passed string if the length exceeds max size. If it exceeds, the string is shortened, trimmed, and 
 * ellipses are added.
 * @param {string} content the string to be shortened
 * @param {number} maxSize the maximum size allowed for the string
 */
export const shortenString = (content: string, maxLength: number): string => {
    if (content.length < maxLength) return content;
    return content.slice(0, maxLength).trim().concat("...");
}

export const blankOrNull = (value: string): boolean => value == null || value.length == 0 || /^\s*$/.test(value);

export const ifPresent = (f, handler: (f: any) => void) => {
    if (f) handler(f)
};


/**
 * //FIXME Missing backend functionality
 * Favorites an application. 
 * @param {Application} Application the application to be favorited
 * @param {Cloud} cloud The cloud instance for requests
 */
export const favoriteApplicationFromPage = (application: Application, page: Page<Application>, cloud: Cloud):Page<Application> => {
    const a = page.items.find(it => it.description.info.name === application.description.info.name);
    a.favorite = !a.favorite;
    infoNotification("Backend functionality for favoriting applications missing");
    return page;
/*  const {info} = a.description;
    if (a.favorite) {
        cloud.post(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } else {
        cloud.delete(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } */
}


export const favoriteApplication = (app) => {
    app.favorite = !app.favorite;
    if (app.favorite) {
        // post
    } else {
        // delete
    }
    return app;
}

export function defaultErrorHandler(error: any): number {
    let request: XMLHttpRequest = error.request;
    let why: string = null;

    if (!!error.response && !!error.response.why) {
        why = error.response.why;
    }

    if (!!request) {
        if (!why) {
            switch (request.status) {
                case 400:
                    why = "Bad request";
                    break;
                case 403:
                    why = "Permission denied";
                    break;
                default:
                    why = "Internal Server Error. Try again later.";
                    break;
            }
        }

        failureNotification(why);
        return request.status;
    }
    return 500;
}