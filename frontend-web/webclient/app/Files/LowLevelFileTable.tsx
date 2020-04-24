import {AppToolLogo} from "Applications/AppToolLogo";
import {APICallParameters, AsyncWorker, callAPI, useAsyncWork} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {emptyPage, KeyCode, ReduxObject, SensitivityLevelMap} from "DefaultObjects";
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
    isAnyMockFile,
    isDirectory,
    isFilePreviewSupported,
    isInvalidPathName,
    MOCK_RELATIVE,
    MOCK_RENAME_TAG,
    mockFile,
    moveFile,
    resolvePath,
    sizeToString,
    MOCK_REPO_CREATE_TAG
} from "Utilities/FileUtilities";
import {buildQueryString} from "Utilities/URIUtilities";
import {addStandardDialog, FileIcon} from "UtilityComponents";
import * as UF from "UtilityFunctions";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import {ListRow} from "ui-components/List";
import {repositoryName, createRepository, renameRepository, isAdminOrPI} from "Utilities/ProjectUtilities";
import {ProjectMember, ProjectRole} from "Project";

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
    permissionAlertEnabled?: boolean;

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

const loadFiles = async (
    attributes: FileResource[],
    callback: (page: Page<File>) => void,
    request: ListDirectoryRequest,
    promises: PromiseKeeper
): Promise<void> => {
    try {
        const response = await callAPI<Page<File>>(listDirectory({
            ...request,
            attrs: [FileResource.PATH, FileResource.FILE_TYPE].concat(attributes)
        }));
        if (promises.canceledKeeper) return;
        callback(response);
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
    setSorting: ((sortBy: SortBy, order: SortOrder, updateColumn?: boolean) => void);
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

function useApiForComponent(
    props: LowLevelFileTableProps,
    setSortByColumns: (s: SortBy) => void
): InternalFileTableAPI {
    const promises = usePromiseKeeper();
    const [managedPage, setManagedPage] = useState<Page<File>>(emptyPage);
    const [pageLoading, pageError, submitPageLoaderJob] = props.asyncWorker ? props.asyncWorker : useAsyncWork();
    const [pageParameters, setPageParameters] = useState<ListDirectoryRequest>({
        ...initialPageParameters,
        type: props.foldersOnly ? "DIRECTORY" : undefined,
        path: Client.homeFolder
    });

    const loadManaged = (request: ListDirectoryRequest): void => {
        setPageParameters(request);
        submitPageLoaderJob(async () => {
            await loadFiles(
                [
                    FileResource.ACL, FileResource.OWNER_NAME, FileResource.FAVORITED,
                    FileResource.SENSITIVITY_LEVEL
                ],
                it => setManagedPage(it),
                request,
                promises);
        });
    };

    useEffect(() => {
        if (props.page === undefined && !Client.fakeFolders.includes(props.path ?? "")) {
            const request = {...pageParameters, path: props.path!};
            if (props.path !== pageParameters.path) request.page = 0;
            loadManaged(request);
        }
    }, [props.path, props.page]);

    if (props.page !== undefined) {
        return {
            page: props.page!,
            pageLoading,
            error: undefined,
            setSorting: () => 0,
            sortBy: SortBy.PATH,
            order: SortOrder.ASCENDING,
            reload: (): void => {
                if (props.onReloadRequested) props.onReloadRequested();
            },
            onPageChanged: (pageNumber: number, itemsPerPage: number): void =>
                props.onPageChanged?.(pageNumber, itemsPerPage)
        };
    } else {
        // TODO Some of these callbacks should use "useCallback"?
        const page = managedPage;
        const error = pageError;
        const loading = pageLoading;

        const setSorting = (sortBy: SortBy, order: SortOrder, updateColumn?: boolean): void => {
            let sortByToUse = sortBy;
            if (sortBy === SortBy.ACL) sortByToUse = pageParameters.sortBy;

            if (updateColumn) {
                setSortingColumn(sortBy);
                setSortByColumns(sortBy);
            }

            loadManaged({
                ...pageParameters,
                sortBy: sortByToUse,
                order,
                page: 0
            });
        };

        const reload = (): void => loadManaged(pageParameters);
        const sortBy = pageParameters.sortBy;
        const order = pageParameters.order;

        const onPageChanged = (pageNumber: number, itemsPerPage: number): void =>
            loadManaged({...pageParameters, page: pageNumber, itemsPerPage});

        return {page, error, pageLoading: loading, setSorting, reload, sortBy, order, onPageChanged};
    }
}


// eslint-disable-next-line no-underscore-dangle
const LowLevelFileTable_: React.FunctionComponent<LowLevelFileTableProps & LowLevelFileTableOperations & {
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
    const [sortByColumn, setSortByColumn] = useState<SortBy>(getSortingColumn());
    const [injectedViaState, setInjectedViaState] = useState<File[]>([]);
    const [workLoading, , invokeWork] = useAsyncWork();
    const [applications, setApplications] = useState<Map<string, QuickLaunchApp[]>>(new Map());

    const promises = usePromiseKeeper();
    const [projectMember, setMember] = React.useState<ProjectMember>({
        role: ProjectRole.USER, username: Client.username ?? ""
    });
    React.useEffect(() => {
        getProjectMember();
    }, [Client.projectId]);

    const history = useHistory();

    const {page, error, pageLoading, setSorting, reload, sortBy, order, onPageChanged} =
        useApiForComponent(props, setSortByColumn);

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
            props.setUploaderCallback(() => reload());
        }
    }, [reload]);

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
        requestFolderCreation: (isRepo?: boolean) => {
            if (props.path === undefined) return;
            const path = `${props.path}/newFolder`;
            setInjectedViaState([
                mockFile({
                    path,
                    tag: isRepo ? MOCK_REPO_CREATE_TAG : MOCK_RENAME_TAG,
                    type: "DIRECTORY"
                })
            ]
            );
            setFileBeingRenamed(path);

            if (!isEmbedded) window.scrollTo({top: 0});
        },
        startRenaming: file => setFileBeingRenamed(file.path),
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
    React.useEffect(() => {
        if (props.refreshHook !== undefined) {
            props.refreshHook(true, () => callbacks.requestReload());
        }
    }, [props.refreshHook, callbacks.requestReload]);

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
        allFiles.every(f => checkedFiles.has(f.path) || f.mockTag !== undefined);
    const isMasterDisabled = allFiles.every(f => f.mockTag !== undefined);
    const isAnyLoading = workLoading || pageLoading;
    const checkedFilesWithInfo = allFiles
        .filter(f => f.path && checkedFiles.has(f.path) && f.mockTag === undefined);
    const onFileNavigation = (path: string): void => {
        setCheckedFiles(new Set());
        setFileBeingRenamed(null);
        setInjectedViaState([]);
        if (!isEmbedded) window.scrollTo({top: 0});
        props.onFileNavigation(path);
    };

    // Loading state
    React.useEffect(() => {
        props.onLoadingState?.(isAnyLoading);
    }, [isAnyLoading]);

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
                            projectFolder={Client.projectFolder}
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
                            <RepositoryOperations role={projectMember.role} path={props.path} createFolder={callbacks.requestFolderCreation} />
                            <FileOperations
                                files={checkedFilesWithInfo}
                                fileOperations={fileOperations}
                                callback={callbacks}
                                role={projectMember?.role}
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
                                onClick={(): void => props.activeUploadCount ? addStandardDialog({
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
                        <StickyBox backgroundColor="white">
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
                                            <Box as={"span"}>Select all</Box>
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
                                                onClick={(): void => setSorting(
                                                    sortByColumn, order === SortOrder.ASCENDING ?
                                                    SortOrder.DESCENDING : SortOrder.ASCENDING, true
                                                )}
                                            >
                                                <>
                                                    {UF.prettierString(order === SortOrder.ASCENDING ?
                                                        SortOrder.DESCENDING : SortOrder.ASCENDING
                                                    )}
                                                </>
                                            </Box>
                                            <Divider />
                                            {Object.values(SortBy)
                                                .filter(it => it !== sortByColumn)
                                                .map((sortByValue: SortBy, j) => (
                                                    <Box
                                                        ml="-16px"
                                                        mr="-16px"
                                                        pl="15px"
                                                        key={j}
                                                        onClick={(): void =>
                                                            setSorting(sortByValue, SortOrder.ASCENDING, true)}
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

    // Private utility functions

    async function getProjectMember(): Promise<void> {
        if (!!Client.projectId && !!Client.username) {
            try {
                const {response} = await promises.makeCancelable(Client.get<{member: ProjectMember}>(
                    `/projects/members?projectId=${encodeURIComponent(Client.projectId)}&username=${encodeURIComponent(Client.username)}`
                )).promise;
                setMember(response.member);
            } catch (err) {
                if (promises.canceledKeeper) return;
                snackbarStore.addFailure(UF.errorMessageOrDefault(err, "An error ocurred fetcing member info."));
            }
        }
    }

    function setChecked(updatedFiles: File[], checkStatus?: boolean): void {
        const checked = new Set(checkedFiles);
        if (checkStatus === false) {
            checked.clear();
        } else {
            updatedFiles.forEach(file => {
                if (checkStatus === true) {
                    checked.add(file.path);
                } else {
                    if (checked.has(file.path)) checked.delete(file.path);
                    else checked.add(file.path);
                }
            });
        }

        setCheckedFiles(checked);
    }

    function onRenameFile(key: number, name: string): void {
        if (key === KeyCode.ESC) {
            setInjectedViaState([]);
            setFileBeingRenamed(null);
        } else if (key === KeyCode.ENTER) {
            const file = allFiles.find(f => f.path === fileBeingRenamed);
            if (file === undefined) return;
            const isProjectRepo = file.mockTag === MOCK_REPO_CREATE_TAG || !!file.isRepo;
            const fileNames = allFiles.map(f => getFilenameFromPath(f.path));
            if (isInvalidPathName({path: name, filePaths: fileNames})) return;
            if (isProjectRepo) {
                if (file.mockTag === MOCK_REPO_CREATE_TAG) {
                    createRepository(Client, name, callbacks.requestReload);
                } else {
                    renameRepository(getFilenameFromPath(file.path), name, Client, callbacks.requestReload);
                }
            } else {
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
                        onSuccess: () => callbacks.requestReload()
                    });
                }
            }
        }
    }

    function pageRenderer({items}: Page<File>): React.ReactNode {
        return (
            <List>
                {items.map(f => (
                    <ListRow
                        key={f.path}
                        isSelected={checkedFiles.has(f.path)}
                        select={() => {
                            if (!isAnyMockFile([f]) && !isEmbedded) setChecked([f]);
                        }}
                        navigate={() => onFileNavigation(f.path)}
                        left={<NameBox
                            file={f}
                            onRenameFile={onRenameFile}
                            onNavigate={onFileNavigation}
                            callbacks={callbacks}
                            fileBeingRenamed={fileBeingRenamed}
                            previewEnabled={props.previewEnabled}
                        />}
                        right={
                            (f.mockTag !== undefined && f.mockTag !== MOCK_RELATIVE) ? null : (
                                <Flex alignItems="center" onClick={UF.stopPropagation}>
                                    {props.permissionAlertEnabled !== true || f.permissionAlert !== true ? null : (
                                        <Tooltip
                                            wrapperOffsetLeft="0"
                                            wrapperOffsetTop="4px"
                                            right="0"
                                            top="1"
                                            mb="50px"
                                            trigger={(
                                                <BaseLink href={"#"} onClick={e => {
                                                    e.preventDefault();
                                                    addStandardDialog({
                                                        title: "Non-standard metadata",
                                                        message: "This file has some non-standard metadata. This can cause problems in some applications. Do you wish to resolve this issue?",
                                                        confirmText: "Resolve issue",
                                                        onConfirm: async () => {
                                                            await Client.post("/files/normalize-permissions", {path: f.path});
                                                            callbacks.requestReload();
                                                        }
                                                    });
                                                }}>
                                                    <Icon
                                                        cursor="pointer"
                                                        size="24px"
                                                        mr="8px"
                                                        color="midGray"
                                                        hoverColor="gray"
                                                        name="warning"
                                                    />
                                                </BaseLink>
                                            )}
                                        >
                                            Non-standard metadata
                                        </Tooltip>
                                    )}
                                    {!(props.previewEnabled && isFilePreviewSupported(f)) ? null :
                                        f.size != null
                                            && UF.inRange({status: f.size, max: PREVIEW_MAX_SIZE, min: 1}) ? (
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
                                                                mr="8px"
                                                                color="midGray"
                                                                hoverColor="gray"
                                                                name="preview"
                                                            />
                                                        </Link>
                                                    )}
                                                >
                                                    Preview available
                                                </Tooltip>
                                            ) : (
                                                <Tooltip
                                                    wrapperOffsetLeft="0"
                                                    wrapperOffsetTop="4px"
                                                    tooltipContentWidth="85px"
                                                    right="0"
                                                    top="1"
                                                    mb="50px"
                                                    trigger={(
                                                        <Icon
                                                            opacity="0.5"
                                                            cursor="default"
                                                            size="24px"
                                                            mr="8px"
                                                            color="midGray"
                                                            name="preview"
                                                        />
                                                    )}
                                                >
                                                    {(f.size ?? 0) > 0 ? "File too large for preview" : "File is empty"}
                                                </Tooltip>
                                            )}
                                    {props.omitQuickLaunch ? null : f.fileType !== "FILE" ? null :
                                        ((applications.get(f.path) ?? []).length < 1) ? null : (
                                            <ClickableDropdown
                                                width="auto"
                                                minWidth="175px"
                                                left="-160px"
                                                trigger={<Icon mr="8px" name="play" size="1em" color="midGray" hoverColor="gray" style={{display: "block"}} />}
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
                                        <Box>
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
                                                    role={projectMember.role}
                                                />
                                            </ClickableDropdown>
                                        </Box>
                                    ) : (
                                            <Box mt="-2px" ml="5px">
                                                <FileOperations
                                                    role={projectMember.role}
                                                    files={[f]}
                                                    fileOperations={fileOperations}
                                                    callback={callbacks}
                                                />
                                            </Box>
                                        )}
                                </Flex>
                            )}
                    />
                ))}
            </List>
        );
    }
};

const StickyBox = styled(Box)`
    position: sticky;
    top: 144px;
    z-index: 50;
`;

function toWebDav(): void {
    const a = document.createElement("a");
    a.href = "/app/login?dav=true";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

const mapStateToProps = ({uploader}: ReduxObject): {activeUploadCount: number} => {
    const activeUploadCount = uploader.uploads.filter(it =>
        (it.uploadXHR?.readyState ?? -1 > XMLHttpRequest.UNSENT) &&
        (it.uploadXHR?.readyState ?? -1 < XMLHttpRequest.DONE)).length;
    return {activeUploadCount};
};


interface LowLevelFileTableOperations {
    showUploader(path: string): void;
    setUploaderCallback(cb?: () => void): void;
    appendUpload(upload: Upload): void;
}

const mapDispatchToProps = (dispatch: Dispatch): LowLevelFileTableOperations => ({
    showUploader: path => dispatch(setUploaderVisible(true, path)),
    setUploaderCallback: cb => dispatch(setUploaderCallback(cb)),
    appendUpload: upload => dispatch(appendUpload(upload))
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
        <Flex mr="10px" alignItems="center" cursor="inherit">
            <FileIcon
                fileIcon={UF.iconFromFilePath(props.file.path, props.file.fileType, Client)}
                size={42}
                shared={(props.file.acl != null ? props.file.acl.length : 0) > 0}
            />
        </Flex>
    );

    const beingRenamed = props.file.path !== null && props.file.path === props.fileBeingRenamed;
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
            <Truncate width={1} mb="-4px" fontSize={20}>
                {getFilenameFromPath(props.file.path)}
            </Truncate>
        );

    return (
        <Flex maxWidth="calc(100% - 210px)">
            <Flex mx="10px" alignItems="center" >
                {isAnyMockFile([props.file]) ? <Box width="24px" /> : (
                    <Icon
                        cursor="pointer"
                        size="24"
                        name={favorite ? "starFilled" : "starEmpty"}
                        color={favorite ? "blue" : "midGray"}
                        onClick={(event): void => {
                            event.stopPropagation();
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
            </Flex>
            {icon}
            <Box width="100%">
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
                ) : props.previewEnabled && isFilePreviewSupported(props.file) && !beingRenamed &&
                    UF.inRange({status: props.file.size ?? 0, min: 1, max: PREVIEW_MAX_SIZE}) ?
                        <Link to={filePreviewQuery(props.file.path)}>{fileName}</Link> :
                        fileName
                }

                <Hide sm xs>
                    <Flex mt="4px">
                        {!props.file.size || isDirectory(props.file) ? null : (
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

function RepositoryOperations(props: {
    path: string | undefined;
    createFolder: (isRepo?: boolean) => void;
    role: ProjectRole;
}): JSX.Element | null {
    if (props.path !== Client.projectFolder || ![ProjectRole.ADMIN, ProjectRole.PI].includes(props.role)) {
        return null;
    }
    return <Button width="100%" onClick={() => props.createFolder(true)}>New Repository</Button>;
}

const SensitivityIcon = (props: {sensitivity: SensitivityLevelMap | null}): JSX.Element => {
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
    role: ProjectRole;
}

const FileOperations = ({files, fileOperations, role, ...props}: FileOperations): JSX.Element | null => {
    if (fileOperations.length === 0) return null;

    const buttons: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode === true);
    const options: FileOperation[] = fileOperations.filter(it => it.currentDirectoryMode !== true);

    const Operation = ({fileOp}: {fileOp: FileOperation}): JSX.Element | null => {
        if (fileOp.repositoryMode && !isAdminOrPI(role)) return null;
        if (fileOp.repositoryMode && files.some(it => !repositoryName(it.path))) return null;
        if (!fileOp.repositoryMode && files.some(it => repositoryName(it.path))) return null;

        if (fileOp.currentDirectoryMode === true && props.directory === undefined) return null;
        if (fileOp.currentDirectoryMode !== true && files.length === 0) return null;
        const filesInCallback = fileOp.currentDirectoryMode === true ? [props.directory!] : files;
        if (fileOp.disabled(filesInCallback)) return null;
        // TODO Fixes complaints about not having a callable signature, but loses some typesafety.
        let As: StyledComponent<any, any>;
        if (fileOperations.length === 1) {
            As = OutlineButton;
        } else if (props.inDropdown) {
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
                onClick={(): void => fileOp.onClick(filesInCallback, props.callback)}
                {...props}
            >
                {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon as IconName} /> : null}
                <span>{fileOp.text}</span>
            </As>
        );
    };
    return (
        <>
            {buttons.map((op, i) => <Operation fileOp={op} key={i} />)}
            {files.length === 0 || fileOperations.length === 1 || props.inDropdown ? null :
                <div><TextSpan bold>{files.length} {files.length === 1 ? "file" : "files"} selected</TextSpan></div>
            }
            {options.map((op, i) => <Operation fileOp={op} key={i} />)}
        </>
    );
};

interface QuickLaunchApps extends SpaceProps {
    file: File;
    applications: QuickLaunchApp[] | undefined;
    history: History<any>;
}

const QuickLaunchApps = ({file, applications, ...props}: QuickLaunchApps): JSX.Element | null => {
    if (applications === undefined) return null;
    if (applications.length < 1) return null;

    const Operation = ({quickLaunchApp}: {quickLaunchApp: QuickLaunchApp}): React.ReactElement => {
        return (
            <Flex
                cursor="pointer"
                alignItems="center"
                onClick={() => quickLaunchCallback(quickLaunchApp, getParentPath(file.path), props.history)}
                width="auto"
                {...props}
            >
                <AppToolLogo name={quickLaunchApp.metadata.name} size="20px" type="APPLICATION" />
                <span style={{marginLeft: "5px", marginRight: "5px"}}>{quickLaunchApp.metadata.title}{quickLaunchApp.metadata.title}{quickLaunchApp.metadata.title}{quickLaunchApp.metadata.title}{quickLaunchApp.metadata.title}</span>
            </Flex>
        );
    };

    return (
        <>
            {applications.map((ap, i) => <Operation quickLaunchApp={ap} key={i} />)}
        </>
    );
};


function getSortingColumn(): SortBy {
    const sortingColumn = window.localStorage.getItem("filesSorting");
    if (sortingColumn && Object.values(SortBy).includes(sortingColumn as SortBy)) {
        return sortingColumn as SortBy;
    }
    window.localStorage.setItem("filesSorting", SortBy.PATH);
    return SortBy.PATH;
}

function setSortingColumn(column: SortBy): void {
    window.localStorage.setItem("filesSorting", column);
}
