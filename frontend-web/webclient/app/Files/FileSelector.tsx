import {Client} from "Authentication/HttpClientInstance";
import {defaultVirtualFolders, VirtualFileTable} from "Files/VirtualFileTable";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";
import {Box} from "ui-components";
import {isDirectory, MOCK_RELATIVE, mockFile, resolvePath} from "Utilities/FileUtilities";
import {addTrailingSlash} from "UtilityFunctions";
import {File, FileSelectorProps} from ".";

const FileSelector: React.FunctionComponent<FileSelectorProps> = props => {
    const [path, setPath] = useState<string>(Client.homeFolder);
    useEffect(() => {
        if (props.initialPath !== undefined) setPath(props.initialPath);
    }, [props.initialPath]);

    const virtualFolders = defaultVirtualFolders();
    const injectedFiles: File[] = [];
    if (resolvePath(path) !== resolvePath(Client.homeFolder)) {
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
                        {...virtualFolders}
                        omitQuickLaunch
                        embedded
                        fileOperations={[{
                            text: "Select",
                            onClick: files => props.onFileSelect(files[0]),
                            disabled: files => {
                                if (files.some(it => addTrailingSlash(resolvePath(it.path)) === Client.currentProjectFolder)) {
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

const FileSelectorModalStyle = {
    content: {
        top: "80px",
        left: "25%",
        right: "25%",
        background: ""
    }
};

export default FileSelector;
