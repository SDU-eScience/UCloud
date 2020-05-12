import {Client} from "Authentication/HttpClientInstance";
import {VirtualFileTable, VirtualFolderDefinition} from "Files/VirtualFileTable";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";
import {Box} from "ui-components";
import {
    isDirectory,
    isProjectHome,
    MOCK_RELATIVE,
    MOCK_VIRTUAL,
    mockFile,
    resolvePath,
    pathComponents
} from "Utilities/FileUtilities";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";
import {File, FileSelectorProps} from ".";
import {FileOperationRepositoryMode} from "Files/FileOperations";
import {Page} from "Types";
import {callAPIWithErrorHandler, callAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {listFavorites} from "./favorite";
import {buildQueryString} from "Utilities/URIUtilities";
import {listRepositoryFiles, UserInProject} from "Project";

const FileSelector: React.FunctionComponent<FileSelectorProps> = props => {
    const [path, setPath] = useState<string>(Client.hasActiveProject ? Client.currentProjectFolder : Client.homeFolder);
    useEffect(() => {
        if (props.initialPath !== undefined) setPath(props.initialPath);
    }, [props.initialPath]);

    const virtualFolders = useVirtualFolders(path);
    const injectedFiles: File[] = [];
    if (resolvePath(path) !== resolvePath(Client.homeFolder) && !isProjectHome(path)) {
        injectedFiles.push(mockFile({
            path: `${addTrailingSlash(path)}..`,
            fileId: "parent",
            type: "DIRECTORY",
            tag: MOCK_RELATIVE
        }));
    }

    const fakeFolders = virtualFolders.fakeFolders ? virtualFolders.fakeFolders : [];
    if (fakeFolders.every(it => resolvePath(it) !== resolvePath(path))) {
        injectedFiles.push(mockFile({
            path: `${addTrailingSlash(path)}.`,
            fileId: "cwd",
            type: "DIRECTORY",
            tag: MOCK_RELATIVE
        }));
    }

    const components = pathComponents(path);

    if (Client.hasActiveProject && components.length === 2 && components[0] === "home") {
        injectedFiles.push(mockFile({
            path: Client.currentProjectFolder,
            fileId: "project",
            type: "DIRECTORY",
            tag: MOCK_VIRTUAL
        }));
    } else if (isProjectHome(path)) {
        injectedFiles.push(mockFile({
            path: Client.homeFolder,
            fileId: "project",
            type: "DIRECTORY",
            tag: MOCK_VIRTUAL
        }));
    }

    const canSelectFolders = !!props.canSelectFolders;

    return (
        <>
            {props.trigger}

            <ReactModal
                isOpen={props.visible}
                shouldCloseOnEsc
                ariaHideApp={false}
                onRequestClose={() => props.onFileSelect(null)}
                style={FileSelectorModalStyle}
            >
                <Box>
                    <VirtualFileTable
                        loadFolder={virtualFolders.loadFolder}
                        fakeFolders={virtualFolders.fakeFolders}
                        omitQuickLaunch
                        embedded
                        fileOperations={[{
                            text: "Select",
                            repositoryMode: FileOperationRepositoryMode.ANY,
                            onClick: files => props.onFileSelect(files[0]),
                            disabled: files => {
                                if (files.some(it => isProjectHome(it.path))) {
                                    return true;
                                }

                                if (files.some(it => removeTrailingSlash(resolvePath(it.path)) === "/projects")) {
                                    return true;
                                }

                                if (files.some(it => it.mockTag !== undefined && it.mockTag !== MOCK_RELATIVE)) {
                                    return true;
                                }

                                return !(files.length === 1 && (
                                    (canSelectFolders && files[0].fileType === "DIRECTORY") ||
                                    (!canSelectFolders && files[0].fileType === "FILE")
                                ));
                            }
                        }]}
                        foldersOnly={props.onlyAllowFolders}
                        fileFilter={file => !props.onlyAllowFolders || isDirectory(file)}
                        onFileNavigation={setPath}
                        injectedFiles={injectedFiles}
                        path={path}
                    />
                </Box>
            </ReactModal>
        </>
    );
};

const useVirtualFolders = (path: string): VirtualFolderDefinition => {
    const fakeFolders = Client.fakeFolders;
    const [, projectName] = pathComponents(path);
    const homeProjectList = `${Client.homeFolder}Project List`;
    const projectProjectList = `/projects/${projectName}/Project List`;
    fakeFolders.push(homeProjectList);
    fakeFolders.push(projectProjectList);
    return {
        fakeFolders,
        loadFolder: async (folder, page, itemsPerPage): Promise<Page<File>> => {
            if (folder === Client.favoritesFolder) {
                const favs = (await callAPIWithErrorHandler<Page<File>>(listFavorites({page, itemsPerPage})));
                return favs ?? emptyPage;
            } else if (folder === Client.sharesFolder) {
                return (await Client.get<Page<File>>(
                    buildQueryString("/shares/list-files", {page, itemsPerPage}))
                ).response;
            } else if (isProjectHome(folder)) {
                try {
                    const response = await callAPI<Page<File>>(listRepositoryFiles({page, itemsPerPage}));
                    response.items.forEach(f => f.isRepo = true);
                    return response;
                } catch (err) {
                    // Edge case that no repos exist for for project, but we want it to be empty instead of non-existant.
                    if (err.request.status === 404) return emptyPage;
                    else throw err;
                }
            } else if ([homeProjectList, projectProjectList].includes(folder)) {
                const {response} = await Client.get<Page<UserInProject>>(buildQueryString("/projects/list", {itemsPerPage, page}));
                return {
                    ...response,
                    items: response.items.map(it => mockFile({
                        path: `/projects/${it.projectId}`,
                        type: "DIRECTORY",
                        fileId: it.projectId,
                        tag: MOCK_VIRTUAL
                    }))
                };
            } else {
                return emptyPage;
            }
        }
    };
};

const FileSelectorModalStyle = {
    content: {
        top: "80px",
        left: "25%",
        right: "25%",
        background: ""
    }
};

export default FileSelector;
