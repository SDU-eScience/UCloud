import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import PrivateNetworkApi from "@/UCloud/PrivateNetworkApi";
import {PrivateNetworkBrowse} from "./PrivateNetworkBrowse";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={PrivateNetworkApi} Browser={PrivateNetworkBrowse} />;
};

export default Router;
