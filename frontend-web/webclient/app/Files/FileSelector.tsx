import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {
    MOCK_RELATIVE,
    mockFile,
    resolvePath
} from "Utilities/FileUtilities";
import {
    File,
    FileSelectorProps,
} from ".";
import {Flex} from "ui-components";
import * as ReactModal from "react-modal";
import {addTrailingSlash} from "UtilityFunctions";
import {useEffect, useState} from "react";
import {defaultVirtualFolders, VirtualFileTable} from "Files/VirtualFileTable";

const FileSelector: React.FunctionComponent<FileSelectorProps> = props => {
    const [path, setPath] = useState<string>(Cloud.homeFolder);
    useEffect(() => {
        if (props.initialPath !== undefined) setPath(props.initialPath);
    }, [props.initialPath]);

    let virtualFolders = defaultVirtualFolders();
    const injectedFiles: File[] = [];
    if (resolvePath(path) !== resolvePath(Cloud.homeFolder)) {
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
        <Flex backgroundColor="white">
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
                    numberOfColumns={0}
                    fileOperations={[{
                        text: "Select",
                        onClick: files => props.onFileSelect(files[0]),
                        disabled: files => {
                            if (files.some(it => it.mockTag !== undefined && it.mockTag !== MOCK_RELATIVE)) {
                                return true;
                            }

                            return !(files.length === 1 && (
                                (canSelectFolders && files[0].fileType === "DIRECTORY") ||
                                (!canSelectFolders && files[0].fileType === "FILE")
                            ))
                        }
                    }]}
                    fileFilter={file => !props.onlyAllowFolders || file.fileType === "DIRECTORY"}
                    onFileNavigation={path => setPath(path)}
                    injectedFiles={injectedFiles}
                    path={path}/>
            </ReactModal>
        </Flex>
    )
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
