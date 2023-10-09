import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import api from "@/UCloud/JobsApi";
import ExperimentalJobs from "./ExperimentalJobs";
import Create from "./Create";

export function JobsRouter() {
    return <ResourceRouter api={api} Browser={ExperimentalJobs} Create={Create} />
}