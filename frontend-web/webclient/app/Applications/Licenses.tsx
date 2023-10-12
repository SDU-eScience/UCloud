import * as React from "react";
import {default as LicenseApi} from "@/UCloud/LicenseApi";
import {ResourceRouter} from "@/Resource/Router";
import {ExperimentalLicenses} from "./ExperimentalLicenses";

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={LicenseApi} Browser={ExperimentalLicenses} />;
};

export default Router;
