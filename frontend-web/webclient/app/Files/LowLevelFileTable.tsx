import * as React from "react";
import {useEffect, useState} from "react";
import {File, FileResource, SortBy, SortOrder} from "Files/index";
import * as UF from "UtilityFunctions"
import {APICallParameters, AsyncWorker, callAPI, useAsyncWork} from "Authentication/DataHook";
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
import {emptyPage, KeyCode, ReduxObject, ResponsiveReduxObject, SensitivityLevelMap} from "DefaultObjects";
import {Cloud} from "Authentication/SDUCloudObject";
import {MainContainer} from "MainContainer/MainContainer";
import Checkbox from "ui-components/Checkbox";
import {Button, Icon, Input, Label, OutlineButton, Tooltip, Truncate} from "ui-components";
import {
    createFolder, favoriteFile,
    getFilenameFromPath,
    getParentPath,
    isAnyMockFile,
    isDirectory,
    isInvalidPathName,
    mergeFilePages, MOCK_RELATIVE,
    MOCK_RENAME_TAG, MOCK_VIRTUAL,
    mockFile,
    moveFile,
    replaceHomeFolder,
    resolvePath
} from "Utilities/FileUtilities";
import BaseLink from "ui-components/BaseLink";
import Theme from "ui-components/theme";
import {defaultFileOperations, FileOperation, FileOperationCallback} from "Files/FileOperations";
import {SpaceProps} from "styled-system";
import * as Heading from "ui-components/Heading";
import * as Pagination from "Pagination";
import {Spacer} from "ui-components/Spacer";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {connect} from "react-redux";
import {Refresh} from "Navigation/Header";
import {RouteComponentProps, withRouter} from "react-router";
import {Dispatch} from "redux";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";

export interface LowLevelFileTableProps {
    page?: Page<File>
    path?: string
    onFileNavigation: (path: string) => void
    embedded?: boolean

    fileOperations?: FileOperation[]
    onReloadRequested?: () => void

    injectedFiles?: File[]
    fileFilter?: (file: File) => boolean

    onLoadingState?: (loading: boolean) => void
    refreshHook?: (shouldRegister: boolean, fn?: () => void) => void

    onPageChanged?: (page: number, itemsPerPage: number) => void
    requestFileSelector?: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>

    numberOfColumns?: number

    asyncWorker?: AsyncWorker
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

const twoPhaseLoadFiles = async (
    attributes: FileResource[],
    callback: (page: Page<File>) => void,
    request: ListDirectoryRequest
) => {
    let promise = callAPI<Page<File>>(listDirectory({
        ...request,
        attrs: [FileResource.FILE_ID, FileResource.PATH, FileResource.FILE_TYPE]
    })).then(result => {
        callback(result);
        return result;
    });

    try {
        const [phaseOne, phaseTwo] = await Promise.all([
            promise,
            callAPI<Page<File>>(listDirectory({...request, attrs: attributes}))
        ]);

        callback(mergeFilePages(phaseOne, phaseTwo, attributes));
    } catch (e) {
        callback(emptyPage); // Set empty page to avoid rendering of this folder
        throw e; // Rethrow to set error status
    }
};

interface InternalFileTableAPI {
    page: Page<File>;
    error: string | undefined;
    pageLoading: boolean;
    setSorting: ((sortBy: SortBy, order: SortOrder, column?: number) => void);
    sortingIconFor: ((other: SortBy) => "arrowUp" | "arrowDown" | undefined);
    reload: () => void;
    sortBy: SortBy;
    order: SortOrder;
    onPageChanged: (pageNumber: number, itemsPerPage: number) => void;
}

const initialPageParameters: ListDirectoryRequest = {
    itemsPerPage: 25,
    order: SortOrder.ASCENDING,
    sortBy: SortBy.PATH,
    page: 0,
    attrs: [],
    path: "TO_BE_REPLACED"
};

function apiForComponent(props, sortByColumns, setSortByColumns): InternalFileTableAPI {
    let api: InternalFileTableAPI;

    const [managedPage, setManagedPage] = useState<Page<File>>(emptyPage);
    const [pageLoading, pageError, submitPageLoaderJob] = props.asyncWorker ? props.asyncWorker : useAsyncWork();
    const [pageParameters, setPageParameters] = useState<ListDirectoryRequest>({
        ...initialPageParameters,
        path: Cloud.homeFolder
    });

    const loadManaged = (request: ListDirectoryRequest) => {
        setPageParameters(request);
        submitPageLoaderJob(async () => {
            await twoPhaseLoadFiles(
                [
                    FileResource.ACL, FileResource.FILE_ID, FileResource.OWNER_NAME, FileResource.FAVORITED,
                    FileResource.SENSITIVITY_LEVEL
                ],
                it => setManagedPage(it),
                request);
        });
    };

    useEffect(() => {
        if (props.page === undefined) {
            const request = {...pageParameters, path: props.path!};
            if (props.path !== pageParameters.path) request.page = 0;
            loadManaged(request);
        }
    }, [props.path, props.page]);

    if (props.page !== undefined) {
        api = {
            page: props.page!,
            pageLoading: false,
            error: undefined,
            setSorting: () => 0,
            sortingIconFor: () => undefined,
            sortBy: SortBy.PATH,
            order: SortOrder.ASCENDING,
            reload: () => {
                if (props.onReloadRequested) props.onReloadRequested();
            },
            onPageChanged: (pageNumber: number, itemsPerPage: number) => {
                if (props.onPageChanged) props.onPageChanged(pageNumber, itemsPerPage);
            }
        }
    } else {
        // TODO Some of these callbacks should use "useCallback"?
        const page = managedPage;
        const error = pageError;
        const loading = pageLoading;

        const setSorting = (sortBy: SortBy, order: SortOrder, column?: number) => {
            if (column !== undefined) {
                setSortingColumnAt(sortBy, column as 0 | 1);

                const newColumns: [SortBy, SortBy] = [sortByColumns[0], sortByColumns[1]];
                newColumns[column] = sortBy;
                setSortByColumns(newColumns);
            }

            loadManaged({
                ...pageParameters,
                sortBy,
                order,
                page: 0
            });
        };

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

        const reload = () => loadManaged(pageParameters);
        const sortBy = pageParameters.sortBy;
        const order = pageParameters.order;

        const onPageChanged = (pageNumber: number, itemsPerPage: number) => {
            loadManaged({...pageParameters, page: pageNumber, itemsPerPage: itemsPerPage});
        };

        api = {page, error, pageLoading: loading, setSorting, sortingIconFor, reload, sortBy, order, onPageChanged};
    }
    return api;
}

const LowLevelFileTable_: React.FunctionComponent<LowLevelFileTableProps &
    RouteComponentProps &
    { responsive: ResponsiveReduxObject } &
    { showUploader: (path: string) => void }> = props => {
    // Validation
    if (props.page === undefined && props.path === undefined) {
        throw Error("FilesTable must set either path or page property");
    }

    if (props.page !== undefined && props.path === undefined && props.embedded !== true) {
        throw Error("page is not currently supported in non-embedded mode without a path");
    }

    // Hooks
    const [checkedFiles, setCheckedFiles] = useState<Set<string>>(new Set());
    const [fileBeingRenamed, setFileBeingRenamed] = useState<string | null>(null);
    const [sortByColumns, setSortByColumns] = useState<[SortBy, SortBy]>(() => getSortingColumns());
    const [injectedViaState, setInjectedViaState] = useState<File[]>([]);
    const [workLoading, workError, invokeWork] = useAsyncWork();

    let {page, error, pageLoading, setSorting, sortingIconFor, reload, sortBy, order, onPageChanged} =
        apiForComponent(props, sortByColumns, setSortByColumns);

    // Callbacks for operations
    const callbacks: FileOperationCallback = {
        invokeAsyncWork: fn => invokeWork(fn),
        requestReload: () => {
            setFileBeingRenamed(null);
            setCheckedFiles(new Set());
            setInjectedViaState([]);
            reload();
        },
        requestFileUpload: () => {
            const path = props.path ? props.path : Cloud.homeFolder;
            props.showUploader(path);
        },
        requestFolderCreation: () => {
            if (props.path === undefined) return;
            let fileId = "newFolderId";
            setInjectedViaState([
                    mockFile({
                        path: `${props.path}/newFolder`,
                        fileId,
                        tag: MOCK_RENAME_TAG,
                        type: "DIRECTORY"
                    })
                ]
            );
            setFileBeingRenamed(fileId);
        },
        startRenaming: file => setFileBeingRenamed(file.fileId!),
        requestFileSelector: async (allowFolders: boolean, canOnlySelectFolders: boolean) => {
            if (props.requestFileSelector) return await props.requestFileSelector(allowFolders, canOnlySelectFolders);
            return null;
        },
        history: props.history
    };

    // Register refresh hook
    if (props.refreshHook !== undefined) {
        props.refreshHook(true, () => callbacks.requestReload());
    }

    useEffect(() => {
        return () => {
            if (props.refreshHook) props.refreshHook(false);
        }
    }, [props.refreshHook]);

    // Aliases
    const isEmbedded = props.embedded !== false;
    const sortingSupported = props.path !== undefined;
    const numberOfColumnsBasedOnSpace =
        (props.responsive.greaterThan.lg ? 2 :
            (props.responsive.greaterThan.md ? 1 : 0));
    const numberOfColumns = props.numberOfColumns !== undefined ? props.numberOfColumns : numberOfColumnsBasedOnSpace;
    const fileOperations = props.fileOperations !== undefined ? props.fileOperations : defaultFileOperations;
    const fileFilter = props.fileFilter ? props.fileFilter : () => true;
    const allFiles = injectedViaState.concat(props.injectedFiles ? props.injectedFiles : []).concat(page.items)
        .filter(fileFilter);
    const isMasterChecked = allFiles.length > 0 && allFiles.every(f => checkedFiles.has(f.fileId!) || f.mockTag !== undefined);
    const isAnyLoading = workLoading || pageLoading;
    const checkedFilesWithInfo = allFiles.filter(f => f.fileId && checkedFiles.has(f.fileId) && f.mockTag === undefined);

    // Loading state
    if (props.onLoadingState) props.onLoadingState(isAnyLoading);

    // Private utility functions
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
            if (file.mockTag === MOCK_RENAME_TAG) {
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
                        currentPath={props.path ? props.path : ""}
                        navigate={path => props.onFileNavigation(path)}
                        homeFolder={Cloud.homeFolder}/>
                }

                right={
                    <>
                        {!isEmbedded && props.path ? null :
                            <Refresh
                                spin={isAnyLoading}
                                onClick={() => callbacks.requestReload()}
                            />
                        }

                        {isEmbedded ? null :
                            <Pagination.EntriesPerPageSelector
                                content="Files per page"
                                entriesPerPage={page.itemsPerPage}
                                onChange={amount => onPageChanged(0, amount)}
                            />
                        }
                    </>
                }
            />
        }

        sidebar={
            <Box pl="5px" pr="5px">
                <VerticalButtonGroup>
                    <SidebarContent>
                        <FileOperations
                            files={checkedFilesWithInfo}
                            fileOperations={fileOperations}
                            callback={callbacks}
                            // Don't pass a directory if the page is set. This should indicate that the path is fake.
                            directory={props.page !== undefined ? undefined : mockFile({
                                path: props.path ? props.path : "",
                                fileId: "currentDir",
                                tag: MOCK_RELATIVE,
                                type: "DIRECTORY"
                            })}
                        />
                    </SidebarContent>
                </VerticalButtonGroup>
            </Box>
        }

        main={
            <Pagination.List
                loading={pageLoading}
                customEmptyPage={!error ? <Heading.h3>No files in current folder</Heading.h3> : <Box>{error}</Box>}
                page={{...page, items: allFiles}}
                onPageChanged={(newPage, currentPage) => onPageChanged(newPage, currentPage.itemsPerPage)}
                pageRenderer={() =>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <FileTableHeaderCell notSticky={isEmbedded} textAlign="left" width="99%">
                                    <Flex
                                        backgroundColor="white"
                                        alignItems="center"
                                        onClick={() => setSorting(SortBy.PATH, invertSortOrder(order))}
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

                                {/* Sorting columns (in header) */}
                                {sortByColumns.filter(it => it != null).map((column, i) => {
                                    if (i >= numberOfColumns) return null;

                                    const isSortedBy = sortBy === column;

                                    return <FileTableHeaderCell notSticky={isEmbedded} width="10rem">
                                        <Flex backgroundColor="white" alignItems="center" cursor="pointer"
                                              justifyContent="left">
                                            <Box
                                                onClick={() => setSorting(column, invertSortOrder(order), i)}>
                                                <Arrow name={sortingIconFor(column)}/>
                                            </Box>
                                            {!sortingSupported ?
                                                <>{UF.sortByToPrettierString(column)}</>
                                                :
                                                <ClickableDropdown
                                                    trigger={<TextSpan>{UF.sortByToPrettierString(column)}</TextSpan>}
                                                    chevron>
                                                    <Box ml="-16px" mr="-16px" pl="15px"
                                                         hidden={order === SortOrder.ASCENDING && isSortedBy}
                                                         onClick={() => setSorting(column, SortOrder.ASCENDING, i)}
                                                    >
                                                        {UF.prettierString(SortOrder.ASCENDING)}
                                                    </Box>
                                                    <Box ml="-16px" mr="-16px" pl="15px"
                                                         onClick={() => setSorting(column, SortOrder.DESCENDING, i)}
                                                         hidden={order === SortOrder.DESCENDING && isSortedBy}
                                                    >
                                                        {UF.prettierString(SortOrder.DESCENDING)}
                                                    </Box>
                                                    <Divider ml="-16px" mr="-16px"/>
                                                    {Object.values(SortBy).map((sortByKey: SortBy, j) => (
                                                        <Box ml="-16px" mr="-16px" pl="15px" key={j}
                                                             onClick={() => setSorting(sortByKey, order, i)}
                                                             hidden={sortByKey === sortBy || sortByKey === SortBy.PATH}
                                                        >
                                                            {UF.sortByToPrettierString(sortByKey)}
                                                        </Box>
                                                    ))}
                                                </ClickableDropdown>
                                            }
                                        </Flex>
                                    </FileTableHeaderCell>
                                })}

                                {/* Options cell (adds a bit of spacing and hosts options in rows) */}
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
                                        {/* This cell contains: [Checkbox|Icon|Name|Favorite] */}
                                        <Flex flexDirection="row" alignItems="center" mx="9px">
                                            {isEmbedded ? null :
                                                <Box>
                                                    <Label>
                                                        <Checkbox
                                                            disabled={file.mockTag !== undefined}
                                                            checked={checkedFiles.has(file.fileId!) && file.mockTag === undefined}
                                                            onChange={e => e.stopPropagation()}
                                                            onClick={() => setChecked([file])}/>
                                                    </Label>
                                                </Box>
                                            }
                                            <Box ml="5px" pr="5px"/>
                                            <NameBox file={file} onRenameFile={onRenameFile}
                                                     onNavigate={props.onFileNavigation}
                                                     callbacks={callbacks}
                                                     fileBeingRenamed={fileBeingRenamed}/>
                                        </Flex>
                                    </TableCell>

                                    <TableCell>
                                        {/* Sensitivity icon */}
                                        <SensitivityIcon sensitivity={file.sensitivityLevel}/>
                                    </TableCell>

                                    {sortByColumns.filter(it => it != null).map((sC, i) => {
                                        if (i >= numberOfColumns) return null;
                                        // Sorting columns
                                        return <TableCell key={i}>
                                            {sC ? UF.sortingColumnToValue(sC, file) : null}
                                        </TableCell>
                                    })}

                                    <TableCell textAlign="center">
                                        {/* Options cell */}
                                        {
                                            checkedFiles.size > 0 || (file.mockTag !== undefined && file.mockTag !== MOCK_RELATIVE) ? null :
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

const mapStateToProps = ({responsive}: ReduxObject) => {
    return {responsive};
};
const mapDispatchToProps = (dispatch: Dispatch) => ({
    showUploader: (path: string) => dispatch(setUploaderVisible(true, path))
});
export const LowLevelFileTable = connect(mapStateToProps, mapDispatchToProps)(withRouter(LowLevelFileTable_));

interface ShellProps {
    embedded: boolean
    header: React.ReactChild
    sidebar: React.ReactChild
    main: React.ReactChild
}

const Shell: React.FunctionComponent<ShellProps> = props => {
    if (props.embedded) {
        return <>
            {props.header}
            {props.main}
        </>;
    }

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
    fileBeingRenamed: string | null
    callbacks: FileOperationCallback
}

const NameBox: React.FunctionComponent<NameBoxProps> = props => {
    const [favorite, setFavorite] = useState<boolean>(props.file.favorited ? props.file.favorited : false);
    useEffect(() => {
        setFavorite(props.file.favorited ? props.file.favorited : false);
    }, [props.file]);
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
        let nameComponent = <Flex alignItems="center">
            {icon}

            <Truncate
                cursor={canNavigate ? "pointer" : undefined}
                mr="5px"
            >
                {getFilenameFromPath(props.file.path)}
            </Truncate>
        </Flex>;

        return <>
            <Flex data-tag={"fileName"} flex="0 1 auto" minWidth="0"> {/* Prevent name overflow */}
                <Box title={replaceHomeFolder(props.file.path, Cloud.homeFolder)} width="100%">
                    {props.file.fileType !== "DIRECTORY" ? nameComponent :
                        <BaseLink href={"#"} onClick={e => {
                            e.preventDefault();
                            props.onNavigate(resolvePath(props.file.path));
                        }}>
                            {nameComponent}
                        </BaseLink>
                    }
                </Box>
            </Flex>

            {isAnyMockFile([props.file]) ? null :
                <Icon
                    data-tag="fileFavorite"
                    size="1em" ml=".7em"
                    color={favorite ? "blue" : "gray"}
                    name={favorite ? "starFilled" : "starEmpty"}
                    onClick={() => {
                        props.callbacks.invokeAsyncWork(async () => {
                            const initialValue = favorite;
                            setFavorite(!initialValue);
                            try {
                                await favoriteFile(props.file, Cloud);
                            } catch (e) {
                                setFavorite(initialValue);
                            }
                        })
                    }}
                    hoverColor="blue"
                />
            }
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

    const buttons: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode === true);
    const options: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode !== true);

    const Operation = ({fileOp}: { fileOp: FileOperation }) => {
        if (fileOp.currentDirectoryMode === true && props.directory === undefined) return null;
        if (fileOp.currentDirectoryMode !== true && files.length === 0) return null;
        const filesInCallback = fileOp.currentDirectoryMode === true ? [props.directory!] : files;
        if (fileOp.disabled(filesInCallback)) return null;

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
            color={fileOp.color}
            alignItems="center"
            onClick={() => fileOp.onClick(filesInCallback, props.callback)}
            {...props}
        >
            {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon}/> : null}
            <span>{fileOp.text}</span>
        </As>;
    };
    return <>
        {buttons.map((op, i) => <Operation fileOp={op} key={`button-${i}`}/>)}
        {files.length === 0 || fileOperations.length === 1 ? null :
            <Box><TextSpan bold>{files.length} {files.length === 1 ? "file" : "files"} selected</TextSpan></Box>
        }
        {options.map((op, i) => <Operation fileOp={op} key={`opt-${i}`}/>)}
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

