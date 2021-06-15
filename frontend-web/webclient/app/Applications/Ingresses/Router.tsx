import * as React from "react";
import {ResourceRouter} from "Resource/Router";
import {default as IngressApi} from "UCloud/IngressApi";
import Browse from "Applications/Ingresses/Browse";

const IngressRouter: React.FunctionComponent = () => {
    console.log("Is this mounting?");
    return <ResourceRouter api={IngressApi} Browser={Browse}/>;
};

export default IngressRouter;
