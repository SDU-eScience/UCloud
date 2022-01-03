import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import {default as NetworkIPApi} from "@/UCloud/NetworkIPApi";
import {NetworkIPBrowse} from "@/Applications/NetworkIP/Browse";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={NetworkIPApi} Browser={NetworkIPBrowse}/>;
};

export default Router;
