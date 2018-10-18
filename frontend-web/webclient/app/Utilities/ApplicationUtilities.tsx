import { infoNotification, failureNotification } from "UtilityFunctions";
import { Application, ParameterTypes } from "Applications";
import Cloud from "Authentication/lib";
import { Page } from "Types";


export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `/hpc/jobs/follow/${id}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

export const hpcJobsQuery = (itemsPerPage: number, page: number): string =>
    `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;

export const hpcFavoriteApp = (name: string, version: string) => `hpc/apps/favorites?name=${name}&version=${version}`;

export const hpcFavorites = (itemsPerPage: number, pageNumber: number) =>
    `/hpc/apps/favorites?pageNumber=${itemsPerPage}&pageNumber=${pageNumber}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) =>
    `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`;


/**
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = (application: Application, page: Page<Application>, cloud: Cloud): Page<Application> => {
    const a = page.items.find(it => it.description.info.name === application.description.info.name);
    if (a) {
        a.favorite = !a.favorite;
        infoNotification("Backend functionality for favoriting applications missing");
        cloud.post(hpcFavoriteApp(a.description.info.name, a.description.info.version)).catch(() =>
            failureNotification("An error occurred favoriting application.")
        );
    } else {
        failureNotification("An error occurred favoriting application.");
    }
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

const compareType = (type: ParameterTypes, parameter): boolean => {
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