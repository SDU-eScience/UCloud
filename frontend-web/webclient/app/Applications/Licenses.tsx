import * as React from "react";
import {default as LicenseApi, License} from "@/UCloud/LicenseApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import {ResourceTab, ResourceTabOptions} from "@/Resource/ResourceTabs";
import {BrowseType} from "@/Resource/BrowseType";

export const LicenseBrowse: React.FunctionComponent<{
    tagged?: string[];
    onSelect?: (selection: License) => void;
    isSearch?: boolean;
    browseType: BrowseType;
}> = props => {
    return <ResourceBrowse
        api={LicenseApi}
        onSelect={props.onSelect}
        browseType={props.browseType}
        header={
            <ResourceTab active={ResourceTabOptions.LICENSES} />
        }
        headerSize={48}
        onInlineCreation={(text, product) => ({
            product: {id: product.name, category: product.category.name, provider: product.category.provider},
            domain: text
        })}
        inlineCreationMode={"NONE"}
        isSearch={props.isSearch}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={LicenseApi} Browser={LicenseBrowse} />;
};

export default Router;
