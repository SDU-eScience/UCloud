import * as React from "react";
import {default as LicenseApi, License} from "@/UCloud/LicenseApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import {ResourceTab, ResourceTabOptions} from "@/Resource/ResourceTabs";

export const LicenseBrowse: React.FunctionComponent<{
    tagged?: string[];
    onSelect?: (selection: License) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    return <ResourceBrowse
        api={LicenseApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        header={
            <ResourceTab active={ResourceTabOptions.LICENSES} />
        }
        headerSize={48}
        onInlineCreation={(text, product, cb) => ({
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
