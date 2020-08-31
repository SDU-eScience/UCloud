import {Client} from "Authentication/HttpClientInstance";
import {VirtualFileTable, VirtualFolderDefinition, defaultVirtualFolders} from "Files/VirtualFileTable";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";
import {
    isDirectory,
    isProjectHome,
    MOCK_RELATIVE,
    MOCK_VIRTUAL,
    mockFile,
    resolvePath,
} from "Utilities/FileUtilities";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";
import {File, FileSelectorProps} from ".";
import {FileOperationRepositoryMode} from "Files/FileOperations";
import {callAPI} from "Authentication/DataHook";
import {listProjects, UserInProject} from "Project";

const FileSelector: React.FunctionComponent<FileSelectorProps> = props => {
    const [path, setPath] = useState<string>(Client.hasActiveProject ? Client.currentProjectFolder : Client.homeFolder);
    useEffect(() => {
        if (props.initialPath !== undefined) setPath(props.initialPath);
    }, [props.initialPath]);

    const virtualFolders = useVirtualFolders(path);
    const injectedFiles: File[] = [];
    if (resolvePath(path) !== resolvePath(Client.homeFolder) && !isProjectHome(path) && path !== fakeProjectListPath) {
        injectedFiles.push(mockFile({
            path: `${addTrailingSlash(path)}..`,
            fileId: "parent",
            type: "DIRECTORY",
            tag: MOCK_RELATIVE
        }));
    }

    const fakeFolders = virtualFolders.fakeFolders ? virtualFolders.fakeFolders : [];
    if (fakeFolders.every(it => resolvePath(it) !== resolvePath(path)) && !isProjectHome(path)
        && path !== fakeProjectListPath) {
        injectedFiles.push(mockFile({
            path: `${addTrailingSlash(path)}.`,
            fileId: "cwd",
            type: "DIRECTORY",
            tag: MOCK_RELATIVE
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
                <VirtualFileTable
                    {...virtualFolders}
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
            </ReactModal>
        </>
    );
};

export const fakeProjectListPath = `/Project List`;

const useVirtualFolders = (path: string): VirtualFolderDefinition => {
    const {fakeFolders, loadFolder, isFakeFolder} = defaultVirtualFolders();
    return {
        fakeFolders,
        isFakeFolder: folder => fakeProjectListPath === folder || (isFakeFolder?.(folder) ?? false),
        loadFolder: async (folder, page, itemsPerPage): Promise<Page<File>> => {
            if (fakeProjectListPath === folder) {
                const response = await callAPI<Page<UserInProject>>(
                    listProjects({itemsPerPage, page, archived: false})
                );
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
                return loadFolder!(folder, page, itemsPerPage);
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
