import * as React from "react";
import {default as NetworkIPApi, NetworkIP} from "@/UCloud/NetworkIPApi";
import {BrowseType, ResourceBrowse} from "@/Resource/Browse";
import {ResourceTab, ResourceTabOptions} from "@/Resource/ResourceTabs";

export const NetworkIPBrowse: React.FunctionComponent<{
    provider?: string;
    onSelect?: (selection: NetworkIP) => void;
    isSearch?: boolean;
    browseType: BrowseType;
}> = props => {
    return <ResourceBrowse
        api={NetworkIPApi}
        onSelect={props.onSelect}
        onInlineCreation={(text, product, cb) => ({
            product: {id: product.name, category: product.category.name, provider: product.category.provider},
        })}
        header={
            <ResourceTab active={ResourceTabOptions.PUBLIC_IP} />
        }
        headerSize={48}
        inlineCreationMode={"NONE"}
        browseType={props.browseType}
        isSearch={props.isSearch}
    />;
};
