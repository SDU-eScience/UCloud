import * as React from "react";
import {callAPI} from "@/Authentication/DataHook";
import {api as FileCollectionsApi} from "@/UCloud/FileCollectionsApi";
import {default as PublicLinkApi} from "@/UCloud/PublicLinkApi";
import {default as LicenseApi} from "@/UCloud/LicenseApi";
import {default as NetworkIPApi} from "@/UCloud/NetworkIPApi";
import {default as FilesApi} from "@/UCloud/FilesApi";
import {default as JobsApi} from "@/UCloud/JobsApi";
import {doNothing} from "@/UtilityFunctions";
import {useEffect} from "react";

export function initializeResources() {
    callAPI(FileCollectionsApi.init()).then(doNothing).catch(doNothing);
    callAPI(PublicLinkApi.init()).then(doNothing).catch(doNothing);
    callAPI(LicenseApi.init()).then(doNothing).catch(doNothing);
    callAPI(NetworkIPApi.init()).then(doNothing).catch(doNothing);
    callAPI(FilesApi.init()).then(doNothing).catch(doNothing);
    callAPI(JobsApi.init()).then(doNothing).catch(doNothing);
}

export const ResourceInit: React.FunctionComponent = () => {
    useEffect(() => {
        initializeResources();
    }, []);
    return null;
};
