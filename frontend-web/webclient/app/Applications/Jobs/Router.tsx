import * as React from "react";
import {ResourceRouter} from "@/Resource/Router";
import api from "@/UCloud/JobsApi";
import Browse from "./ExperimentalJobs";
import Create from "./Create";

export function JobsRouter() {
    return <ResourceRouter api={api} Browser={Browse} Create={Create} />
}