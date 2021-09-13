import * as React from "react";
import {default as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import {useCallback, useMemo} from "react";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {bulkRequestOf} from "@/DefaultObjects";

export const FileCollectionBrowse: React.FunctionComponent<{
    onSelect?: (selection: FileCollection) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    const onRename = useCallback(async (text: string, res: FileCollection, cb: ResourceBrowseCallbacks<FileCollection>) => {
        await cb.invokeCommand(FileCollectionsApi.rename(bulkRequestOf({
            id: res.id,
            newTitle: text
        })));
    }, []);

    return <ResourceBrowse
        api={FileCollectionsApi}
        onSelect={props.onSelect}
        onRename={onRename}
        embedded={props.embedded}
        onInlineCreation={((text, product, cb) => ({
                product: {id: product.name, category: product.category.name, provider: product.category.provider},
                title: text
            })
        )}
        navigateToChildren={FileCollectionsApi.navigateToChildren}
        isSearch={props.isSearch}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={FileCollectionsApi} Browser={FileCollectionBrowse}/>;
};

export default Router;
