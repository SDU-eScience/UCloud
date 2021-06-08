import * as React from "react";
import {default as IngressApi, Ingress, IngressSupport} from "UCloud/IngressApi";
import {ResourceBrowse} from "Resource/Browse";
import {Icon} from "ui-components";
import {doNothing} from "UtilityFunctions";

const Browse: React.FunctionComponent<{
    computeProvider?: string;
    onSelect?: (selection: Ingress) => void
}> = props => {
    return <ResourceBrowse
        api={IngressApi}
        onSelect={props.onSelect}
        onInlineCreation={((text, product, cb) => ({
                product: {id: product.id, category: product.category.id, provider: product.category.provider},
                domain: text
            })
        )}
        inlinePrefix={p => (p.support as IngressSupport).domainPrefix}
        inlineSuffix={p => (p.support as IngressSupport).domainSuffix}
    />;
};
export default Browse;
