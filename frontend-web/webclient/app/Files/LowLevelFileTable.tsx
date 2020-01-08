import {AppToolLogo} from "Applications/AppToolLogo";
import {APICallParameters, AsyncWorker, callAPI, useAsyncWork} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {emptyPage, KeyCode, ReduxObject, ResponsiveReduxObject, SensitivityLevelMap} from "DefaultObjects";
import {File, FileResource, FileType, SortBy, SortOrder} from "Files";
import {defaultFileOperations, FileOperation, FileOperationCallback} from "Files/FileOperations";
import {QuickLaunchApp, quickLaunchCallback} from "Files/QuickLaunch";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {Refresh} from "Navigation/Header";
import * as Pagination from "Pagination";
import PromiseKeeper, {usePromiseKeeper} from "PromiseKeeper";
import {useEffect, useState} from "react";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled, {StyledComponent} from "styled-components";
import {SpaceProps} from "styled-system";
import {Page} from "Types";
import {
    Button,
    Checkbox,
    Divider,
    Hide,
    Icon,
    Input,
    Label,
    Link,
    List,
    OutlineButton,
    Text,
    Tooltip,
    Truncate
} from "ui-components";
import BaseLink from "ui-components/BaseLink";
import Box from "ui-components/Box";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import Theme from "ui-components/theme";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {Upload} from "Uploader";
import {appendUpload, setUploaderCallback, setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {
    createFolder,
    favoriteFile,
    filePreviewQuery,
    getFilenameFromPath,
    getParentPath,
    isAnyMockFile, isAnySharedFs,
    isDirectory,
    isFilePreviewSupported,
    isInvalidPathName,
    mergeFilePages,
    MOCK_RELATIVE,
    MOCK_RENAME_TAG,
    mockFile,
    moveFile,
    resolvePath,
    sizeToString
} from "Utilities/FileUtilities";
import {buildQueryString} from "Utilities/URIUtilities";
import {addStandardDialog, Arrow, FileIcon} from "UtilityComponents";
import * as UF from "UtilityFunctions";

export interface LowLevelFileTableProps {
    page?: Page<File>;
    path?: string;
    onFileNavigation: (path: string) => void;
    embedded?: boolean;

    fileOperations?: FileOperation[];
    onReloadRequested?: () => void;

    injectedFiles?: File[];
    fileFilter?: (file: File) => boolean;

    onLoadingState?: (loading: boolean) => void;
    refreshHook?: (shouldRegister: boolean, fn?: () => void) => void;

    onPageChanged?: (page: number, itemsPerPage: number) => void;
    requestFileSelector?: (allowFolders: boolean, canOnlySelectFolders: boolean) => Promise<string | null>;

    foldersOnly?: boolean;

    omitQuickLaunch?: boolean;
    previewEnabled?: boolean;

    asyncWorker?: AsyncWorker;
}

export interface ListDirectoryRequest {
    path: string;
    page: number;
    itemsPerPage: number;
    order: SortOrder;
    sortBy: SortBy;
    attrs: FileResource[];
    type?: FileType;
}

export const listDirectory = ({
    path,
    page,
    itemsPerPage,
    order,
    sortBy,
    attrs,
    type
}: ListDirectoryRequest): APICallParameters<ListDirectoryRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/files",
        {
            path,
            page,
            itemsPerPage,
            order,
            sortBy,
            attrs: attrs.join(","),
            type
        }
    ),
    parameters: {path, page, itemsPerPage, order, sortBy, attrs},
    reloadId: Math.random()
});

const twoPhaseLoadFiles = async (
    attributes: FileResource[],
    callback: (page: Page<File>) => void,
    request: ListDirectoryRequest,
    promises: PromiseKeeper
) => {
    const promise = callAPI<Page<File>>(listDirectory({
        ...request,
        attrs: [FileResource.FILE_ID, FileResource.PATH, FileResource.FILE_TYPE]
    })).then(result => {
        if (!promises.canceledKeeper) callback(result);
        return result;
    });
    try {
        const [phaseOne, phaseTwo] = await Promise.all([
            promise,
            callAPI<Page<File>>(listDirectory({...request, attrs: attributes}))
        ]);
        if (promises.canceledKeeper) return;
        callback(mergeFilePages(phaseOne, phaseTwo, attributes));
    } catch (e) {
        if (promises.canceledKeeper) return;
        callback(emptyPage); // Set empty page to avoid rendering of this folder
        throw e; // Rethrow to set error status
    }
};

interface InternalFileTableAPI {
    page: Page<File>;
    error: string | undefined;
    pageLoading: boolean;
    setSorting: ((sortBy: SortBy, order: SortOrder, column?: number) => void);
    sortingIcon: (other: SortBy) => React.ReactNode;
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

function apiForComponent(
    props, /* FIXME: ADD TYPESAFETY */
    sortByColumns: [SortBy, SortBy],
    setSortByColumns: (s: [SortBy, SortBy]) => void
): InternalFileTableAPI {
    let api: InternalFileTableAPI;
    const promises = usePromiseKeeper();
    const [managedPage, setManagedPage] = useState<Page<File>>(emptyPage);
    const [pageLoading, pageError, submitPageLoaderJob] = props.asyncWorker ? props.asyncWorker : useAsyncWork();
    const [pageParameters, setPageParameters] = useState<ListDirectoryRequest>({
        ...initialPageParameters,
        type: props.foldersOnly ? "DIRECTORY" : undefined,
        path: Client.homeFolder
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
                request,
                promises);
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
            pageLoading,
            error: undefined,
            setSorting: () => 0,
            sortingIcon: () => undefined,
            sortBy: SortBy.PATH,
            order: SortOrder.ASCENDING,
            reload: () => {
                if (props.onReloadRequested) props.onReloadRequested();
            },
            onPageChanged: (pageNumber: number, itemsPerPage: number) => {
                if (props.onPageChanged) props.onPageChanged(pageNumber, itemsPerPage);
            }
        };
    } else {
        // TODO Some of these callbacks should use "useCallback"?
        const page = managedPage;
        const error = pageError;
        const loading = pageLoading;

        const setSorting = (sortBy: SortBy, order: SortOrder, column?: 0 | 1) => {
            let sortByToUse = sortBy;
            if (sortBy === SortBy.ACL) sortByToUse = pageParameters.sortBy;

            if (column !== undefined) {
                setSortingColumnAt(sortBy, column);

                const newColumns: [SortBy, SortBy] = [sortByColumns[0], sortByColumns[1]];
                newColumns[column] = sortBy;
                setSortByColumns(newColumns);
            }

            loadManaged({
                ...pageParameters,
                sortBy: sortByToUse,
                order,
                page: 0
            });
        };

        const sortingIcon = (other: SortBy): React.ReactNode =>
            <Arrow sortBy={pageParameters.sortBy} activeSortBy={other} order={pageParameters.order} />;

        const reload = () => loadManaged(pageParameters);
        const sortBy = pageParameters.sortBy;
        const order = pageParameters.order;

        const onPageChanged = (pageNumber: number, itemsPerPage: number) => {
            loadManaged({...pageParameters, page: pageNumber, itemsPerPage});
        };

        api = {page, error, pageLoading: loading, setSorting, sortingIcon, reload, sortBy, order, onPageChanged};
    }
    return api;
}

// tslint:disable-next-line: variable-name
const LowLevelFileTable_: React.FunctionComponent<LowLevelFileTableProps & {
    responsive: ResponsiveReduxObject;
    showUploader: (path: string) => void;
    setUploaderCallback: (cb?: () => void) => void;
    appendUpload: (uploads: Upload) => void;
    activeUploadCount: number;
}> = props => {
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
    const [workLoading, , invokeWork] = useAsyncWork();
    const [applications, setApplications] = useState(new Map<string, QuickLaunchApp[]>());
    const history = useHistory();

    const {page, error, pageLoading, setSorting, reload, sortBy, order, onPageChanged} =
        apiForComponent(props, sortByColumns, setSortByColumns);

    // Fetch quick launch applications upon page refresh
    useEffect(() => {
        const filesOnly = page.items.filter(f => f.fileType === "FILE");
        if (filesOnly.length > 0) {
            Client.post<QuickLaunchApp[]>(
                "/hpc/apps/bySupportedFileExtension",
                {files: filesOnly.map(f => f.path)}
            ).then(({response}) => {
                const newApplications = new Map<string, QuickLaunchApp[]>();
                filesOnly.forEach(f => {
                    const fileApps: QuickLaunchApp[] = [];

                    const [fileName] = f.path.split("/").slice(-1);
                    let [fileExtension] = fileName.split(".").slice(-1);

                    if (fileName !== fileExtension) {
                        fileExtension = `.${fileExtension}`;
                    }

                    response.forEach(item => {
                        item.extensions.forEach(ext => {
                            if (fileExtension === ext) {
                                fileApps.push(item);
                            }
                        });
                    });

                    newApplications.set(f.path, fileApps);
                });
                setApplications(newApplications);
            }).catch(e =>
                snackbarStore.addFailure(UF.errorMessageOrDefault(e, "An error occurred fetching Quicklaunch Apps")
                ));
        }
    }, [page]);

    useEffect(() => {
        if (!props.embedded) {
            const {pageNumber, itemsPerPage} = page;
            props.setUploaderCallback(() => onPageChanged(pageNumber, itemsPerPage));
        }
    });

    useEffect(() => {
        return () => props.setUploaderCallback();
    }, []);

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
            const path = props.path ? props.path : Client.homeFolder;
            props.showUploader(path);
        },
        requestFolderCreation: () => {
            if (props.path === undefined) return;
            const fileId = "newFolderId";
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

            if (!isEmbedded) window.scrollTo({top: 0});
        },
        startRenaming: file => setFileBeingRenamed(file.fileId!),
        requestFileSelector: async (allowFolders: boolean, canOnlySelectFolders: boolean) => {
            if (props.requestFileSelector) return await props.requestFileSelector(allowFolders, canOnlySelectFolders);
            return null;
        },
        createNewUpload: upload => {
            props.appendUpload(upload);

            const path = props.path ? props.path : Client.homeFolder;
            props.showUploader(path);
        },
        history
    };

    // Register refresh hook
    if (props.refreshHook !== undefined) {
        props.refreshHook(true, () => callbacks.requestReload());
    }

    useEffect(() => {
        return () => {
            if (props.refreshHook) props.refreshHook(false);
        };
    }, [props.refreshHook]);


    // Aliases
    const isForbiddenPath = ["Forbidden", "Not Found"].includes(error ?? "");
    const isEmbedded = props.embedded !== false;
    const sortingSupported = !props.embedded;
    const fileOperations = props.fileOperations !== undefined ? props.fileOperations : defaultFileOperations;
    const fileFilter = props.fileFilter ? props.fileFilter : () => true;
    const allFiles = injectedViaState.concat(props.injectedFiles ? props.injectedFiles : []).concat(page.items)
        .filter(fileFilter);
    const isMasterChecked = allFiles.filter(f => !f.mockTag).length > 0 &&
        allFiles.every(f => checkedFiles.has(f.fileId!) || f.mockTag !== undefined);
    const isMasterDisabled = allFiles.every(f => f.mockTag !== undefined);
    const isAnyLoading = workLoading || pageLoading;
    const checkedFilesWithInfo = allFiles
        .filter(f => f.fileId && checkedFiles.has(f.fileId) && f.mockTag === undefined);
    const onFileNavigation = (path: string) => {
        setCheckedFiles(new Set());
        setFileBeingRenamed(null);
        setInjectedViaState([]);
        if (!isEmbedded) window.scrollTo({top: 0});
        props.onFileNavigation(path);
    };

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

    const onRenameFile = (key: number, name: string) => {
        if (key === KeyCode.ESC) {
            setInjectedViaState([]);
            setFileBeingRenamed(null);
        } else if (key === KeyCode.ENTER) {
            const file = allFiles.find(f => f.fileId === fileBeingRenamed);
            if (file === undefined) return;
            const fileNames = allFiles.map(f => getFilenameFromPath(f.path));
            if (isInvalidPathName({path: name, filePaths: fileNames})) return;

            const fullPath = `${UF.addTrailingSlash(getParentPath(file.path))}${name}`;
            if (file.mockTag === MOCK_RENAME_TAG) {
                createFolder({
                    path: fullPath,
                    client: Client,
                    onSuccess: () => callbacks.requestReload()
                });
            } else {
                moveFile({
                    oldPath: file.path,
                    newPath: fullPath,
                    client: Client,
                    setLoading: () => 42, // TODO
                    onSuccess: () => callbacks.requestReload()
                });
            }
        }
    };

    return (
        <Shell
            embedded={isEmbedded}

            header={(
                <Spacer
                    left={(
                        <BreadCrumbs
                            currentPath={props.path ?? ""}
                            navigate={onFileNavigation}
                            homeFolder={Client.homeFolder}
                        />
                    )}

                    right={(
                        <>
                            {!isEmbedded && props.path ? null : (
                                <Refresh
                                    spin={isAnyLoading}
                                    onClick={callbacks.requestReload}
                                />
                            )}

                            {isEmbedded ? null : (
                                <Pagination.EntriesPerPageSelector
                                    content="Files per page"
                                    entriesPerPage={page.itemsPerPage}
                                    onChange={amount => onPageChanged(0, amount)}
                                />
                            )}
                        </>
                    )}
                />
            )}

            sidebar={(
                <Box pl="5px" pr="5px" height="calc(100% - 20px)">
                    {isForbiddenPath ? <></> : (
                        <VerticalButtonGroup>
                            <FileOperations
                                files={checkedFilesWithInfo}
                                fileOperations={fileOperations}
                                callback={callbacks}
                                // Don't pass a directory if the page is set.
                                // This should indicate that the path is fake.
                                directory={props.page !== undefined ? undefined : mockFile({
                                    path: props.path ? props.path : "",
                                    fileId: "currentDir",
                                    tag: MOCK_RELATIVE,
                                    type: "DIRECTORY"
                                })}
                            />

                            <Box flexGrow={1} />

                            {/* Note: Current hack to hide sidebar/header requires a full re-load. */}

                            <OutlineButton
                                onClick={() => props.activeUploadCount ? addStandardDialog({
                                    title: "Continue",
                                    message: (
                                        <Box>
                                            <Text>You have tasks that will be cancelled if you continue.</Text>
                                            {props.activeUploadCount ? (
                                                <Text>
                                                    {props.activeUploadCount} uploads in progress.
                                                </Text>
                                            ) : ""}
                                            {/* TODO: TASKS */}
                                        </Box>
                                    ),
                                    onConfirm: () => toWebDav(),
                                    confirmText: "OK"
                                }) : toWebDav()}
                            >
                                Use your files locally (BETA)
                            </OutlineButton>
                        </VerticalButtonGroup>
                    )}
                </Box>
            )}

            main={(
                <>
                    {!sortingSupported ? <div /> : (
                        <StickyBox>
                            <Spacer
                                left={isMasterDisabled ? null : (
                                    <Box mr="18px">
                                        <Label ml={10}>
                                            <Checkbox
                                                size={27}
                                                data-tag="masterCheckbox"
                                                onClick={() => setChecked(
                                                    allFiles.filter(it => !isAnyMockFile([it])), !isMasterChecked
                                                )}
                                                checked={isMasterChecked}
                                                disabled={isMasterDisabled}
                                                onChange={UF.stopPropagation}
                                            />
                                            <Box as={"span"} ml={"4px"}>Select all</Box>
                                        </Label>
                                    </Box>
                                )}
                                right={(
                                    <>
                                        <ClickableDropdown
                                            trigger={(
                                                <>
                                                    <Icon
                                                        cursor="pointer"
                                                        name="arrowDown"
                                                        rotation={order === SortOrder.ASCENDING ? 180 : 0}
                                                        size=".7em"
                                                        mr=".4em"
                                                    />
                                                    Sort by: {UF.sortByToPrettierString(sortBy)}
                                                </>
                                            )}
                                            chevron
                                        >
                                            <Box
                                                ml="-16px"
                                                mr="-16px"
                                                pl="15px"
                                                onClick={() => setSorting(
                                                    sortByColumns[0], order === SortOrder.ASCENDING ?
                                                    SortOrder.DESCENDING : SortOrder.ASCENDING, 0
                                                )}
                                            >
                                                <>
                                                    {UF.prettierString(order === SortOrder.ASCENDING ?
                                                        SortOrder.DESCENDING : SortOrder.ASCENDING
                                                    )}
                                                </>
                                            </Box>
                                            <Divider />
                                            {Object.values(SortBy).filter(it => it !== sortByColumns[0]).map((sortByValue: SortBy, j) => (
                                                <Box
                                                    ml="-16px"
                                                    mr="-16px"
                                                    pl="15px"
                                                    key={j}
                                                    onClick={() => setSorting(sortByValue, SortOrder.ASCENDING, 0)}
                                                >
                                                    {UF.sortByToPrettierString(sortByValue)}
                                                </Box>
                                            ))}
                                        </ClickableDropdown>
                                    </>
                                )}
                            />
                        </StickyBox>
                    )}
                    <Pagination.List
                        loading={pageLoading}
                        customEmptyPage={!error ? <Heading.h3>No files in current folder</Heading.h3> : pageLoading ?
                            null : <div>{error}</div>}
                        page={{...page, items: allFiles}}
                        onPageChanged={(newPage, currentPage) => onPageChanged(newPage, currentPage.itemsPerPage)}
                        pageRenderer={pageRenderer}
                    />
                </>
            )}
        />
    );

    function pageRenderer({items}: Page<File>) {
        return (
            <List>
                {items.map(f => (
                    <Flex
                        backgroundColor={checkedFiles.has(f.fileId!) ? "lightBlue" : "white"}
                        onClick={() => {
                            if (!isAnyMockFile([f]) && !isEmbedded) setChecked([f]);
                        }}
                        width="100%"
                        key={f.path}
                    >
                        <Spacer
                            width="100%"
                            left={(
                                <NameBox
                                    file={f}
                                    onRenameFile={onRenameFile}
                                    onNavigate={onFileNavigation}
                                    callbacks={callbacks}
                                    fileBeingRenamed={fileBeingRenamed}
                                    previewEnabled={props.previewEnabled}
                                />
                            )}
                            right={(f.mockTag !== undefined && f.mockTag !== MOCK_RELATIVE) ? null : (
                                <Flex mt="5px" onClick={UF.stopPropagation}>
                                    {/* Show members as icons */}
                                    {/* {!f.acl ? null : <ACLAvatars members={f.acl.map(it => it.entity)} />} */}
                                    {!(props.previewEnabled && isFilePreviewSupported(f)) ? null : (
                                        <Tooltip
                                            wrapperOffsetLeft="0"
                                            wrapperOffsetTop="4px"
                                            right="0"
                                            top="1"
                                            mb="50px"
                                            trigger={(
                                                <Link to={filePreviewQuery(f.path)}>
                                                    <Icon
                                                        cursor="pointer"
                                                        size="24px"
                                                        mt="4px"
                                                        mr="8px"
                                                        color="gray"
                                                        name="preview"
                                                    />
                                                </Link>
                                            )}
                                        >
                                            Preview available
                                        </Tooltip>
                                    )}
                                    {props.omitQuickLaunch ? null : f.fileType !== "FILE" ? null :
                                        ((applications.get(f.path) ?? []).length < 1) ? null : (
                                            <ClickableDropdown
                                                width="175px"
                                                left="-160px"
                                                trigger={<Icon mr="8px" mt="2px" name="play" size="1em" />}
                                            >
                                                <QuickLaunchApps
                                                    file={f}
                                                    applications={applications.get(f.path)}
                                                    history={history}
                                                    ml="-17px"
                                                    mr="-17px"
                                                    pl="15px"
                                                />
                                            </ClickableDropdown>
                                        )
                                    }
                                    <SensitivityIcon sensitivity={f.sensitivityLevel} />
                                    {checkedFiles.size !== 0 ? <Box width="38px" /> : fileOperations.length > 1 ? (
                                        <Box mt="2px">
                                            <ClickableDropdown
                                                width="175px"
                                                left="-160px"
                                                trigger={(
                                                    <Icon
                                                        onClick={UF.preventDefault}
                                                        ml="10px"
                                                        mr="10px"
                                                        name="ellipsis"
                                                        size="1em"
                                                        rotation={90}
                                                    />
                                                )}
                                            >
                                                <FileOperations
                                                    files={[f]}
                                                    fileOperations={fileOperations}
                                                    inDropdown
                                                    ml="-17px"
                                                    mr="-17px"
                                                    pl="15px"
                                                    callback={callbacks}
                                                />
                                            </ClickableDropdown>
                                        </Box>
                                    ) : (
                                            <Box mt="-2px" ml="5px">
                                                <FileOperations
                                                    files={[f]}
                                                    fileOperations={fileOperations}
                                                    callback={callbacks}
                                                />
                                            </Box>
                                        )}
                                </Flex>
                            )}
                        />
                    </Flex>
                ))}
            </List>
        );
    }
};

const StickyBox = styled(Box)`
    position: sticky;
    top: 120px;
    z-index: 50;
`;

function toWebDav() {
    const a = document.createElement("a");
    a.href = "/app/login?dav=true";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

const mapStateToProps = ({responsive, uploader}: ReduxObject) => {
    const activeUploadCount = uploader.uploads.filter(it =>
        (it.uploadXHR?.readyState ?? -1 > XMLHttpRequest.UNSENT) &&
        (it.uploadXHR?.readyState ?? -1 < XMLHttpRequest.DONE)).length;
    return {responsive, activeUploadCount};
};

const mapDispatchToProps = (dispatch: Dispatch) => ({
    showUploader: (path: string) => dispatch(setUploaderVisible(true, path)),
    setUploaderCallback: (cb?: () => void) => dispatch(setUploaderCallback(cb)),
    appendUpload: (upload: Upload) => dispatch(appendUpload(upload)),
});

export const LowLevelFileTable = connect(mapStateToProps, mapDispatchToProps)(LowLevelFileTable_);

interface ShellProps {
    embedded: boolean;
    header: React.ReactChild;
    sidebar: React.ReactChild;
    main: React.ReactChild;
}

const Shell: React.FunctionComponent<ShellProps> = props => {
    if (props.embedded) {
        return (
            <>
                {props.header}
                {props.main}
            </>
        );
    }

    return (
        <MainContainer
            header={props.header}
            main={props.main}
            sidebar={props.sidebar}
        />
    );
};

interface NameBoxProps {
    file: File;
    onRenameFile: (keycode: number, value: string) => void;
    onNavigate: (path: string) => void;
    fileBeingRenamed: string | null;
    callbacks: FileOperationCallback;
    previewEnabled?: boolean;
}

const NameBox: React.FunctionComponent<NameBoxProps> = props => {
    const [favorite, setFavorite] = useState<boolean>(props.file.favorited ? props.file.favorited : false);
    useEffect(() => {
        setFavorite(props.file.favorited ? props.file.favorited : false);
    }, [props.file]);
    const canNavigate = isDirectory({fileType: props.file.fileType});

    const icon = (
        <Box mr="10px" mt="4px" mb="4px" cursor="inherit">
            <FileIcon
                fileIcon={UF.iconFromFilePath(props.file.path, props.file.fileType, Client.homeFolder)}
                size={38}
                shared={(props.file.acl != null ? props.file.acl.length : 0) > 0}
            />
        </Box>
    );

    const beingRenamed = props.file.fileId !== null && props.file.fileId === props.fileBeingRenamed;
    const fileName = beingRenamed ? (
        <Input
            placeholder={props.file.mockTag ? "" : getFilenameFromPath(props.file.path)}
            defaultValue={props.file.mockTag ? "" : getFilenameFromPath(props.file.path)}
            pt="0px"
            pb="0px"
            pr="0px"
            pl="0px"
            mb="-4px"
            noBorder
            fontSize={20}
            maxLength={1024}
            borderRadius="0px"
            type="text"
            width="100%"
            autoFocus
            data-tag="renameField"
            onKeyDown={e => props.onRenameFile?.(e.keyCode, (e.target as HTMLInputElement).value)}
        />
    ) : (
            <Truncate
                width={[170, 250, 300, 250, 500, "100%"]}
                mb="-4px"
                fontSize={20}
            >
                {getFilenameFromPath(props.file.path)}
            </Truncate>
        );

    return (
        <Flex>
            <Box mx="10px" mt="9px">
                {isAnyMockFile([props.file]) || isAnySharedFs([props.file]) ? <Box width="24px" /> : (
                    <Icon
                        cursor="pointer"
                        size="24"
                        name={favorite ? "starFilled" : "starEmpty"}
                        color={favorite ? "blue" : "gray"}
                        onClick={e => {
                            e.stopPropagation();
                            props.callbacks.invokeAsyncWork(async () => {
                                const initialValue = favorite;
                                setFavorite(!initialValue);
                                try {
                                    await favoriteFile(props.file, Client);
                                } catch (e) {
                                    setFavorite(initialValue);
                                }
                            });
                        }}
                        hoverColor="blue"
                    />
                )}
            </Box>
            {icon}
            <Box width="100%" mt="2px">
                {canNavigate && !beingRenamed ? (
                    <BaseLink
                        onClick={e => {
                            e.preventDefault();
                            e.stopPropagation();
                            props.onNavigate(resolvePath(props.file.path));
                        }}
                    >
                        {fileName}
                    </BaseLink>
                ) : props.previewEnabled && isFilePreviewSupported(props.file) ?
                        <Link to={filePreviewQuery(props.file.path)}>{fileName}</Link> :
                        fileName
                }

                <Hide sm xs>
                    <Flex>
                        {!props.file.size ? null : (
                            <Text fontSize={0} title="Size" mr="12px" color="gray">
                                {sizeToString(props.file.size)}
                            </Text>
                        )}
                        {!props.file.modifiedAt ? null : (
                            <Text title="Modified at" fontSize={0} mr="12px" color="gray">
                                <Icon size="10" mr="3px" name="edit" />
                                {format(props.file.modifiedAt, "HH:mm:ss dd/MM/yyyy")}
                            </Text>
                        )}
                        {!props.file.createdAt ? null : (
                            <Text title="Created at" fontSize={0} mr="12px" color="gray">
                                <Icon size="10" mr="3px" name="copy" />
                                {format(props.file.createdAt, "HH:mm:ss dd/MM/yyyy")}
                            </Text>
                        )}
                        {!((props.file.acl?.length ?? 0) > 1) ? null : (
                            <Text title="Members" fontSize={0} mr="12px" color="gray">
                                {props.file.acl?.length} members
                            </Text>
                        )}
                    </Flex>
                </Hide>
            </Box>
        </Flex>
    );
};

const SensitivityIcon = (props: {sensitivity: SensitivityLevelMap | null}) => {
    interface IconDef {
        color: string;
        text: string;
        shortText: string;
    }

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

    const badge = <SensitivityBadge data-tag="sensitivityBadge" bg={def.color}>{def.shortText}</SensitivityBadge>;
    return (
        <Tooltip
            wrapperOffsetLeft="6px"
            wrapperOffsetTop="-5px"
            right="0"
            top="1"
            mb="50px"
            trigger={badge}
        >
            {def.text}
        </Tooltip>
    );
};

const SensitivityBadge = styled.div<{bg: string}>`
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
    files: File[];
    fileOperations: FileOperation[];
    callback: FileOperationCallback;
    directory?: File;
    inDropdown?: boolean;
}

const FileOperations = ({files, fileOperations, ...props}: FileOperations) => {
    if (fileOperations.length === 0) return null;

    const buttons: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode === true);
    const options: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode !== true);

    const Operation = ({fileOp}: {fileOp: FileOperation}) => {
        if (fileOp.currentDirectoryMode === true && props.directory === undefined) return null;
        if (fileOp.currentDirectoryMode !== true && files.length === 0) return null;
        const filesInCallback = fileOp.currentDirectoryMode === true ? [props.directory!] : files;
        if (fileOp.disabled(filesInCallback)) return null;
        // TODO Fixes complaints about not having a callable signature, but loses some typesafety.
        let As: StyledComponent<any, any>;
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
        return (
            <As
                cursor="pointer"
                color={fileOp.color}
                alignItems="center"
                onClick={() => fileOp.onClick(filesInCallback, props.callback)}
                {...props}
            >
                {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon as IconName} /> : null}
                <span>{fileOp.text}</span>
            </As>
        );
    };
    return (
        <>
            {buttons.map((op, i) => <Operation fileOp={op} key={`button-${i}`} />)}
            {files.length === 0 || fileOperations.length === 1 || props.inDropdown ? null :
                <div><TextSpan bold>{files.length} {files.length === 1 ? "file" : "files"} selected</TextSpan></div>
            }
            {options.map((op, i) => <Operation fileOp={op} key={`opt-${i}`} />)}
        </>
    );
};

interface QuickLaunchApps extends SpaceProps {
    file: File;
    applications: QuickLaunchApp[] | undefined;
    history: History<any>;
}

const QuickLaunchApps = ({file, applications, ...props}: QuickLaunchApps) => {
    if (typeof applications === "undefined") return null;
    if (applications.length < 1) return null;

    const Operation = ({quickLaunchApp}: {quickLaunchApp: QuickLaunchApp}) => {
        return (
            <Flex
                cursor="pointer"
                alignItems="center"
                onClick={() => quickLaunchCallback(quickLaunchApp, getParentPath(file.path), props.history)}
                {...props}
            >
                <AppToolLogo name={quickLaunchApp.metadata.name} size="20px" type="APPLICATION" />
                <span>{quickLaunchApp.metadata.title}</span>
            </Flex>
        );
    };

    return (
        <>
            {applications.map((ap, i) => <Operation quickLaunchApp={ap} key={`opt-${i}`} />)}
        </>
    );
};


function getSortingColumnAt(columnIndex: 0 | 1): SortBy {
    const sortingColumn = window.localStorage.getItem(`filesSorting${columnIndex}`);
    if (sortingColumn && Object.values(SortBy).includes(sortingColumn as SortBy)) return sortingColumn as SortBy;
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
