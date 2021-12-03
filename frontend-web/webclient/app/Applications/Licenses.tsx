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
    browseType?: BrowseType;
    additionalFilters?: Record<string, string>;
    onSelectRestriction?: (res: License) => boolean;
}> = props => {
    const browseType = props.browseType ?? BrowseType.MainContent;
    return <ResourceBrowse
        api={LicenseApi}
        onSelect={props.onSelect}
        browseType={props.browseType ?? BrowseType.MainContent}
        header={
            browseType === BrowseType.MainContent ? (
                <ResourceTab active={ResourceTabOptions.LICENSES} />) : undefined
        }
        headerSize={48}
        onSelectRestriction={props.onSelectRestriction}
        additionalFilters={props.additionalFilters}
        onInlineCreation={(text, product) => ({
            product: {id: product.name, category: product.category.name, provider: product.category.provider},
            domain: text
        })}
        inlineCreationMode={"NONE"}
        isSearch={props.isSearch}
        emptyPage={
            <>
                <p>No licenses match your current search/filter criteria.</p>

                <p>
                    You might not have activated any licenses yet. If you have already applied or received a license,
                    then you must first activate it, before you can use it. Click the &quot;Activate license&quot;
                    button to continue.
                </p>
            </>
        }
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={LicenseApi} Browser={LicenseBrowse} />;
};

export default Router;
