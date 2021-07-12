import * as React from "react";
import {default as FileCollectionsApi, FileCollection} from "UCloud/FileCollectionsApi";
import {ResourceBrowse} from "Resource/Browse";
import {ResourceRouter} from "Resource/Router";

export const FileCollectionBrowse: React.FunctionComponent<{
    onSelect?: (selection: FileCollection) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    return <ResourceBrowse
        api={FileCollectionsApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        onInlineCreation={((text, product, cb) => ({
                product: {id: product.id, category: product.category.id, provider: product.category.provider},
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
