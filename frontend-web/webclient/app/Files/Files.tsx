import * as React from "react";
import {default as FilesApi, UFile} from "UCloud/FilesApi";
import {ResourceBrowse} from "Resource/Browse";
import {ResourceRouter} from "Resource/Router";
import {useHistory, useLocation} from "react-router";
import {buildQueryString, getQueryParamOrElse} from "Utilities/URIUtilities";
import {useGlobal} from "Utilities/ReduxHooks";
import {useCallback, useEffect, useMemo, useState} from "react";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {getParentPath, pathComponents} from "Utilities/FileUtilities";
import {joinToString, removeTrailingSlash} from "UtilityFunctions";
import FileCollectionsApi, {FileCollection} from "UCloud/FileCollectionsApi";
import {useCloudAPI} from "Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "DefaultObjects";
import * as H from "history";
import {ResourceBrowseCallbacks} from "UCloud/ResourceApi";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Flex, Icon} from "ui-components";
import {PageV2} from "UCloud";
import {ListV2} from "Pagination";

export const FilesBrowse: React.FunctionComponent<{
    onSelect?: (selection: UFile) => void;
    isSearch?: boolean;
    embedded?: boolean;
    pathRef?: React.MutableRefObject<string>;
}> = props => {
    const [, setUploadPath] = useGlobal("uploadPath", "/");
    const location = useLocation();
    const pathFromQuery = getQueryParamOrElse(location.search, "path", "/");
    const [pathFromState, setPathFromState] = useState(pathFromQuery);
    const path = props.embedded === true ? pathFromState : pathFromQuery;
    const additionalFilters = useMemo((() => ({path, includeMetadata: "true"})), [path]);
    const history = useHistory();
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [drives, fetchDrives] = useCloudAPI<PageV2<FileCollection>>(
        FileCollectionsApi.browse({itemsPerPage: 10}), emptyPageV2
    );

    const navigateToPath = useCallback((history: H.History, path: string) => {
        if (props.embedded === true) {
            setPathFromState(path);
        } else {
            history.push(buildQueryString("/files", {path: path}));
        }
    }, [props.embedded]);

    const navigateToFile = useCallback((history: H.History, file: UFile) => {
        if (file.status.type === "DIRECTORY") {
            navigateToPath(history, file.id);
        } else if (!props.embedded) {
            history.push(`/${FilesApi.routingNamespace}/properties/${encodeURIComponent(file.id)}`);
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
            {!props.embedded ? null : (
                <ClickableDropdown colorOnHover={false} trigger={<Icon mt="8px" mr="6px" name="hdd" />}>
                    <ListV2
                        loading={drives.loading}
                        onLoadMore={() => fetchDrives(FileCollectionsApi.browse({itemsPerPage: drives.data.itemsPerPage, next: drives.data.next}))}
                        page={drives.data}
                        pageRenderer={items => (
                            items.filter(c => c.specification?.title !== collection.data?.specification.title).map((c, index) => (
                                <div key={index} onClick={() => navigateToPath(history, `/${c.id}`)}>{c.specification?.title}</div>
                            )))}
                    />
                </ClickableDropdown>
            )}
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
    }, [path, props.embedded, collection.data]);

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
    }, []);

    const callbacks = useMemo(() => ({
        collection: collection?.data ?? undefined
    }), [collection.data]);

    return <ResourceBrowse
        api={FilesApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        inlineProduct={collection.data?.status.resolvedSupport?.product}
        onInlineCreation={onInlineCreation}
        onRename={onRename}
        isSearch={props.isSearch}
        additionalFilters={additionalFilters}
        header={breadcrumbsComponent}
        headerSize={48}
        navigateToChildren={navigateToFile}
        extraCallbacks={callbacks}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter
        api={FilesApi}
        Browser={FilesBrowse}
    />;
};

export default Router;
