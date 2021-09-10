import * as React from "react";
import {ResourceBrowse} from "Resource/Browse";
import {ResourceRouter} from "Resource/Router";
import SharesApi, {Share} from "UCloud/SharesApi";

export const ShareBrowse: React.FunctionComponent<{
    onSelect?: (selection: Share) => void;
    isSearch?: boolean;
    embedded?: boolean;
}> = props => {
    return <ResourceBrowse
        api={SharesApi}
        onSelect={props.onSelect}
        embedded={props.embedded}
        isSearch={props.isSearch}
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={SharesApi} Browser={ShareBrowse}/>;
};

export default Router;
