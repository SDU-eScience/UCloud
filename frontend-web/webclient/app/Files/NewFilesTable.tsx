import * as React from "react";
import {File, FileResource, SortBy, SortOrder} from "Files/index";
import * as UF from "UtilityFunctions"
import {APICallParameters, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page} from "Types";
import Table, {TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import styled from "styled-components";
import Flex from "ui-components/Flex";
import Box from "ui-components/Box";
import {Arrow, FileIcon} from "UtilityComponents";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {TextSpan} from "ui-components/Text";
import Divider from "ui-components/Divider";
import {emptyPage, KeyCode, SensitivityLevelMap} from "DefaultObjects";
import {Cloud} from "Authentication/SDUCloudObject";
import {MainContainer} from "MainContainer/MainContainer";
import Checkbox from "ui-components/Checkbox";
import {Button, Icon, Input, Label, OutlineButton, Tooltip, Truncate} from "ui-components";
import {
    createFolder,
    getFilenameFromPath, getParentPath,
    isDirectory,
    isInvalidPathName, moveFile, newMockFolder,
    replaceHomeFolder
} from "Utilities/FileUtilities";
import BaseLink from "ui-components/BaseLink";
import Theme from "ui-components/theme";
import {defaultFileOperations, FileOperation, FileOperationCallback} from "Files/NewFileOperations";
import {SpaceProps} from "styled-system";
import {useEffect, useState} from "react";
import * as Heading from "ui-components/Heading";
import * as Pagination from "Pagination";
import {Spacer} from "ui-components/Spacer";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";

interface NewFilesTableProps {
    page?: Page<File>
    path?: string
    onFileNavigation: (path: string) => void
    embedded?: boolean

    fileOperations?: FileOperation[]
    onReloadRequested?: () => void

    injectedFiles?: File[]
}

export interface ListDirectoryRequest {
    path: string
    page: number
    itemsPerPage: number
    order: SortOrder
    sortBy: SortBy
    attrs: FileResource[]
}

export const listDirectory = ({path, page, itemsPerPage, order, sortBy, attrs}: ListDirectoryRequest): APICallParameters<ListDirectoryRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/files",
        {
            path,
            page,
            itemsPerPage,
            order,
            sortBy,
            attrs: attrs.join(",")
        }
    ),
    parameters: {path, page, itemsPerPage, order, sortBy, attrs},
    reloadId: Math.random()
});

const invertSortOrder = (order: SortOrder): SortOrder => {
    switch (order) {
        case SortOrder.ASCENDING:
            return SortOrder.DESCENDING;
        case SortOrder.DESCENDING:
            return SortOrder.ASCENDING;
    }
};

export const NewFilesTable: React.FunctionComponent<NewFilesTableProps> = props => {
    if (props.page !== undefined && props.path !== undefined) {
        throw Error("FilesTable contain both page and path properties, these are mutually exclusive.");
    }

    if (props.page === undefined && props.path === undefined) {
        throw Error("FilesTable must set either path or page property");
    }

    if (props.page !== undefined) {
        throw Error("Not yet implemented");
    }

    const isEmbedded = props.embedded !== false;
    const sortingSupported = props.path !== undefined;

    const [checkedFiles, setCheckedFiles] = useState<Set<string>>(new Set());
    const [fileBeingRenamed, setFileBeingRenamed] = useState<string | null>(null);
    const [sortByColumns, setSortByColumns] = useState<[SortBy, SortBy]>(getSortingColumns());

    const [injectedViaState, setInjectedViaState] = useState<File[]>([]);

    // TODO Some of these callbacks should use "useCallback"?
    // TODO Two phase load.
    const [page, setPageParams, pageParams] = useCloudAPI<Page<File>>(listDirectory({
        path: props.path!,
        itemsPerPage: 25,
        page: 0,
        sortBy: SortBy.PATH,
        order: SortOrder.ASCENDING,
        attrs: []
    }), emptyPage);

    const allFiles = injectedViaState.concat(props.injectedFiles ? props.injectedFiles : []).concat(page.data.items);
    const pageParameters: ListDirectoryRequest = pageParams.parameters!;

    useEffect(() => {
        setPageParams(listDirectory({...pageParameters, path: props.path!}));
    }, [props.path]);

    const setSorting = (sortBy: SortBy, order: SortOrder, column?: number) => {
        if (!sortingSupported) return;
        if (column !== undefined) {
            setSortingColumnAt(sortBy, column as 0 | 1);

            const newColumns: [SortBy, SortBy] = [sortByColumns[0], sortByColumns[1]];
            newColumns[column] = sortBy;
            setSortByColumns(newColumns);
        }

        setPageParams(
            listDirectory({
                ...pageParameters,
                sortBy,
                order,
                page: 0
            })
        );
    };

    const [commandState, invokeCommand] = useAsyncCommand();

    const sortingIconFor = (other: SortBy): "arrowUp" | "arrowDown" | undefined => {
        if (other === pageParameters.sortBy) {
            if (pageParameters.order === SortOrder.ASCENDING) {
                return "arrowUp";
            } else {
                return "arrowDown";
            }
        }

        return undefined;
    };

    const callbacks: FileOperationCallback = {
        invokeCommand,
        requestReload: () => {
            setFileBeingRenamed(null);
            setCheckedFiles(new Set());
            setInjectedViaState([]);

            if (props.path !== undefined) {
                setPageParams(listDirectory(pageParameters));
            } else if (props.onReloadRequested !== undefined) {
                props.onReloadRequested();
            }
        },
        requestFolderCreation: () => {
            let fileId = "newFolderId";
            setInjectedViaState([newMockFolder(`${props.path!}/newFolder`, true, fileId)]);
            setFileBeingRenamed(fileId);
        },
        startRenaming: file => setFileBeingRenamed(file.fileId!)
    };

    const fileOperations = props.fileOperations !== undefined ? props.fileOperations : defaultFileOperations;

    const setChecked = (updatedFiles: File[], checkStatus?: boolean) => {
        const checked = new Set(checkedFiles);
        if (checkStatus === false) {
            checked.clear();
        } else {
            updatedFiles.forEach(file => {
                const fileId = file.fileId!;
                if (checkStatus === true) {
                    checked.add(fileId);
                } else {
                    if (checked.has(fileId)) checked.delete(fileId);
                    else checked.add(fileId);
                }
            });
        }

        setCheckedFiles(checked);
    };

    const isMasterChecked = allFiles.length > 0 && allFiles.every(f => checkedFiles.has(f.fileId!));

    const onRenameFile = (key: number, file: File, name: string) => {
        if (key === KeyCode.ESC) {
            setInjectedViaState([]);
            setFileBeingRenamed(null);
        } else if (key === KeyCode.ENTER) {
            const file = allFiles.find(f => f.fileId == fileBeingRenamed);
            if (file === undefined) return;
            const fileNames = allFiles.map(file => getFilenameFromPath(file.path));
            if (isInvalidPathName({path: name, filePaths: fileNames})) return;

            const fullPath = `${UF.addTrailingSlash(getParentPath(file.path))}${name}`;
            if (file.isMockFolder) {
                createFolder({
                    path: fullPath,
                    cloud: Cloud,
                    onSuccess: () => callbacks.requestReload()
                })
            } else {
                moveFile({
                    oldPath: file.path,
                    newPath: fullPath,
                    cloud: Cloud,
                    setLoading: () => 42, // TODO
                    onSuccess: () => callbacks.requestReload()
                });
            }
        }
    };

    return <Shell
        embedded={isEmbedded}

        header={
            <Spacer
                left={
                    <BreadCrumbs
                        currentPath={props.path!}
                        navigate={path => props.onFileNavigation(path)}
                        homeFolder={Cloud.homeFolder}/>
                }

                right={
                    <Pagination.EntriesPerPageSelector
                        content="Files per page"
                        entriesPerPage={42} // TODO
                        onChange={() => 42} // TODO
                    />
                }
            />
        }

        sidebar={
            <Box pl="5px" pr="5px">
                <VerticalButtonGroup>
                    <SidebarContent>
                        <FileOperations
                            files={allFiles.filter(f => f.fileId && checkedFiles.has(f.fileId))}
                            fileOperations={fileOperations}
                            callback={callbacks}
                            directory={newMockFolder(props.path!)}
                        />
                    </SidebarContent>
                </VerticalButtonGroup>
            </Box>
        }

        main={
            <Pagination.List
                loading={page.loading}
                customEmptyPage={!page.error ? <Heading.h3>No files in current folder</Heading.h3> : <Box/>}
                page={page.data}
                onPageChanged={(a, b) => 42} // TODO
                pageRenderer={() =>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <FileTableHeaderCell notSticky={isEmbedded} textAlign="left" width="99%">
                                    <Flex
                                        backgroundColor="white"
                                        alignItems="center"
                                        onClick={() => setSorting(SortBy.PATH, invertSortOrder(pageParameters.order))}
                                    >
                                        <Box mx="9px" onClick={e => e.stopPropagation()}>
                                            {isEmbedded ? null :
                                                <Label>
                                                    <Checkbox
                                                        data-tag="masterCheckbox"
                                                        onClick={e => setChecked(allFiles, !isMasterChecked)}
                                                        checked={isMasterChecked}
                                                        onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                                    />
                                                </Label>
                                            }
                                        </Box>
                                        <Arrow name={sortingIconFor(SortBy.PATH)}/>
                                        <Box cursor="pointer">Filename</Box>
                                    </Flex>
                                </FileTableHeaderCell>
                                <FileTableHeaderCell notSticky={isEmbedded} width={"3em"}>
                                    <Flex backgroundColor="white"/>
                                </FileTableHeaderCell>

                                {sortByColumns.filter(it => it != null).map((column, i) => {
                                    const isSortedBy = pageParameters.sortBy === column;

                                    return <FileTableHeaderCell notSticky={isEmbedded} width="10rem">
                                        <Flex backgroundColor="white" alignItems="center" cursor="pointer"
                                              justifyContent="left">
                                            <Box
                                                onClick={() => setSorting(column, invertSortOrder(pageParameters.order), i)}>
                                                <Arrow name={sortingIconFor(column)}/>
                                            </Box>
                                            {!sortingSupported ?
                                                <>UF.sortByToPrettierString(column)</>
                                                :
                                                <ClickableDropdown
                                                    trigger={<TextSpan>{UF.sortByToPrettierString(column)}</TextSpan>}
                                                    chevron>
                                                    <Box ml="-16px" mr="-16px" pl="15px"
                                                         hidden={pageParameters.order === SortOrder.ASCENDING && isSortedBy}
                                                         onClick={() => setSorting(column, SortOrder.ASCENDING, i)}
                                                    >
                                                        {UF.prettierString(SortOrder.ASCENDING)}
                                                    </Box>
                                                    <Box ml="-16px" mr="-16px" pl="15px"
                                                         onClick={() => setSorting(column, SortOrder.DESCENDING, i)}
                                                         hidden={pageParameters.order === SortOrder.DESCENDING && isSortedBy}
                                                    >
                                                        {UF.prettierString(SortOrder.DESCENDING)}
                                                    </Box>
                                                    <Divider ml="-16px" mr="-16px"/>
                                                    {Object.values(SortBy).map((sortByKey: SortBy, j) => (
                                                        <Box ml="-16px" mr="-16px" pl="15px" key={j}
                                                             onClick={() => setSorting(sortByKey, pageParameters.order, i)}
                                                             hidden={sortByKey === pageParameters.sortBy || sortByKey === SortBy.PATH}
                                                        >
                                                            {UF.sortByToPrettierString(sortByKey)}
                                                        </Box>
                                                    ))}
                                                </ClickableDropdown>
                                            }
                                        </Flex>
                                    </FileTableHeaderCell>
                                })}

                                <FileTableHeaderCell
                                    notSticky={isEmbedded}

                                    // TODO This is not correct. We had some custom code before. This should be ported.
                                    width={"7em"}
                                >
                                    <Flex/>
                                </FileTableHeaderCell>
                            </TableRow>
                        </TableHeader>

                        <TableBody>
                            {allFiles.map(file => (
                                <TableRow highlighted={file.isChecked} key={file.fileId!} data-tag={"fileRow"}>
                                    <TableCell>
                                        <Flex flexDirection="row" alignItems="center" mx="9px">
                                            {isEmbedded ? null :
                                                <Box>
                                                    <Label>
                                                        <Checkbox
                                                            checked={checkedFiles.has(file.fileId!)}
                                                            onChange={e => e.stopPropagation()}
                                                            onClick={() => setChecked([file])}/>
                                                    </Label>
                                                </Box>
                                            }
                                            <Box ml="5px" pr="5px"/>
                                            <NameBox file={file} onRenameFile={onRenameFile}
                                                     onNavigate={props.onFileNavigation}
                                                     invokeCommand={invokeCommand} fileBeingRenamed={fileBeingRenamed}/>
                                        </Flex>
                                    </TableCell>

                                    <TableCell>
                                        <SensitivityIcon sensitivity={file.sensitivityLevel}/>
                                    </TableCell>

                                    {sortByColumns.filter(it => it != null).map((sC, i) => (
                                        <TableCell key={i}>{sC ? UF.sortingColumnToValue(sC, file) : null}</TableCell>
                                    ))}

                                    <TableCell textAlign="center">
                                        {
                                            checkedFiles.size > 0 ? null :
                                                fileOperations.length > 1 ?
                                                    <ClickableDropdown
                                                        width="175px"
                                                        left="-160px"
                                                        trigger={<Icon name="ellipsis" size="1em" rotation="90"/>}
                                                    >
                                                        <FileOperations
                                                            files={[file]}
                                                            fileOperations={fileOperations}
                                                            inDropdown
                                                            ml="-17px"
                                                            mr="-17px"
                                                            pl="15px"
                                                            callback={callbacks}
                                                        />
                                                    </ClickableDropdown> :
                                                    <FileOperations
                                                        files={[file]}
                                                        fileOperations={fileOperations}
                                                        callback={callbacks}
                                                    />
                                        }
                                    </TableCell>
                                </TableRow>)
                            )}
                        </TableBody>
                    </Table>
                }
            />
        }
    />;
};

interface ShellProps {
    embedded: boolean
    header: React.ReactNode
    sidebar: React.ReactNode
    main: React.ReactNode
}

const Shell: React.FunctionComponent<ShellProps> = props => {
    if (props.embedded) return <>{props.main}</>;

    return <MainContainer
        header={props.header}
        main={props.main}
        sidebar={props.sidebar}
    />;
};

const SidebarContent = styled.div`
    grid: auto-flow;
    & > * {
        min-width: 75px;
        max-width: 225px;
        margin-left: 5px;
        margin-right: 5px;
    }
`;

interface NameBoxProps {
    file: File
    onRenameFile: (keycode: number, file: File, value: string) => void
    onNavigate: (path: string) => void
    invokeCommand: (call: APICallParameters) => void
    fileBeingRenamed: string | null
}

const NameBox: React.FunctionComponent<NameBoxProps> = props => {
    const canNavigate = isDirectory({fileType: props.file.fileType});

    const icon = <Box mr="10px" cursor="inherit">
        <FileIcon
            fileIcon={UF.iconFromFilePath(props.file.path, props.file.fileType, Cloud.homeFolder)}
            size={38} link={props.file.link} shared={(props.file.acl != null ? props.file.acl.length : 0) > 0}
        />
    </Box>;

    if (props.file.fileId !== null && props.file.fileId === props.fileBeingRenamed) {
        return <>
            {icon}

            <Input
                placeholder={props.file.isMockFolder ? "" : getFilenameFromPath(props.file.path)}
                defaultValue={props.file.isMockFolder ? "" : getFilenameFromPath(props.file.path)}
                p="0"
                noBorder
                maxLength={1024}
                borderRadius="0px"
                type="text"
                width="100%"
                autoFocus
                data-tag="renameField"
                onKeyDown={e => {
                    if (!!props.onRenameFile) props.onRenameFile(e.keyCode, props.file, (e.target as HTMLInputElement).value)
                }}
            />

            <Icon
                size={"1em"}
                color="red"
                ml="9px"
                name="close"
                onClick={() => {
                    if (!!props.onRenameFile) props.onRenameFile(KeyCode.ESC, props.file, "")
                }}
            />
        </>;
    } else {
        return <>
            <Flex data-tag={"fileName"} flex="0 1 auto" minWidth="0"> {/* Prevent name overflow */}
                <Box title={replaceHomeFolder(props.file.path, Cloud.homeFolder)} width="100%">
                    <BaseLink href={"#"} onClick={e => {
                        e.preventDefault();
                        props.onNavigate(props.file.path);
                    }}>
                        <Flex alignItems="center">
                            {icon}

                            <Truncate
                                cursor={canNavigate ? "pointer" : undefined}
                                mr="5px"
                            >
                                {getFilenameFromPath(props.file.path)}
                            </Truncate>
                        </Flex>
                    </BaseLink>
                </Box>
            </Flex>

            <Icon
                data-tag="fileFavorite"
                size="1em" ml=".7em"
                color={props.file.favorited ? "blue" : "gray"}
                name={props.file.favorited ? "starFilled" : "starEmpty"}
                // onClick={() => props.onFavorite(props.file)}
                // TODO Handle on favorite
                hoverColor="blue"
            />
        </>;
    }
};

const notSticky = ({notSticky}: { notSticky?: boolean }): { position: "sticky" } | null =>
    notSticky ? null : {position: "sticky"};

const FileTableHeaderCell = styled(TableHeaderCell) <{ notSticky?: boolean }>`
        background-color: ${({theme}) => theme.colors.white};
        top: 144px; //topmenu + header size
        z-index: 10;
        ${notSticky}
        `;

const SensitivityIcon = (props: { sensitivity: SensitivityLevelMap | null }) => {
    type IconDef = { color: string, text: string, shortText: string };
    let def: IconDef;

    switch (props.sensitivity) {
        case SensitivityLevelMap.CONFIDENTIAL:
            def = {color: Theme.colors.purple, text: "Confidential", shortText: "C"};
            break;
        case SensitivityLevelMap.SENSITIVE:
            def = {color: "#ff0004", text: "Sensitive", shortText: "S"};
            break;
        case SensitivityLevelMap.PRIVATE:
            def = {color: Theme.colors.midGray, text: "Private", shortText: "P"};
            break;
        default:
            def = {color: Theme.colors.midGray, text: "", shortText: ""};
            break;
    }

    const badge = <SensitivityBadge data-tag={"sensitivityBadge"} bg={def.color}>{def.shortText}</SensitivityBadge>;
    return <Tooltip right={"0"} top={"1"} mb="50px" trigger={badge}>{def.text}</Tooltip>;
};

const SensitivityBadge = styled.div<{ bg: string }>`
    content: '';
    height: 2em;
    width: 2em;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 0.2em solid ${props => props.bg};
    border-radius: 100%;
`;

interface FileOperations extends SpaceProps {
    files: File[],
    fileOperations: FileOperation[],
    callback: FileOperationCallback
    directory?: File
    inDropdown?: boolean
}

const FileOperations = ({files, fileOperations, ...props}: FileOperations) => {
    if (fileOperations.length === 0) return null;

    return <>
        {fileOperations.map((fileOp: FileOperation, i: number) => {
            if (fileOp.disabled(files)) return null;
            if (fileOp.currentDirectoryMode === true && props.directory === undefined) return null;
            if (fileOp.currentDirectoryMode !== true && files.length === 0) return null;

            let As: typeof OutlineButton | typeof Box | typeof Button | typeof Flex;
            if (fileOperations.length === 1) {
                As = OutlineButton;
            } else if (props.inDropdown === true) {
                As = Box;
            } else {
                if (fileOp.currentDirectoryMode === true) {
                    if (fileOp.outline === true) {
                        As = OutlineButton;
                    } else {
                        As = Button;
                    }
                } else {
                    As = Flex;
                }
            }

            return <As
                cursor="pointer"
                key={i}
                color={fileOp.color}
                alignItems="center"
                onClick={() => {
                    if (fileOp.currentDirectoryMode === true) {
                        fileOp.onClick([props.directory!], props.callback);
                    } else {
                        fileOp.onClick(files, props.callback);
                    }
                }}
                {...props}
            >
                {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon}/> : null}
                <span>{fileOp.text}</span>
            </As>;
        })}
    </>
};

function getSortingColumnAt(columnIndex: 0 | 1): SortBy {
    const sortingColumn = window.localStorage.getItem(`filesSorting${columnIndex}`);
    if (sortingColumn && Object.values(SortBy).includes(sortingColumn)) return sortingColumn as SortBy;
    switch (columnIndex) {
        case 0:
            window.localStorage.setItem("filesSorting0", SortBy.MODIFIED_AT);
            return SortBy.MODIFIED_AT;
        case 1:
            window.localStorage.setItem("filesSorting1", SortBy.SIZE);
            return SortBy.SIZE;
    }
}

function getSortingColumns(): [SortBy, SortBy] {
    return [getSortingColumnAt(0), getSortingColumnAt(1)];
}

function setSortingColumnAt(column: SortBy, columnIndex: 0 | 1) {
    window.localStorage.setItem(`filesSorting${columnIndex}`, column);
}

export const NewFilesTableDemo: React.FunctionComponent = props => {
    const [path, setPath] = useState(Cloud.homeFolder);
    return <NewFilesTable
        embedded={false}
        path={path}
        onFileNavigation={path => {
            console.log("Navigating to ", path);
            setPath(path);
        }}
    />;
};
