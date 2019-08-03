import * as React from "react";
import {List as PaginationList} from "Pagination/List";
import {Cloud} from "Authentication/SDUCloudObject";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import {
    favoritesQuery,
    filepathQuery,
    isDirectory, MOCK_RELATIVE, MOCK_VIRTUAL, mockFile,
    newMockFolder,
    resolvePath
} from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import {emptyPage} from "DefaultObjects";
import {
    File,
    FileOperation,
    FileResource,
    FileSelectorProps,
    FileSource,
    SortBy,
    SortOrder
} from ".";
import {Box, Button, Flex, Icon, Input, SelectableText, SelectableTextWrapper} from "ui-components";
import * as ReactModal from "react-modal";
import {Spacer} from "ui-components/Spacer";
import FilesTable from "./FilesTable";
import SDUCloud from "Authentication/lib";
import {addTrailingSlash, errorMessageOrDefault} from "UtilityFunctions";
import {Refresh} from "Navigation/Header";
import {Page} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";
import {useState} from "react";
import {LowLevelFilesTable} from "Files/LowLevelFilesTable";
import {defaultVirtualFolders, VirtualFilesTable} from "Files/VirtualFilesTable";

const FileSelector: React.FunctionComponent<FileSelectorProps> = props => {
    const [path, setPath] = useState<string>(Cloud.homeFolder);

    const injectedFiles: File[] = [];
    if (resolvePath(path) !== resolvePath(Cloud.homeFolder)) {
        injectedFiles.push(mockFile({
            path: `${addTrailingSlash(path)}..`,
            fileId: "parent",
            type: "DIRECTORY",
            tag: MOCK_RELATIVE
        }));
    }

    injectedFiles.push(mockFile({
        path: `${addTrailingSlash(path)}.`,
        fileId: "cwd",
        type: "DIRECTORY",
        tag: MOCK_RELATIVE
    }));

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
                <VirtualFilesTable
                    {...defaultVirtualFolders()}
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
                    fileFilter={file => canSelectFolders || file.fileType === "DIRECTORY"}
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