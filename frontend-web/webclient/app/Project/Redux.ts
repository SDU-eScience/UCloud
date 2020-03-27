import {File} from "Files";
import {Action} from "redux";
import {Dictionary, Page} from "Types";
import {getParentPath, resolvePath} from "Utilities/FileUtilities";

export interface State {
    project?: string;
    files?: Dictionary<ProjectFilesCacheEntry>;
}

export interface ProjectFilesCacheEntry {
    project: string;
    path: string;
    files: Page<File>;
}

export const initialState = {project: getStoredProject() ?? undefined};

interface SetProjectAction extends Action<"SET_PROJECT"> {
    project?: string;
}

export function setProjectAction(project?: string): SetProjectAction {
    return {project, type: "SET_PROJECT"};
}

interface UpdateProjectFileAction extends Action<"UPDATE_PROJECT_FILE"> {
    newFile: File;
}

export function updateProjectFileAction(newFile: File): UpdateProjectFileAction {
    return {newFile, type: "UPDATE_PROJECT_FILE"};
}

type CacheProjectFilesAction = Action<"CACHE_PROJECT_FILES"> & ProjectFilesCacheEntry;

export function cacheProjectFilesAction(entry: ProjectFilesCacheEntry): CacheProjectFilesAction {
    return {...entry, type: "CACHE_PROJECT_FILES"};
}

type ClearDirtFlagAction = Action<"CACHE_PROJECT_FILES_CLEAR_DIRTY">;

export function clearProjectFilesDirtyFlag(): ClearDirtFlagAction {
    return {type: "CACHE_PROJECT_FILES_CLEAR_DIRTY"};
}

type ProjectAction = SetProjectAction | CacheProjectFilesAction | UpdateProjectFileAction | ClearDirtFlagAction;

export const reducer = (state: State = initialState, action: ProjectAction) => {
    switch (action.type) {
        case "SET_PROJECT": {
            setStoredProject(action.project ?? null);
            return {...state, project: action.project};
        }

        case "CACHE_PROJECT_FILES": {
            const existingDirectoryOrInitial = state.files ?? {};
            const files = {...existingDirectoryOrInitial};
            files[action.path] = action;
            return {...state, files};
        }

        case "UPDATE_PROJECT_FILE": {
            const parentPath = resolvePath(getParentPath(action.newFile.path));
            const newFile = {...action.newFile, frontend: {dirty: true}};
            const cachedParent: ProjectFilesCacheEntry = state.files?.[parentPath] ??
                {
                    project: state.project!,
                    path: parentPath,
                    files: {
                        items: [newFile],
                        pageNumber: 0,
                        itemsPerPage: 50,
                        itemsInTotal: 1,
                        pagesInTotal: 1
                    }
                };

            const directoryCopy = cachedParent.files.items.slice();
            for (let i = 0; i < directoryCopy.length; i++) {
                const file = directoryCopy[i];
                if (file.path === newFile.path) {
                    directoryCopy[i] = newFile;
                    break;
                }
            }

            const files = {...state.files};
            files[parentPath] = {...cachedParent, files: {...cachedParent.files, items: directoryCopy}};
            return {...state, files};
        }

        case "CACHE_PROJECT_FILES_CLEAR_DIRTY": {
            const files: Dictionary<ProjectFilesCacheEntry> = {...state.files};
            Object.keys(files).forEach(key => {
                const current = files[key];
                files[key] = {
                    ...current,
                    files: {
                        ...current.files,
                        items: current.files.items.map(it => ({...it, frontend: undefined}))
                    }
                };
            });

            return {...state, files};
        }

        default:
            return state;
    }
};

function getStoredProject(): string | null {
    return window.localStorage.getItem("project") ?? null;
}

function setStoredProject(value: string | null) {
    if (value === null) {
        window.localStorage.removeItem("project");
    } else {
        window.localStorage.setItem("project", value);
    }
}

