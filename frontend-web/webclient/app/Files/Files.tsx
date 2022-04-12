import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {api as FilesApi, UFile, UFileIncludeFlags} from "@/UCloud/FilesApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {ResourceRouter} from "@/Resource/Router";
import {useHistory, useLocation} from "react-router";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {getParentPath, pathComponents} from "@/Utilities/FileUtilities";
import {isLightThemeStored, joinToString, removeTrailingSlash} from "@/UtilityFunctions";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPage, emptyPageV2} from "@/DefaultObjects";
import * as H from "history";
import {ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {Box, Flex, Icon, List, Text} from "@/ui-components";
import {PageV2} from "@/UCloud";
import {ListV2, List as ListV1} from "@/Pagination";
import styled from "styled-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {FilesSearchTabs} from "@/Files/FilesSearchTabs";
import {UserInProject, ListProjectsRequest, listProjects} from "@/Project";
import {Client} from "@/Authentication/HttpClientInstance";

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

    const lightTheme = isLightThemeStored();

    const browseType = props.browseType ?? BrowseType.MainContent;
    const [, setUploadPath] = useGlobal("uploadPath", "/");
    const location = useLocation();
    const pathFromQuery = getQueryParamOrElse(location.search, "path", "/");
    const [pathFromState, setPathFromState] = useState(
        browseType !== BrowseType.Embedded ? pathFromQuery : props.pathRef?.current ?? pathFromQuery
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
    const history = useHistory();
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [directory, fetchDirectory] = useCloudAPI<UFile | null>({noop: true}, null);

    const [localActiveProject, setLocalActiveProject] = useState(Client.projectId ?? "");

    // We need to be able to await the call.
    const [drives, setDrives] = useState<PageV2<FileCollection>>(emptyPageV2);
    const [loading, invokeCommand] = useCloudCommand();

    useEffect(() => {
        invokeCommand(FileCollectionsApi.browse({itemsPerPage: 250, filterMemberFiles: "all"} as any)).then(
            it => setDrives(it)
        );
    }, []);

    const [projects, fetchProjects] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 25, archived: false}),
        emptyPage
    );


    const viewPropertiesInline = useCallback((file: UFile): boolean =>
        browseType === BrowseType.Embedded &&
        props.forceNavigationToPage !== true,
        []
    );

    const navigateToPath = useCallback((history: H.History, path: string) => {
        if (browseType === BrowseType.Embedded && !props.forceNavigationToPage) {
            setPathFromState(path);
        } else {
            history.push(buildQueryString("/files", {path}));
        }
    }, [browseType, props.forceNavigationToPage]);

    const navigateToFile = useCallback((history: H.History, file: UFile): "properties" | void => {
        if (file.status.type === "DIRECTORY") {
            navigateToPath(history, file.id);
        } else {
            return "properties";
        }
    }, [navigateToPath]);

    useEffect(() => {
        if (browseType !== BrowseType.Embedded) setUploadPath(path);
        if (props.pathRef) props.pathRef.current = path;
    }, [path, browseType, props.pathRef]);

    useEffect(() => {
        if (browseType !== BrowseType.MainContent) {
            if (path === "" && drives.items.length > 0) {
                setPathFromState("/" + drives.items[0].id);
            }
        }
    }, [browseType, path, drives.items]);

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

    useEffect(() => {
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

    const headerComponent = useMemo((): JSX.Element => {
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
            {props.isSearch !== true || browseType != BrowseType.MainContent ? null : <FilesSearchTabs active={"FILES"} />}
            {browseType !== BrowseType.Embedded ? null : <Flex>
                <DriveDropdown iconName="projects">
                    <ListV1
                        loading={projects.loading}
                        onPageChanged={newPage => fetchProjects(listProjects({
                            itemsPerPage: projects.data.itemsPerPage,
                            page: newPage
                        }))}
                        page={projects.data}
                        pageRenderer={page => (
                            <>
                                <List childPadding={"8px"} bordered={false}>
                                    <DriveInDropdown
                                        className="expandable-row-child"
                                        onClick={() => selectLocalProject("")}
                                    >
                                        My Workspace
                                    </DriveInDropdown>
                                    {page.items.filter(it => it.projectId !== localActiveProject).map(project => (
                                        <DriveInDropdown
                                            key={project.projectId}
                                            className="expandable-row-child"
                                            onClick={() => selectLocalProject(project.projectId)}
                                        >
                                            {project.title}
                                        </DriveInDropdown>

                                    ))}
                                </List>
                            </>
                        )}
                    />
                </DriveDropdown>
                <Text fontSize="25px">
                    {localActiveProject === "" ? "My Workspace" : (projects.data.items.find(it => it.projectId === localActiveProject)?.title ?? "")}
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
    }, [path, browseType, collection.data, drives.items, projects.data.items, lightTheme, localActiveProject]);

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
    }), [collection.data, directory.data]);

    return <ResourceBrowse
        api={FilesApi}
        onSelect={props.onSelect}
        onSelectRestriction={props.onSelectRestriction}
        browseType={browseType}
        inlineProduct={collection.data?.status.resolvedSupport?.product}
        onInlineCreation={onInlineCreation}
        onRename={onRename}
        emptyPage={
            <>No files found. Click &quot;Create folder&quot; or &quot;Upload files&quot;.</>
        }
        isSearch={props.isSearch}
        additionalFilters={additionalFilters}
        header={headerComponent}
        headerSize={75}
        navigateToChildren={navigateToFile}
        extraCallbacks={callbacks}
        viewPropertiesInline={viewPropertiesInline}
        showCreatedBy={false}
        showProduct={false}
        shouldFetch={shouldFetch}
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
