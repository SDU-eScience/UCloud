import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import api from "@/UCloud/JobsApi";
import Browse from "./JobsBrowse";
import Create from "./Create";

type JobsRouterProps = {
    browseFilterType?: "VMS_ONLY" | "JOBS_ONLY";
};

export function JobsRouter({browseFilterType}: JobsRouterProps) {
    const Browser = React.useCallback(() => <Browse opts={{jobTypeFilter: browseFilterType}} />, [browseFilterType]);
    return <ResourceRouter api={api} Browser={Browser} Create={Create} />
}
