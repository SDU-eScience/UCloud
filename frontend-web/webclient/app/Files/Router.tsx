import {ResourceRouter} from "@/Resource/Router";
import FilesApi from "@/UCloud/FilesApi";
import FileCollectionsApi from "@/UCloud/FileCollectionsApi";
import FileBrowse from "@/Files/FileBrowse";
import React from "react";
import DriveBrowse from "./DriveBrowse";

export function DrivesRouter(): React.ReactNode {
    return <ResourceRouter
        Browser={DriveBrowse}
        api={FileCollectionsApi}
    />
}

export function FilesRouter(): React.ReactNode {
    return <ResourceRouter
        Browser={FileBrowse}
        api={FilesApi}
    />
}