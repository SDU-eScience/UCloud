import * as React from "react";
import {default as LicenseApi} from "@/UCloud/LicenseApi";
import {ResourceRouter} from "@/Resource/Router";
import {LicenseBrowse} from "./LicenseBrowse";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={LicenseApi} Browser={LicenseBrowse} />;
};

export default Router;
