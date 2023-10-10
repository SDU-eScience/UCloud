import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import {ExperimentalPublicLinks} from "./ExperimentalBrowse";
import PublicLinkApi from "@/UCloud/PublicLinkApi";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={PublicLinkApi} Browser={ExperimentalPublicLinks}/>;
};

export default Router;
