import * as React from "react";
import {useEffect, useState} from "react";
import {AppToolLogo} from "Applications/AppToolLogo";
import {AsyncWorker, callAPI, useAsyncWork} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {emptyPage, KeyCode, SensitivityLevelMap} from "DefaultObjects";
import {File, FileType, SortBy, SortOrder} from "Files";
import {
    defaultFileOperations, FileOperation, FileOperationCallback, FileOperationRepositoryMode
} from "Files/FileOperations";
import {QuickLaunchApp, quickLaunchCallback} from "Files/QuickLaunch";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {Refresh} from "Navigation/Header";
import * as Pagination from "Pagination";
import PromiseKeeper, {usePromiseKeeper} from "PromiseKeeper";
import {useDispatch, useSelector} from "react-redux";
import {useHistory} from "react-router";
import styled, {StyledComponent} from "styled-components";
import {SpaceProps} from "styled-system";
import {
    Button, Box, Flex, Card, Checkbox, Divider, Hide, Icon, Input,
    Label, Link, List, OutlineButton, Text, Tooltip, Truncate
} from "ui-components";
import BaseLink from "ui-components/BaseLink";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {appendUpload, setUploaderCallback, setUploaderVisible} from "Uploader/Redux/UploaderActions";
import * as FUtils from "Utilities/FileUtilities";
import * as UF from "UtilityFunctions";
import {buildQueryString} from "Utilities/URIUtilities";
import {addStandardDialog, FileIcon, ConfirmCancelButtons, shareDialog} from "UtilityComponents";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import {ListRow} from "ui-components/List";
import {
    createRepository, isRepository, renameRepository, getProjectNames, isAdminOrPI, updatePermissionsPrompt
} from "Utilities/ProjectUtilities";
import {ProjectRole, useProjectManagementStatus} from "Project";
import {useFavoriteStatus} from "Files/favorite";
import {useFilePermissions} from "Files/permissions";
import {ProjectStatus, useProjectStatus} from "Project/cache";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {useAppQuickLaunch} from "Utilities/ApplicationUtilities";
import {isAnyMockFile, MOCK_RENAME_TAG, MOCK_REPO_CREATE_TAG} from "Utilities/FileUtilities";
import {fakeProjectListPath} from "Files/FileSelector";

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
    disableNavigationButtons?: boolean;
}

export interface ListDirectoryRequest {
    path: string;
    page: number;
    itemsPerPage: number;
    order: SortOrder;
    sortBy: SortBy;
    type?: FileType;
}

export const statFile = (request: { path: string }): APICallParameters<{ path: string }> => ({
    method: "GET",
    path: buildQueryString("/files/stat", request),
    parameters: request,
    reloadId: Math.random()
});

export const listDirectory = ({
                                  path, page, itemsPerPage, order, sortBy, type
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
            type
        }
    ),
    parameters: {path, page, itemsPerPage, order, sortBy},
    reloadId: Math.random()
});

const loadFiles = async (
    callback: (page: Page<File>) => void,
    request: ListDirectoryRequest,
    promises: PromiseKeeper
): Promise<void> => {
    try {
        const response = await callAPI<Page<File>>(listDirectory({...request}));
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
    path: "TO_BE_REPLACED"
};

function useApiForComponent(
    props: LowLevelFileTableProps,
    setSortByColumns: (s: SortBy) => void
): InternalFileTableAPI {
    const promises = usePromiseKeeper();
    const [managedPage, setManagedPage] = useState<Page<File>>(emptyPage);
    const [pageLoading, pageError, submitPageLoaderJob] = props.asyncWorker ?? useAsyncWork();
    const [pageParameters, setPageParameters] = useState<ListDirectoryRequest>({
        ...initialPageParameters,
        type: props.foldersOnly ? "DIRECTORY" : undefined,
        path: Client.homeFolder
    });

    const loadManaged = (request: ListDirectoryRequest): void => {
        setPageParameters(request);
        submitPageLoaderJob(async () => {
            await loadFiles(
                it => {
                    setManagedPage(it);
                },
                request,
                promises
            );
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


export const LowLevelFileTable: React.FunctionComponent<LowLevelFileTableProps> = props => {
    // Validation
    if (props.page === undefined && props.path === undefined) {
        throw Error("FilesTable must set either path or page property");
    }

    if (props.page !== undefined && props.path === undefined && props.embedded !== true) {
        throw Error("page is not currently supported in non-embedded mode without a path");
    }

    const {projectRole} = useProjectManagementStatus({isRootComponent: !props.embedded, allowPersonalProject: true});

    // Hooks
    const [checkedFiles, setCheckedFiles] = useState<Set<string>>(new Set());
    const [fileBeingRenamed, setFileBeingRenamed] = useState<string | null>(null);
    const [sortByColumn, setSortByColumn] = useState<SortBy>(getSortingColumn());
    const [injectedViaState, setInjectedViaState] = useState<File[]>([]);
    const [workLoading, , invokeWork] = useAsyncWork();
    const favorites = useFavoriteStatus();
    const projects = useProjectStatus();
    const dispatch = useDispatch();
    const projectMember = (
        !Client.projectId ?
            undefined :
            projects.fetch().membership.find(it => it.projectId === Client.projectId)?.whoami
    ) ?? {username: Client.username, role: ProjectRole.USER};

    useEffect(() => {
        projects.reload();
    }, [Client.projectId]);

    const history = useHistory();

    const activeUploadCount = useSelector<ReduxObject, number>(redux =>
        redux.uploader.uploads.filter(upload =>
            ((upload.uploadXHR?.readyState ?? -1 > XMLHttpRequest.UNSENT) &&
                (upload.uploadXHR?.readyState ?? -1 < XMLHttpRequest.DONE))).length
    );
    const {page, error, pageLoading, setSorting, reload, sortBy, order, onPageChanged} =
        useApiForComponent(props, setSortByColumn);

    useEffect(() => {
        const isKnownToBeFavorite = props.path === Client.favoritesFolder;
        const files = page.items
            .filter(it => it.mockTag === FUtils.MOCK_VIRTUAL || it.mockTag === undefined)
            .map(it => it.path);

        favorites.updateCache(files, isKnownToBeFavorite);
    }, [page]);

    // Fetch quick launch applications upon page refresh

    const applications = useAppQuickLaunch(page, Client);

    useEffect(() => {
        if (!props.embedded) {
            dispatch(setUploaderCallback(() => reload()));
        }
    }, [reload]);

    useEffect(() => {
        return () => {
            dispatch(setUploaderCallback());
            props.onLoadingState?.(false);
        };
    }, []);

    const permissions = useFilePermissions();
    const projectNames = getProjectNames(projects);

    // Callbacks for operations
    const callbacks: FileOperationCallback = {
        projects: projectNames,
        permissions,
        invokeAsyncWork: fn => invokeWork(fn),
        requestReload: () => {
            setFileBeingRenamed(null);
            setCheckedFiles(new Set());
            setInjectedViaState([]);
            reload();
        },
        requestFileUpload: () => {
            const path = props.path ? props.path : Client.homeFolder;
            dispatch(setUploaderVisible(true, path));
        },
        requestFolderCreation: (isRepo?: boolean) => {
            if (props.path === undefined) return;
            const path = `${props.path}/newFolder`;
            setInjectedViaState([
                    FUtils.mockFile({
                        path,
                        tag: isRepo ? FUtils.MOCK_REPO_CREATE_TAG : FUtils.MOCK_RENAME_TAG,
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
            dispatch(appendUpload(upload));

            const path = props.path ?? Client.homeFolder;
            dispatch(setUploaderVisible(true, path));
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
    const forbidden = error === "Forbidden";
    // At the time of writing, "Not found " provided by backend error is trailed by a space.
    const notFound = error === "Not found ";
    const isForbiddenPath = forbidden || notFound;
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
    const checkedFilesWithInfo = allFiles.filter(f => f.path && checkedFiles.has(f.path) && f.mockTag === undefined);
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

    React.useEffect(() => {
        setInjectedViaState([]);
        setFileBeingRenamed(null);
    }, [Client.projectId]);

    return (
        <Shell
            embedded={isEmbedded}

            header={(
                <>
                    <Spacer
                        left={(
                            <BreadCrumbs
                                embedded={!!props.embedded}
                                currentPath={props.path ?? ""}
                                navigate={onFileNavigation}
                                client={Client}
                            />
                        )}

                        right={(
                            <>
                                {(!isEmbedded && props.path) || props.disableNavigationButtons === true ? null : (
                                    <>
                                        <Box mt="9px" ml="6px">
                                            <Refresh
                                                spin={isAnyLoading}
                                                onClick={callbacks.requestReload}
                                            />
                                        </Box>
                                    </>
                                )}

                                {isEmbedded ? null : (
                                    <Flex minWidth="160px">
                                        <Pagination.EntriesPerPageSelector
                                            content="Files per page"
                                            entriesPerPage={page.itemsPerPage}
                                            onChange={amount => onPageChanged(0, amount)}
                                        />
                                    </Flex>
                                )}
                            </>
                        )}
                    />
                    {(!isEmbedded && props.path) || props.disableNavigationButtons === true ? null : (
                        <Box my={8}>
                            <Button onClick={() => onFileNavigation(Client.homeFolder)} mr={8}>
                                <Icon color="white" color2="gray" name="home" mr={"4px"}/>
                                Personal Home
                            </Button>
                            <Button onClick={() => onFileNavigation(fakeProjectListPath)}>
                                <Icon color="white" color2="gray" name="projects" mr={"4px"}/>
                                Project List
                            </Button>
                        </Box>
                    )}
                </>
            )}

            sidebar={(
                <Box pl="5px" pr="5px" height="calc(100% - 20px)">
                    {isForbiddenPath ? <></> : (
                        <VerticalButtonGroup>
                            <RepositoryOperations role={projectRole} path={props.path}
                                                  createFolder={callbacks.requestFolderCreation}/>
                            <FileOperations
                                files={checkedFilesWithInfo}
                                fileOperations={fileOperations}
                                callback={callbacks}
                                role={projectMember?.role}
                                // Don't pass a directory if the page is set.
                                // This should indicate that the path is fake.
                                directory={props.page !== undefined ? undefined : FUtils.mockFile({
                                    path: props.path ? props.path : "",
                                    fileId: "currentDir",
                                    tag: FUtils.MOCK_RELATIVE,
                                    type: "DIRECTORY"
                                })}
                            />
                            <Box flexGrow={1}/>
                            <OutlineButton
                                onClick={(): void => activeUploadCount ? addStandardDialog({
                                    title: "Continue",
                                    message: (
                                        <Box>
                                            <Text>You have tasks that will be cancelled if you continue.</Text>
                                            {activeUploadCount ? (
                                                <Text>
                                                    {activeUploadCount} uploads in progress.
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
                    {!sortingSupported ? <div/> : (
                        <StickyBox backgroundColor="white">
                            <Spacer
                                left={isMasterDisabled ? null : (
                                    <Box mr="18px">
                                        <Label ml={10}>
                                            <Checkbox
                                                size={27}
                                                data-tag="masterCheckbox"
                                                onClick={() => setChecked(
                                                    allFiles.filter(it => !FUtils.isAnyMockFile([it])), !isMasterChecked
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
                                            <Divider/>
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
                            null : <div>{messageFromError(error)}</div>}
                        page={{...page, items: allFiles}}
                        onPageChanged={(newPage, currentPage) => onPageChanged(newPage, currentPage.itemsPerPage)}
                        pageRenderer={pageRenderer}
                    />
                </>
            )}
        />
    );

    // Private utility functions

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
            const isProjectRepo = file.mockTag === FUtils.MOCK_REPO_CREATE_TAG || !!file.isRepo;
            const fileNames = allFiles.map(f => FUtils.getFilenameFromPath(f.path, []));
            if (FUtils.isInvalidPathName({path: name, filePaths: fileNames})) return;
            if (isProjectRepo) {
                if (file.mockTag === FUtils.MOCK_REPO_CREATE_TAG) {
                    createRepository(Client, name, callbacks.requestReload);
                } else {
                    renameRepository(FUtils.getFilenameFromPath(file.path, []), name, Client, callbacks.requestReload);
                }
            } else {
                const fullPath = `${UF.addTrailingSlash(FUtils.getParentPath(file.path))}${name}`;
                if (file.mockTag === FUtils.MOCK_RENAME_TAG) {
                    FUtils.createFolder({
                        path: fullPath,
                        client: Client,
                        onSuccess: () => callbacks.requestReload()
                    });
                } else {
                    FUtils.moveFile({
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
                            if (!FUtils.isAnyMockFile([f]) && !isEmbedded) setChecked([f]);
                        }}
                        navigate={() => onFileNavigation(f.path)}
                        left={<NameBox
                            file={f}
                            isEmbedded={props.embedded}
                            onRenameFile={onRenameFile}
                            onNavigate={onFileNavigation}
                            callbacks={callbacks}
                            fileBeingRenamed={fileBeingRenamed}
                            previewEnabled={props.previewEnabled}
                            projectRole={projectMember.role}
                        />}
                        right={
                            (f.mockTag !== undefined && f.mockTag !== FUtils.MOCK_RELATIVE) ? null : (
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
                                                            const {path} = f;
                                                            await Client.post("/files/normalize-permissions", {path});
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
                                    {!(props.previewEnabled && FUtils.isFilePreviewSupported(f)) ? null :
                                        f.size != null
                                        && UF.inRange({status: f.size, max: PREVIEW_MAX_SIZE, min: 1}) ? (
                                            <Tooltip
                                                wrapperOffsetLeft="0"
                                                wrapperOffsetTop="4px"
                                                right="0"
                                                top="1"
                                                mb="50px"
                                                trigger={(
                                                    <Link to={FUtils.filePreviewQuery(f.path)}>
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
                                                trigger={
                                                    <Icon
                                                        mr="8px"
                                                        name="play"
                                                        size="1em"
                                                        color="midGray"
                                                        hoverColor="gray"
                                                        style={{display: "block"}}
                                                    />
                                                }
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
                                    <SensitivityIcon isRepo={f.isRepo} sensitivity={f.sensitivityLevel}/>
                                    {checkedFiles.size !== 0 ? <Box width="33px"/> :
                                        <FileOperations
                                            inDropdown={fileOperations.length > 1}
                                            files={[f]}
                                            fileOperations={fileOperations}
                                            callback={callbacks}
                                            role={projectMember.role}
                                        />}
                                </Flex>
                            )}
                    />
                ))}
            </List>
        );
    }
};

function messageFromError(error: string): string {
    if (error === "Not Found ") return "Folder not found.";
    if (error === "Forbidden") return "You do not have access to this folder.";
    return error;
}

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


function getFileNameForNameBox(path: string, projectStatus: ProjectStatus): string {
    if (FUtils.isMyPersonalFolder(path)) {
        return `Personal Files (${Client.username})`;
    } else if (FUtils.isProjectHome(path)) {
        const projectId = FUtils.projectIdFromPath(path);
        return projectStatus.fetch().membership.find(it => it.projectId === projectId)?.title
            ?? FUtils.getFilenameFromPath(path, []);
    }

    return FUtils.getFilenameFromPath(path, []);
}

const RenameBox = (props: { file: File; onRenameFile: (keycode: number, value: string) => void }): JSX.Element => {
    const projectNames = getProjectNames(useProjectStatus());
    const ref = React.useRef<HTMLInputElement>(null);
    return (
        <Flex width={1} alignItems="center">
            <Input
                placeholder={props.file.mockTag ? "" : FUtils.getFilenameFromPath(props.file.path, projectNames)}
                defaultValue={props.file.mockTag ? "" : FUtils.getFilenameFromPath(props.file.path, projectNames)}
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
                ref={ref}
                autoFocus
                data-tag="renameField"
                onKeyDown={e => props.onRenameFile?.(e.keyCode, (e.target as HTMLInputElement).value)}
            />
            <ConfirmCancelButtons
                confirmText="Create"
                cancelText="Cancel"
                onConfirm={() => props.onRenameFile?.(KeyCode.ENTER, ref.current?.value ?? "")}
                onCancel={() => props.onRenameFile?.(KeyCode.ESC, "")}
            />
        </Flex>
    );
};


interface NameBoxProps {
    file: File;
    onRenameFile: (keycode: number, value: string) => void;
    onNavigate: (path: string) => void;
    fileBeingRenamed: string | null;
    callbacks: FileOperationCallback;
    previewEnabled?: boolean;
    projectRole?: ProjectRole;
    isEmbedded?: boolean;
}

const NameBox: React.FunctionComponent<NameBoxProps> = props => {
    const projectStatus = useProjectStatus();
    const favorites = useFavoriteStatus();
    const canNavigate = FUtils.isDirectory({fileType: props.file.fileType});

    const icon = (
        <Flex mr="10px" alignItems="center" cursor="inherit">
            <FileIcon
                fileIcon={UF.iconFromFilePath(props.file.path, props.file.fileType)}
                size={42}
                shared={(props.file.acl != null ? props.file.acl.length : 0) > 0}
            />
        </Flex>
    );

    const beingRenamed = props.file.path !== null && props.file.path === props.fileBeingRenamed;
    const fileName = beingRenamed ? (
        <RenameBox file={props.file} onRenameFile={props.onRenameFile}/>
    ) : (
        <Truncate width={1} mb="-4px" fontSize={20}>
            {getFileNameForNameBox(props.file.path, projectStatus)}
        </Truncate>
    );

    return (
        <Flex maxWidth={`calc(100% - ${220 + (props.isEmbedded ? 15 : 0)}px)`}>
            <Flex mx="10px" alignItems="center">
                {FUtils.isAnyMockFile([props.file]) ? <Box width="24px"/> : (
                    <Icon
                        cursor="pointer"
                        size="24"
                        name={(favorites.cache[props.file.path] ?? false) ? "starFilled" : "starEmpty"}
                        color={(favorites.cache[props.file.path] ?? false) ? "blue" : "midGray"}
                        onClick={(event): void => {
                            event.stopPropagation();
                            favorites.toggle(props.file.path);
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
                            props.onNavigate(FUtils.resolvePath(props.file.path));
                        }}
                    >
                        {fileName}
                    </BaseLink>
                ) : props.previewEnabled && FUtils.isFilePreviewSupported(props.file) && !beingRenamed &&
                UF.inRange({status: props.file.size ?? 0, min: 1, max: PREVIEW_MAX_SIZE}) ?
                    <Link to={FUtils.filePreviewQuery(props.file.path)}>{fileName}</Link> : fileName
                }

                <Hide sm xs>
                    <Flex mt="4px">
                        {!props.file.size || FUtils.isDirectory(props.file) ? null : (
                            <Text fontSize={0} title="Size" mr="12px" color="gray">
                                {FUtils.sizeToString(props.file.size)}
                            </Text>
                        )}
                        {!props.file.modifiedAt ? null : (
                            <Text title="Modified at" fontSize={0} mr="12px" color="gray">
                                <Icon size="10" mr="3px" name="edit"/>
                                {format(props.file.modifiedAt, "HH:mm:ss dd/MM/yyyy")}
                            </Text>
                        )}
                        <MembersFileRowStat file={props.file} projectRole={props.projectRole}
                                            requestReload={props.callbacks.requestReload}/>
                    </Flex>
                </Hide>
            </Box>
        </Flex>
    );
};

const MembersFileRowStat: React.FunctionComponent<{
    file: File;
    projectRole?: ProjectRole;
    requestReload: () => void;
}> = ({file, projectRole, requestReload}) => {
    const aclLength = (file.acl ?? []).filter(it => it.rights.length > 0).length;
    if (aclLength === 0) {
        if (!FUtils.isPartOfProject(file.path)) return null;
        if (FUtils.isPartOfSomePersonalFolder(file.path)) return null;
        if (projectRole === undefined) return null;
        if (!isAdminOrPI(projectRole)) return null;
        if (file.mockTag === MOCK_REPO_CREATE_TAG) return null;
        if (FUtils.isPersonalRootFolder(file.path)) {
            return (
                <Text title={"members"} fontSize={0} mr={"12px"} color={"gray"}>
                    <Icon name={"info"} color={"white"} color2={"iconColor"} size={13} mr={"3px"}/>
                    Admins only
                </Text>
            );
        }
        return (
            <Text
                fontSize={0}
                mr={"12px"}
                color={"red"}
                cursor={"pointer"}
                onClick={e => {
                    e.stopPropagation();
                    updatePermissionsPrompt(Client, file, requestReload);
                }}
            >
                <Icon name={"warning"} color={"red"} size={13} mr={"3px"}/>
                Usable only by project admins
            </Text>
        );
    } else {
        return (
            <Text
                title={"members"}
                fontSize={0}
                mr={"12px"}
                color={"gray"}
                cursor={"pointer"}
                onClick={e => {
                    e.stopPropagation();
                    if (FUtils.isPartOfProject(file.path)) {
                        updatePermissionsPrompt(Client, file, requestReload);
                    } else {
                        shareDialog([file.path], Client);
                    }
                }}
            >
                {aclLength} {aclLength === 1 ? "member" : "members"}
            </Text>
        );
    }
};

function RepositoryOperations(props: {
    path: string | undefined;
    createFolder: (isRepo?: boolean) => void;
    role: ProjectRole;
}): JSX.Element | null {
    if (props.path === undefined || !FUtils.isProjectHome(props.path) || !isAdminOrPI(props.role)) {
        return null;
    }
    return <Button width="100%" onClick={() => props.createFolder(true)}>New Folder</Button>;
}

const SensitivityIcon = (props: { sensitivity: SensitivityLevelMap | null, isRepo?: boolean }): JSX.Element | null => {
    if (props.isRepo) return null;

    interface IconDef {
        color: string;
        text: string;
        shortText: string;
    }

    let def: IconDef;

    switch (props.sensitivity) {
        case SensitivityLevelMap.CONFIDENTIAL:
            def = {color: getCssVar("purple"), text: "Confidential", shortText: "C"};
            break;
        case SensitivityLevelMap.SENSITIVE:
            def = {color: "#ff0004", text: "Sensitive", shortText: "S"};
            break;
        case SensitivityLevelMap.PRIVATE:
            def = {color: getCssVar("midGray"), text: "Private", shortText: "P"};
            break;
        default:
            def = {color: getCssVar("midGray"), text: "", shortText: ""};
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

const SensitivityBadge = styled.div<{ bg: string }>`
    content: '';
    height: 2em;
    width: 2em;
    display: flex;
    margin-right: 5px;
    align-items: center;
    justify-content: center;
    border: 0.2em solid ${props => props.bg};
    border-radius: 100%;
`;

interface FileOperations {
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

    const isLegalOperation = (fileOp: FileOperation): boolean => {
        const repoMode = fileOp.repositoryMode ?? FileOperationRepositoryMode.DISALLOW;
        if (repoMode === FileOperationRepositoryMode.REQUIRED && !isAdminOrPI(role)) return false;
        if (repoMode === FileOperationRepositoryMode.REQUIRED && files.some(it => !isRepository(it.path))) return false;
        if (repoMode === FileOperationRepositoryMode.DISALLOW && files.some(it => isRepository(it.path))) return false;

        if (fileOp.currentDirectoryMode === true && props.directory === undefined) return false;
        if (fileOp.currentDirectoryMode !== true && files.length === 0) return false;
        const filesInCallback = fileOp.currentDirectoryMode === true ? [props.directory!] : files;
        if (fileOp.disabled(filesInCallback, props.callback)) return false;
        return true;
    };

    const Operation = ({fileOp}: { fileOp: FileOperation }): JSX.Element | null => {
        const filesInCallback = fileOp.currentDirectoryMode === true ? [props.directory!] : files;
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
                onClick={() => fileOp.onClick(filesInCallback, props.callback)}
                ml={props.inDropdown ? "-17px" : undefined}
                mr={props.inDropdown ? "-17px" : undefined}
                pl={props.inDropdown ? "15px" : undefined}
                data-tag={`${fileOp.text}-action`}
                {...props}
            >
                {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon as IconName}/> : null}
                <span>{fileOp.text}</span>
            </As>
        );
    };

    const filteredButtons = buttons.filter(it => isLegalOperation(it));
    const filteredOptions = options.filter(it => isLegalOperation(it));
    if (filteredButtons.length === 0 && filteredOptions.length === 0) {
        return <Box width="38px"/>;
    }

    const content: JSX.Element[] =
        filteredButtons.map((op, i) => <Operation fileOp={op} key={i}/>)
            .concat(files.length === 0 || fileOperations.length === 1 || props.inDropdown ? [] :
                <div key="selected">
                    <TextSpan bold>{files.length} {files.length === 1 ? "file" : "files"} selected</TextSpan>
                </div>
            ).concat(filteredOptions.map((op, i) => <Operation fileOp={op} key={i + "_"}/>));

    const dataTag = files.length === 0 ? undefined : files.length === 1 ? files[0].path + "-dropdown" : "file-ops";

    return (props.inDropdown ?
            <Box>
                <ClickableDropdown
                    width="175px"
                    left="-160px"
                    trigger={(
                        <Icon
                            onClick={UF.preventDefault}
                            ml="5px"
                            mr="10px"
                            name="ellipsis"
                            size="1em"
                            data-tag={dataTag}
                            rotation={90}
                        />
                    )}
                >
                    {content}
                </ClickableDropdown>
            </Box> : <>{content}</>
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

    const Operation = ({quickLaunchApp}: { quickLaunchApp: QuickLaunchApp }): React.ReactElement => {
        return (
            <Flex
                cursor="pointer"
                alignItems="center"
                onClick={() => quickLaunchCallback(quickLaunchApp, FUtils.getParentPath(file.path), props.history)}
                width="auto"
                {...props}
            >
                <AppToolLogo name={quickLaunchApp.metadata.name} size="20px" type="APPLICATION"/>
                <span style={{marginLeft: "5px", marginRight: "5px"}}>{quickLaunchApp.metadata.title}</span>
            </Flex>
        );
    };

    return (
        <>
            {applications.map((ap, i) => <Operation quickLaunchApp={ap} key={i}/>)}
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
