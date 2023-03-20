import * as React from "react";
import {default as IngressApi, Ingress, IngressSupport} from "@/UCloud/IngressApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceOptions} from "@/Resource/ResourceOptions";
import {BrowseType} from "@/Resource/BrowseType";


const Browse: React.FunctionComponent<{
    computeProvider?: string;
    onSelect?: (selection: Ingress) => void;
    isSearch?: boolean;
    browseType?: BrowseType;
    onSelectRestriction?: (res: Ingress) => boolean | string;
    additionalFilters: Record<string, string>;
}> = props => {
    const browseType = props.browseType ?? BrowseType.MainContent;
    return <ResourceBrowse
        api={IngressApi}
        onSelect={props.onSelect}
        onInlineCreation={(text, product, cb) => ({
            product: {id: product.name, category: product.category.name, provider: product.category.provider},
            domain: text
        })}
        header={
            browseType === BrowseType.MainContent ? <>{ResourceOptions.PUBLIC_LINKS}</> : undefined
        }
        headerSize={48}
        onSelectRestriction={props.onSelectRestriction}
        additionalFilters={props.additionalFilters}
        inlinePrefix={p => (p.support as IngressSupport).domainPrefix}
        inlineSuffix={p => (p.support as IngressSupport).domainSuffix}
        isSearch={props.isSearch}
        browseType={props.browseType ?? BrowseType.MainContent}
    />;
};
export default Browse;
