import * as React from "react";
import * as UCloud from "UCloud";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";

export const Run: React.FunctionComponent = () => {
    const [searchResp] = useCloudAPI(
        UCloud.compute.apps.advancedSearch({
            showAllVersions: true
        }),
        emptyPage
    );

    const [loading, invokeCall] = useCloudCommand();
    invokeCall((UCloud.file.favorite.toggleFavorite({
        path: "/home/fie/fie2.jpg"
    })));

    return null;
}

function takesProduct(prod: UCloud.accounting.Product) {
    if (prod.type === "compute") {
        prod.cpu
    }
}
