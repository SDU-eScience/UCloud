import { failureNotification } from "UtilityFunctions";
import { ParameterTypes, WithAppFavorite, WithAppMetadata, ApplicationParameter } from "Applications";
import Cloud from "Authentication/lib";
import { Page } from "Types";
import { getFilenameFromPath, expandHomeFolder } from "./FileUtilities";

export const hpcJobQueryPost = "/hpc/jobs";

export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `/hpc/jobs/follow/${encodeURIComponent(id)}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

export const hpcJobsQuery = (itemsPerPage: number, page: number): string =>
    `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;

export const hpcFavoriteApp = (name: string, version: string) => `/hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`;

export const hpcFavorites = (itemsPerPage: number, pageNumber: number) =>
    `/hpc/apps/favorites?itemsPerPage=${itemsPerPage}&page=${pageNumber}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) =>
    `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`;

interface HPCApplicationsSearchQuery { query: string, page: number, itemsPerPage: number }
export const hpcApplicationsSearchQuery = ({ query, page, itemsPerPage }): string =>
    `/hpc/apps/search?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

export const hpcApplicationsTagSearchQuery = ({ query, page, itemsPerPage }: HPCApplicationsSearchQuery): string =>
    `/hpc/apps/searchTags?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

/**
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = async (name: string, version: string, page: Page<WithAppMetadata & WithAppFavorite>, cloud: Cloud): Promise<Page<WithAppMetadata & WithAppFavorite>> => {
    const a = page.items.find(it => it.metadata.name === name && it.metadata.version === version)!;
    // FIXME better error handling. Pass as callback, call on success?
    try {
        await cloud.post(hpcFavoriteApp(name, version)).catch(() => failureNotification(`An error ocurred favoriting ${name}`));
        a.favorite = !a.favorite;
    } catch { }
    return page;
}

interface AllowedParameterKey { name: string, type: ParameterTypes }
export const extractParameters = (parameters, allowedParameterKeys: AllowedParameterKey[], siteVersion: number): { [key: string]: string } => {
    let extractedParameters = {};
    if (siteVersion === 1) {
        allowedParameterKeys.forEach(({ name, type }) => {
            if (parameters[name] !== undefined) {
                if (compareType(type, parameters[name])) {
                    extractedParameters[name] = parameters[name];
                }
            }
        });
    }
    return extractedParameters;
}


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
}

interface ExtractedParameters {
    [key: string]: string | number | boolean | { source: string, destination: string }
}

export type ParameterValues = Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement>>;

export function extractParametersFromMap(map: ParameterValues, appParameters: ApplicationParameter[], cloud: Cloud): ExtractedParameters {
    const extracted: ExtractedParameters = {};
    map.forEach(({ current }, key) => {
        const parameter = appParameters.find(it => it.name === key);
        if (!current) return;
        if (!current.value || !current.checkValidity()) return;
        if (!parameter) return;
        switch (parameter.type) {
            case ParameterTypes.InputDirectory:
            case ParameterTypes.InputFile:
                const expandedValue = expandHomeFolder(current.value, cloud.homeFolder)
                extracted[key] = {
                    source: expandedValue,
                    destination: getFilenameFromPath(expandedValue)
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
    })
    return extracted;
}