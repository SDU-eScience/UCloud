import {ResourceRouter} from "@/Resource/Router";
import FilesApi from "@/UCloud/FilesApi";
import FileCollectionsApi from "@/UCloud/FileCollectionsApi";
import ExperimentalFileBrowse from "@/Files/ExperimentalBrowse";
import React from "react";
import ExperimentalDriveBrowse from "./ExperimentalDriveBrowse";

export function DrivesRouter() {
    return <ResourceRouter
        Browser={ExperimentalDriveBrowse}
        api={FileCollectionsApi}
    />
}

export function FilesRouter() {
    return <ResourceRouter
        Browser={ExperimentalFileBrowse}
        api={FilesApi}
    />
}