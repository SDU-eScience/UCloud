import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {api as FilesApi, UFile, UFileIncludeFlags} from "@/UCloud/FilesApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {ResourceRouter} from "@/Resource/Router";
import {useHistory, useLocation} from "react-router";
import {buildQueryString, getQueryParam, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {getParentPath, pathComponents} from "@/Utilities/FileUtilities";
import {
    defaultErrorHandler, doNothing, inDevEnvironment,
    isLightThemeStored,
    joinToString, onDevSite,
    randomUUID,
    removeTrailingSlash,
} from "@/UtilityFunctions";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPage, emptyPageV2} from "@/DefaultObjects";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {Box, Button, Flex, Icon, Link, List, Text} from "@/ui-components";
import {PageV2, compute, provider, accounting} from "@/UCloud";
import {ListV2, List as ListV1} from "@/Pagination";
import styled from "styled-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {FilesSearchTabs} from "@/Files/FilesSearchTabs";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {SyncthingConfig, SyncthingConfigResponse} from "@/Syncthing/api";
import * as Sync from "@/Syncthing/api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {setLoading} from "@/Navigation/Redux/StatusActions";
import {useDispatch} from "react-redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import ProjectAPI, {Project} from "@/Project/Api";

export const FilesBrowse: React.FunctionComponent<{
    onSelect?: (selection: UFile) => void;
    additionalFilters?: UFileIncludeFlags;
    onSelectRestriction?: (res: UFile) => boolean;
    isSearch?: boolean;
    browseType?: BrowseType;
    pathRef?: React.MutableRefObject<string>;
    forceNavigationToPage?: boolean;
    allowMoveCopyOverride?: boolean;
}> = props => {
    const shouldUseStreamingSearch = onDevSite() || inDevEnvironment();

    // Input parameters and configuration
    const lightTheme = isLightThemeStored();

    const location = useLocation();
    const history = useHistory();
    const dispatch = useDispatch();

    const browseType = props.browseType ?? BrowseType.MainContent;
    const [uploadPath, setUploadPath] = useGlobal("uploadPath", "/");
    const pathFromQuery = getQueryParamOrElse(location.search, "path", "/");
    // TODO(Dan): This should probably be passed down as a search parameter instead.
    const searchQuery = props.isSearch ? getQueryParamOrElse(location.search, "q", "") : null;
    const searchContext = getQueryParam(location.search, "searchContext");

    // UI state
    const didUnmount = useDidUnmount();
    //const [syncthingConfig, fetchSyncthingConfig] = useCloudAPI<SyncthingConfigResponse | null>({noop: true}, null);
    const [syncthingConfig, setSyncthingConfig] = useState<SyncthingConfig | null>(null);
    const [syncthingProduct, setSyncthingProduct] = useState<compute.ComputeProductSupportResolved | null>(null);
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [directory, fetchDirectory] = useCloudAPI<UFile | null>({noop: true}, null);
    const [drives, setDrives] = useState<PageV2<FileCollection>>(emptyPageV2);
    const [projects] = useCloudAPI<PageV2<Project>>(
        ProjectAPI.browse({itemsPerPage: 25, includeArchived: false}),
        emptyPage
    );

    const [searchResults, setSearchResults] = useState<UFile[] | undefined>(undefined);

    const [localActiveProject, setLocalActiveProject] = useState(Client.projectId ?? "");
    const [pathFromState, setPathFromState] = useState(browseType !== BrowseType.Embedded ?
        pathFromQuery :
        props.pathRef?.current ?? pathFromQuery
    );
    const path = browseType === BrowseType.Embedded ? pathFromState : pathFromQuery;
    const shouldFetch = useCallback(() => path.length > 0, [path]);
    const additionalFilters = useMemo((() => {
        const base = {
            path, includeMetadata: "true",
        };

        if (props.additionalFilters != null) {
            Object.keys(props.additionalFilters).forEach(it => {
                base[it] = props.additionalFilters![it].toString();
            });
        }

        return base;
    }), [path, props.additionalFilters]);


    // UI callbacks and state manipulation
    const [loading, invokeCommand] = useCloudCommand();

    const viewPropertiesInline = useCallback((file: UFile): boolean =>
        browseType === BrowseType.Embedded &&
        props.forceNavigationToPage !== true,
        []
    );

    const navigateToPath = useCallback((history: ReturnType<typeof useHistory>, path: string) => {
        if (browseType === BrowseType.Embedded && !props.forceNavigationToPage) {
            setPathFromState(path);
        } else {
            history.push(buildQueryString("/files", {path}));
        }
    }, [browseType, props.forceNavigationToPage]);

    const navigateToFile = useCallback((history: ReturnType<typeof useHistory>, file: UFile): "properties" | void => {
        if (file.status.type === "DIRECTORY") {
            navigateToPath(history, file.id);
        } else {
            return "properties";
        }
    }, [navigateToPath]);

    const setSynchronization = useCallback((file: UFile, shouldAdd: boolean) => {
        setSyncthingConfig((config) => {
            if (!config) return config;
            const newConfig = deepCopy(config);
            
            const folders = newConfig?.folders ?? []

            if (shouldAdd) {
                const newFolders = [...folders];
                newConfig.folders = newFolders;

                if (newFolders.every(it => it.ucloudPath !== file.id)) {
                    newFolders.push({id: randomUUID(), ucloudPath: file.id});
                }
            } else {
                newConfig.folders = folders.filter(it => it.ucloudPath !== file.id);
            }

            if (!collection.data?.specification.product.provider) return config;

            invokeCommand(Sync.api.updateConfiguration({
                providerId: collection.data?.specification.product.provider,
                category: "syncthing",
                config: newConfig
            })).catch(e => {
                if (didUnmount.current) return;
                defaultErrorHandler(e);
                setSyncthingConfig(config);
            });
            return newConfig;
        });
    }, [collection.data?.specification.product.provider]);

    const selectLocalProject = useCallback(async (projectOverride: string) => {
        const result = await invokeCommand<PageV2<FileCollection>>({
            ...FileCollectionsApi.browse({
                itemsPerPage: drives.itemsPerPage,
                filterMemberFiles: "all"
            } as any), projectOverride
        });
        if (result != null) {
            setDrives(result);
            setLocalActiveProject(projectOverride);
            setPathFromState("");
        }
    }, [drives]);

    const onRename = useCallback(async (text: string, res: UFile, cb: ResourceBrowseCallbacks<UFile>) => {
        await cb.invokeCommand(FilesApi.move(bulkRequestOf({
            conflictPolicy: "REJECT",
            oldId: res.id,
            newId: getParentPath(res.id) + text
        })));
    }, []);

    const onInlineCreation = useCallback((text: string) => {
        return FilesApi.createFolder(bulkRequestOf({
            id: removeTrailingSlash(path) + "/" + text,
            conflictPolicy: "RENAME"
        }));
    }, [path]);

    const callbacks = useMemo(() => ({
        collection: collection?.data ?? undefined,
        allowMoveCopyOverride: props.allowMoveCopyOverride,
        directory: directory?.data ?? undefined,
        syncthingConfig: syncthingConfig ?? undefined,
        setSynchronization
    }), [collection.data, directory.data, syncthingConfig]);

    // Effects
    useEffect(() => {
        // NOTE(Dan): We load relevant file collections (for the header) only on load
        invokeCommand(FileCollectionsApi.browse({itemsPerPage: 250, filterMemberFiles: "all"} as any))
            .then(it => setDrives(it));
    }, []);

    useEffect(() => {
        // NOTE(Dan): Load relevant synchronization configuration. We don't currently reload any of this information,
        // but maybe we should.
        if (!collection.data?.specification.product.provider) return;

        Sync.fetchProducts(collection.data.specification.product.provider).then(products => {
            if (!products) return;
            if (!collection.data?.specification.product.provider) return;
            const product = products.find(it => it.product.category.name === "syncthing") ?? null;
            if (!product) return;
            setSyncthingProduct(product);
        });
    }, [collection.data, localActiveProject]);


    useEffect(() => {
        if (!syncthingProduct) return;
        if (didUnmount.current) return;
        Sync.fetchConfig(syncthingProduct.product.category.provider).then(config => {
            setSyncthingConfig(config);
        });
        /*if (!syncthingConfig.loading) {
            fetchSyncthingConfig(Sync.api.retrieveConfiguration(syncthingProduct.product.category.provider, "syncthing"));
        }*/
    }, [collection.data, path, localActiveProject, syncthingProduct]);

    useEffect(() => {
        // NOTE(Dan): The uploader component, triggered by one of the operations, need to know about the current folder.
        // We store this in global application state and set it if we are rendering a main UI.
        if (browseType !== BrowseType.Embedded && !props.isSearch) setUploadPath(path);
        if (props.pathRef) props.pathRef.current = path;
    }, [path, browseType, props.pathRef, props.isSearch]);

    useEffect(() => {
        // NOTE(Dan): Trigger a redirect to an appropriate drive, if no path was supplied to us. This typically happens
        // when displaying a file selector.
        if (browseType !== BrowseType.MainContent) {
            if (path === "" && drives.items.length > 0) {
                setPathFromState("/" + drives.items[0].id);
            }
        }
    }, [browseType, path, drives.items]);

    useEffect(() => {
        // NOTE(Dan): Fetch information about the current directory and associated drive when the path/project changes.
        const components = pathComponents(path);
        if (path.length > 0) {
            if (components.length >= 1) {
                const collectionId = components[0];

                if (collection.data?.id !== collectionId && !collection.loading) {
                    fetchCollection({
                        ...FileCollectionsApi.retrieve({id: collectionId, includeSupport: true}),
                        projectOverride: localActiveProject
                    });
                }
            }
            fetchDirectory({...FilesApi.retrieve({id: path}), projectOverride: localActiveProject})
        }
    }, [path, localActiveProject]);

    useEffect(() => {
        // NOTE(Dan): The search context is currently derived from the uploadPath global. After a refresh on the
        // search page we have the searchContext but uploadPath is not set. This effect sets the upload path such that
        // subsequent searches will still have the correct context.
        if (!props.isSearch) return;
        if (!searchContext) return;
        if (uploadPath === "/") setUploadPath(searchContext);
    }, [uploadPath, searchContext, props.isSearch])

    useEffect(() => {
        if (!props.isSearch) return;
        if (!searchContext && uploadPath) {
            history.replace(`${location.pathname}${location.search}&searchContext=${encodeURIComponent(uploadPath)}`);
        }
    }, [props.isSearch, location.search, location.pathname]);

    useEffect(() => {
        // NOTE(Dan): Reset search results between different queries.
        if (!props.isSearch) return;
        setSearchResults(undefined);
    }, [searchQuery]);

    useEffect(() => {
        if (!shouldUseStreamingSearch) return;

        if (props.isSearch === true) {
            const connection = WSFactory.open(
                "/files",
                {
                    reconnect: false,
                    init: async (conn) => {
                        dispatch(setLoading(true));
                        try {
                            await conn.subscribe({
                                call: "files.streamingSearch",
                                payload: {
                                    query: searchQuery,
                                    flags: {},
                                    currentFolder: searchContext ?? uploadPath
                                },
                                handler: (message) => {
                                    if (message.payload["type"] === "result") {
                                        const files = message.payload["batch"] as UFile[];
                                        setSearchResults(prev => {
                                            const previous = prev ?? [];
                                            return [...previous, ...files];
                                        });
                                    }
                                }
                            });
                        } finally {
                            dispatch(setLoading(false));
                        }
                    },
                }
            );

            return () => {
                connection.close();
                dispatch(setLoading(false));
            };
        } else {
            setSearchResults(undefined);
            return doNothing;
        }
    }, [props.isSearch, searchQuery]);

    const headerComponent = useMemo((): JSX.Element => {
        if (props.isSearch) return <FilesSearchTabs active={"FILES"} />;
        const components = pathComponents(path);
        let breadcrumbs: string[] = [];
        if (components.length >= 1) {
            if (collection.data !== null) {
                breadcrumbs.push(collection.data.specification.title)
                for (let i = 1; i < components.length; i++) {
                    breadcrumbs.push(components[i]);
                }
            }
        } else {
            breadcrumbs = components;
        }

        return <Box backgroundColor={getCssVar("white")}>
            {browseType !== BrowseType.Embedded ? null : <Flex>
                <DriveDropdown iconName="projects">
                    <ListV2
                        loading={projects.loading}
                        onLoadMore={() => ProjectAPI.browse({itemsPerPage: 25, includeArchived: false, next: projects.data.next})}
                        page={projects.data}
                        pageRenderer={items => (
                            <>
                                <List childPadding={"8px"} bordered={false}>
                                    <DriveInDropdown
                                        className="expandable-row-child"
                                        onClick={() => selectLocalProject("")}
                                    >
                                        My Workspace
                                    </DriveInDropdown>
                                    {items.filter(it => it.id !== localActiveProject).map(project => (
                                        <DriveInDropdown
                                            key={project.id}
                                            className="expandable-row-child"
                                            onClick={() => selectLocalProject(project.id)}
                                        >
                                            {project.specification.title}
                                        </DriveInDropdown>

                                    ))}
                                </List>
                            </>
                        )}
                    />
                </DriveDropdown>
                <Text fontSize="25px">
                    {localActiveProject === "" ? "My Workspace" : (projects.data.items.find(it => it.id === localActiveProject)?.specification.title ?? "")}
                </Text>
            </Flex>}
            <Flex>
                <DriveDropdown iconName="hdd">
                    <ListV2
                        loading={loading}
                        onLoadMore={() => invokeCommand(FileCollectionsApi.browse({
                            itemsPerPage: 25,
                            next: drives.next,
                            filterMemberFiles: "all"
                        } as any)).then(page => setDrives(page)).catch(e => console.log(e))}
                        page={drives}
                        pageRenderer={items => (
                            <List childPadding={"8px"} bordered={false}>
                                {items.map(drive => (
                                    <DriveInDropdown
                                        key={drive.id}
                                        className="expandable-row-child"
                                        onClick={() => navigateToPath(history, `/${drive.id}`)}
                                    >
                                        {drive.specification?.title}
                                    </DriveInDropdown>
                                ))}
                            </List>
                        )}
                    />
                </DriveDropdown>
                <BreadCrumbsBase embedded={browseType === BrowseType.Embedded}
                    className={browseType == BrowseType.MainContent ? "isMain" : undefined}>
                    {breadcrumbs.map((it, idx) => (
                        <span data-component={"crumb"} key={it} test-tag={it} title={it}
                            onClick={() => {
                                navigateToPath(
                                    history,
                                    "/" + joinToString(components.slice(0, idx + 1), "/")
                                );
                            }}
                        >
                            {it}
                        </span>
                    ))}
                </BreadCrumbsBase>
            </Flex>
        </Box>;
    }, [path, browseType, collection.data, drives.items, projects.data.items, lightTheme, localActiveProject, props.isSearch]);

    return <ResourceBrowse
        api={FilesApi}
        onSelect={props.onSelect}
        onSelectRestriction={props.onSelectRestriction}
        browseType={browseType}
        inlineProduct={collection.data?.status.resolvedSupport?.product}
        onInlineCreation={onInlineCreation}
        onRename={onRename}
        emptyPage={
            <>
                {props.isSearch ?
                    <>
                        UCloud is currently searching for files. This search may use your current folder as a starting
                        point. If you do not get any results, try to select a different folder before starting your
                        search.
                    </> :
                    <>
                        No files found. Click &quot;Create folder&quot; or &quot;Upload files&quot;.
                    </>
                }
            </>
        }
        isSearch={props.isSearch}
        additionalFilters={additionalFilters}
        header={headerComponent}
        headerSize={props.isSearch ? 48 : 75}
        navigateToChildren={navigateToFile}
        extraCallbacks={callbacks}
        viewPropertiesInline={viewPropertiesInline}
        showCreatedBy={false}
        showProduct={false}
        shouldFetch={shouldFetch}
        resources={searchResults}
        extraSidebar={
            <>
                <Box flexGrow={1} />
                {!syncthingConfig ? null :
                    <Link to={`/syncthing?provider=${collection.data?.specification.product.provider}`}>
                        <Button>Manage synchronization (BETA)</Button>
                    </Link>
                }
            </>
        }
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter
        api={FilesApi}
        Browser={FilesBrowse}
    />;
};

const DriveDropdown: React.FunctionComponent<{iconName: "hdd" | "projects"}> = props => {
    return (
        <ClickableDropdown
            colorOnHover={false}
            paddingControlledByContent={true}
            width={"450px"}
            trigger={<div style={{display: "flex"}}>
                <Icon mt="8px" mr="6px" name={props.iconName} color2="white" size="24px" />
                <Icon
                    size="12px"
                    mr="8px"
                    mt="15px"
                    name="chevronDownLight"
                />
            </div>}
        >
            {props.children}
        </ClickableDropdown>
    );
}

const DriveInDropdown = styled.div`
  padding: 0 17px;
  width: 450px;
  overflow-x: hidden;

  &:hover {
    background-color: var(--lightBlue);
  }
`;

export default Router;
