import * as React from "react";
import {default as FilesApi, UFile} from "UCloud/FilesApi";
import {ResourceBrowse} from "Resource/Browse";
import {ResourceRouter} from "Resource/Router";
import {useHistory, useLocation} from "react-router";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {useGlobal} from "Utilities/ReduxHooks";
import {useEffect, useMemo} from "react";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {getParentPath, pathComponents} from "Utilities/FileUtilities";
import {joinToString} from "UtilityFunctions";
import FileCollectionsApi, {FileCollection} from "UCloud/FileCollectionsApi";
import {useCloudAPI} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";

export const FilesBrowse: React.FunctionComponent<{
    onSelect?: (selection: UFile) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    const [, setUploadPath] = useGlobal("uploadPath", "/");
    const location = useLocation();
    const path = getQueryParamOrElse(location.search, "path", "/");
    const additionalFilters = useMemo((() => ({path})), [path]);
    const history = useHistory();
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);

    useEffect(() => {
        setUploadPath(path);
    }, [path]);

    useEffect(() => {
        const components = pathComponents(path);
        if (components.length >= 1) {
            const collectionId = components[0];

            if (collection.data?.id !== collectionId && !collection.loading) {
                fetchCollection(FileCollectionsApi.retrieve({id: collectionId}));
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

        return <>
            <BreadCrumbsBase embedded={props.embedded ?? false}>
                {breadcrumbs.map((it, idx) => (
                    <span key={it} test-tag={it} title={it} children={it}
                          onClick={() => {
                              FilesApi.navigateToFile(
                                  history,
                                  "/" + joinToString(components.slice(0, idx + 1), "/")
                              );
                          }}
                    />
                ))}
            </BreadCrumbsBase>
        </>;
    }, [path, props.embedded, collection.data]);

    return <ResourceBrowse
        api={FilesApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        onInlineCreation={((text, product, cb) => ({
                product: {id: product.id, category: product.category.id, provider: product.category.provider},
            })
        )}
        onRename={async (text, res, cb) => {
            await cb.invokeCommand(FilesApi.move(bulkRequestOf({
                conflictPolicy: "REJECT",
                oldId: res.id,
                newId: getParentPath(res.id) + text
            })));
        }}
        isSearch={props.isSearch}
        additionalFilters={additionalFilters}
        header={breadcrumbsComponent}
        headerSize={48}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={FilesApi} Browser={FilesBrowse}/>;
};

export default Router;
