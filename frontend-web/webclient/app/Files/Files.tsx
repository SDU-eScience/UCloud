import * as React from "react";
import {default as FilesApi, UFile} from "@/UCloud/FilesApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import {useHistory, useLocation} from "react-router";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useCallback, useEffect, useMemo, useState} from "react";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {getParentPath, pathComponents} from "@/Utilities/FileUtilities";
import {joinToString, removeTrailingSlash} from "@/UtilityFunctions";
import FileCollectionsApi, {FileCollection} from "@/UCloud/FileCollectionsApi";
import {useCloudAPI} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import * as H from "history";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {Flex, Icon, List} from "@/ui-components";
import {PageV2} from "@/UCloud";
import {ListV2} from "@/Pagination";
import styled from "styled-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";

export const FilesBrowse: React.FunctionComponent<{
    onSelect?: (selection: UFile) => void;
    isSearch?: boolean;
    embedded?: boolean;
    pathRef?: React.MutableRefObject<string>;
    forceNavigationToPage?: boolean;
}> = props => {
    const [, setUploadPath] = useGlobal("uploadPath", "/");
    const location = useLocation();
    const pathFromQuery = getQueryParamOrElse(location.search, "path", "/");
    const [pathFromState, setPathFromState] = useState(
        props.embedded !== true ? pathFromQuery : props.pathRef?.current ?? pathFromQuery
    );
    const path = props.embedded === true ? pathFromState : pathFromQuery;
    const additionalFilters = useMemo((() => ({path, includeMetadata: "true"})), [path]);
    const history = useHistory();
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [directory, fetchDirectory] = useCloudAPI<UFile | null>({noop: true}, null);

    const [drives, fetchDrives] = useCloudAPI<PageV2<FileCollection>>(
        FileCollectionsApi.browse({itemsPerPage: 10}), emptyPageV2
    );

    const viewPropertiesInline = useCallback((file: UFile): boolean =>
        props.embedded === true && props.forceNavigationToPage !== true,
        []
    );

    const navigateToPath = useCallback((history: H.History, path: string) => {
        if (props.embedded === true && !props.forceNavigationToPage) {
            setPathFromState(path);
        } else {
            history.push(buildQueryString("/files", {path: path}));
        }
    }, [props.embedded, props.forceNavigationToPage]);

    const navigateToFile = useCallback((history: H.History, file: UFile): "properties" | void => {
        if (file.status.type === "DIRECTORY") {
            navigateToPath(history, file.id);
        } else {
            return "properties";
        }
    }, [navigateToPath]);

    useEffect(() => {
        if (props.embedded !== true) setUploadPath(path);
        if (props.pathRef) props.pathRef.current = path;
    }, [path, props.embedded, props.pathRef]);

    useEffect(() => {
        const components = pathComponents(path);
        if (components.length >= 1) {
            const collectionId = components[0];

            if (collection.data?.id !== collectionId && !collection.loading) {
                fetchCollection(FileCollectionsApi.retrieve({id: collectionId, includeSupport: true}));
            }
        }
        fetchDirectory(FilesApi.retrieve({id: path}))
    }, [path]);

    const breadcrumbsComponent = useMemo((): JSX.Element => {
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

        return <Flex>
            <DriveDropdown>
                <ListV2
                    loading={drives.loading}
                    onLoadMore={() => fetchDrives(FileCollectionsApi.browse({
                        itemsPerPage: drives.data.itemsPerPage,
                        next: drives.data.next
                    }))}
                    page={drives.data}
                    pageRenderer={items => {
                        return (
                            <>
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
                            </>
                        );
                    }}
                />
            </DriveDropdown>
            <BreadCrumbsBase embedded={props.embedded ?? false}>
                {breadcrumbs.map((it, idx) => (
                    <span key={it} test-tag={it} title={it}
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
        </Flex>;
    }, [path, props.embedded, collection.data, drives.data]);

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
        directory: directory?.data ?? undefined
    }), [collection.data, directory.data]);

    return <ResourceBrowse
        api={FilesApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        inlineProduct={collection.data?.status.resolvedSupport?.product}
        onInlineCreation={onInlineCreation}
        onRename={onRename}
        emptyPage={<>
            No files found. Click &quot;Create folder&quot; or &quot;Upload files&quot;.
        </>}
        isSearch={props.isSearch}
        additionalFilters={additionalFilters}
        header={breadcrumbsComponent}
        headerSize={48}
        navigateToChildren={navigateToFile}
        extraCallbacks={callbacks}
        viewPropertiesInline={viewPropertiesInline}
        showCreatedBy={false}
        showProduct={false}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter
        api={FilesApi}
        Browser={FilesBrowse}
    />;
};

const DriveDropdown: React.FunctionComponent = props => {
    return (
        <ClickableDropdown
            colorOnHover={false}
            paddingControlledByContent={true}
            width={"450px"}
            trigger={<div style={{display: "flex"}}>
                <Icon mt="8px" mr="6px" name="hdd" size="24px" />
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
