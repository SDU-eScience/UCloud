import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import {default as NetworkIPApi} from "@/UCloud/NetworkIPApi";
import {ExperimentalNetworkIP} from "./ExperimentalBrowse";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={NetworkIPApi} Browser={ExperimentalNetworkIP}/>;
};

export default Router;
