import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import Browse from "@/Admin/Providers/Browse";
import ProvidersApi from "@/UCloud/ProvidersApi";

const ProviderRouter: React.FunctionComponent = () => {
    return <ResourceRouter api={ProvidersApi} Browser={Browse} />
};

export default ProviderRouter;