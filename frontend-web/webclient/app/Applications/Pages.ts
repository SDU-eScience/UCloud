import {buildQueryString} from "@/Utilities/URIUtilities";
import {NameAndVersion} from "@/Applications/AppStoreApi";

export const view = (name: string, version: string): string =>
    `/applications/details/${encodeURIComponent(name)}/${encodeURIComponent(version)}/`;

export const run = (name: string, version: string | undefined = undefined): string =>
    buildQueryString("/jobs/create", {app: name, version});

export const browseGroup = (id: string): string =>
    `/applications/group/${encodeURIComponent(id)}`;

export const runApplication = (application: NameAndVersion): string =>
    run(application.name, application.version);

export const runApplicationWithName = (application: string): string =>
    run(application);

export const results = (): string => `/applications/results`;

export const browse = (itemsPerPage = 25, page = 0): string =>
    buildQueryString(`/applications`, {itemsPerPage, page});