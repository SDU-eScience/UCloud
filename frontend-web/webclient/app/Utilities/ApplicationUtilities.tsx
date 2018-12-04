import { infoNotification, failureNotification } from "UtilityFunctions";
import { Application, ParameterTypes } from "Applications";
import Cloud from "Authentication/lib";
import { Page } from "Types";


export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `/hpc/jobs/follow/${encodeURIComponent(id)}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

export const hpcJobsQuery = (itemsPerPage: number, page: number): string =>
    `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;

export const hpcFavoriteApp = (name: string, version: string) => `hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`;

export const hpcFavorites = (itemsPerPage: number, pageNumber: number) =>
    `/hpc/apps/favorites?itemsPerPage=${itemsPerPage}&page=${pageNumber}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) =>
    `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`;

export const hpcApplicationsSearchQuery = (query: string, page: number, itemsPerPage: number) =>
    `/hpc/apps/search?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

export const hpcApplicationsTagSearchQuery = (tag: string, page: number, itemsPerPage: number) =>
    `/hpc/apps/searchTags?query=${encodeURIComponent(tag)}&page=${page}&itemsPerPage=${itemsPerPage}`;


/**
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = async (name: string, version: string, page: Page<Application>, cloud: Cloud): Promise<Page<Application>> => {
    const a = page.items.find(it => it.description.info.name === name && it.description.info.version === version)!;
    // FIXME better error handling. Pass as callback, call on success?
    await cloud.post(hpcFavoriteApp(name, version)).catch(() => failureNotification(`An error ocurred favoriting ${name}`));
    a.favorite = !a.favorite;
    return page;
}

interface AllowedParameterKey { name: string, type: ParameterTypes }
export const extractParameters = (parameters, allowedParameterKeys: AllowedParameterKey[], siteVersion: number) => {
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


const compareType = (type: ParameterTypes, parameter: any): boolean => {
    switch (type) {
        case ParameterTypes.Boolean:
            return typeof parameter === "boolean";
        case ParameterTypes.Integer:
            return typeof parameter === "number" && parameter % 1 === 0;
        case ParameterTypes.FloatingPoint:
            return typeof parameter === "number";
        case ParameterTypes.Text:
            return typeof parameter === "string";
        case ParameterTypes.InputDirectory:
        case ParameterTypes.InputFile:
            return typeof parameter.destination === "string" && typeof parameter.source === "string";
    }
}