import * as React from "react";
import {default as IngressApi, Ingress, IngressSupport} from "@/UCloud/IngressApi";
import {ResourceBrowse} from "@/Resource/Browse";

const Browse: React.FunctionComponent<{
    computeProvider?: string;
    onSelect?: (selection: Ingress) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    return <ResourceBrowse
        api={IngressApi}
        onSelect={props.onSelect}
        onInlineCreation={((text, product, cb) => ({
                product: {id: product.name, category: product.category.name, provider: product.category.provider},
                domain: text
            })
        )}
        inlinePrefix={p => (p.support as IngressSupport).domainPrefix}
        inlineSuffix={p => (p.support as IngressSupport).domainSuffix}
        isSearch={props.isSearch}
        embedded={props.embedded}
    />;
};
export default Browse;
