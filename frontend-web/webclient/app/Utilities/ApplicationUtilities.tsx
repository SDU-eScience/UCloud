import {removeTrailingSlash, errorMessageOrDefault} from "UtilityFunctions";
import {ParameterTypes, WithAppFavorite, WithAppMetadata, ApplicationParameter, AppState, RunsSortBy} from "Applications";
import Cloud from "Authentication/lib";
import {Page} from "Types";
import {expandHomeFolder} from "./FileUtilities";
import {addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SortOrder} from "Files";

export const hpcJobQueryPost = "/hpc/jobs";

export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `/hpc/jobs/follow/${encodeURIComponent(id)}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

export function hpcJobsQuery(
    itemsPerPage: number,
    page: number,
    sortOrder?: SortOrder,
    sortBy?: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: AppState
): string {
    var query = `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (sortOrder) query = query.concat(`&order=${sortOrder}`);
    if (sortBy) query = query.concat(`&sortBy=${sortBy}`);
    if (minTimestamp != null) query = query.concat(`&minTimestamp=${minTimestamp}`);
    if (maxTimestamp != null) query = query.concat(`&maxTimestamp=${maxTimestamp}`);
    if (filter != null) query = query.concat(`&filter=${filter}`)
    return query;
}

export const hpcFavoriteApp = (name: string, version: string) => `/hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`;

export const hpcFavorites = (itemsPerPage: number, pageNumber: number) =>
    `/hpc/apps/favorites?itemsPerPage=${itemsPerPage}&page=${pageNumber}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) =>
    `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`;

interface HPCApplicationsSearchQuery {query: string, page: number, itemsPerPage: number}
export const hpcApplicationsSearchQuery = ({query, page, itemsPerPage}: HPCApplicationsSearchQuery): string =>
    `/hpc/apps/search?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

export const hpcApplicationsTagSearchQuery = ({query, page, itemsPerPage}: HPCApplicationsSearchQuery): string =>
    `/hpc/apps/searchTags?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

export const cancelJobQuery = `hpc/jobs`;

export const cancelJobDialog = ({jobId, onConfirm, jobCount = 1}: {jobCount?: number, jobId: string, onConfirm: () => void}): void =>
    addStandardDialog({
        title: `Cancel job${jobCount > 1 ? "s" : ""}?`,
        message: jobCount === 1 ? `Cancel job: ${jobId}?` : "Cancel jobs?",
        cancelText: "No",
        confirmText: `Cancel job${jobCount > 1 ? "s" : ""}`,
        onConfirm
    });

export const cancelJob = (cloud: Cloud, jobId: string): Promise<{request: XMLHttpRequest, response: void}> =>
    cloud.delete(cancelJobQuery, {jobId});

interface FavoriteApplicationFromPage {
    name: string
    version: string
    page: Page<WithAppMetadata & WithAppFavorite>
    cloud: Cloud
}
/**
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = async ({name, version, page, cloud}: FavoriteApplicationFromPage): Promise<Page<WithAppMetadata & WithAppFavorite>> => {
    const a = page.items.find(it => it.metadata.name === name && it.metadata.version === version)!;
    try {
        await cloud.post(hpcFavoriteApp(name, version));
        a.favorite = !a.favorite;
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, `An error ocurred favoriting ${name}`));
    }
    return page;
};



type StringMap = {[k: string]: string}
interface AllowedParameterKey {name: string, type: ParameterTypes}
interface ExtractParameters {
    parameters: StringMap
    allowedParameterKeys: AllowedParameterKey[]
    siteVersion: number
}

export const extractParameters = ({parameters, allowedParameterKeys, siteVersion}: ExtractParameters): StringMap => {
    let extractedParameters = {};
    if (siteVersion === 1) {
        allowedParameterKeys.forEach(({name, type}) => {
            if (parameters[name] !== undefined) {
                if (compareType(type, parameters[name])) {
                    extractedParameters[name] = parameters[name];
                }
            }
        });
    }
    return extractedParameters;
}

export const isFileOrDirectoryParam = ({type}: {type: string}) => type === "input_file" || type === "input_directory";


const compareType = (type: ParameterTypes, parameter: string): boolean => {
    switch (type) {
        case ParameterTypes.Boolean:
            return parameter === "Yes" || parameter === "No" || parameter === "";
        case ParameterTypes.Integer:
            return parseInt(parameter) % 1 === 0;
        case ParameterTypes.FloatingPoint:
            return typeof parseFloat(parameter) === "number";
        case ParameterTypes.Text:
        case ParameterTypes.InputDirectory:
        case ParameterTypes.InputFile:
            return typeof parameter === "string";

    }
};

interface ExtractedParameters {
    [key: string]: string | number | boolean | {source: string, destination: string}
}

export type ParameterValues = Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement>>;

interface ExtractParametersFromMap {
    map: ParameterValues
    appParameters: ApplicationParameter[]
    cloud: Cloud
}

export function extractParametersFromMap({map, appParameters, cloud}: ExtractParametersFromMap): ExtractedParameters {
    const extracted: ExtractedParameters = {};
    map.forEach(({current}, key) => {
        const parameter = appParameters.find(it => it.name === key);
        if (!current) return;
        if (!current.value || !current.checkValidity()) return;
        if (!parameter) return;
        switch (parameter.type) {
            case ParameterTypes.InputDirectory:
            case ParameterTypes.InputFile:
                const expandedValue = expandHomeFolder(current.value, cloud.homeFolder);
                extracted[key] = {
                    source: expandedValue,
                    destination: removeTrailingSlash(expandedValue).split("/").pop()!
                };
                return;
            case ParameterTypes.Boolean:
                switch (current.value) {
                    case "Yes":
                        extracted[key] = true;
                        return;
                    case "No":
                        extracted[key] = false;
                        return;
                    default:
                        return;
                }
            case ParameterTypes.Integer:
                extracted[key] = parseInt(current.value);
                return;
            case ParameterTypes.FloatingPoint:
                extracted[key] = parseFloat(current.value);
                return;
            case ParameterTypes.Text:
                extracted[key] = current.value;
                return;
        }
    });
    return extracted;
}

export const inCancelableState = (state: AppState) =>
    state === AppState.VALIDATED ||
    state === AppState.PREPARED ||
    state === AppState.SCHEDULED ||
    state === AppState.RUNNING;