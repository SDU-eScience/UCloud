import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import {PublicLinkBrowse} from "./PublicLinkBrowse";
import PublicLinkApi from "@/UCloud/PublicLinkApi";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={PublicLinkApi} Browser={PublicLinkBrowse}/>;
};

export default Router;
