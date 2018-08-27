import { infoNotification, failureNotification } from "UtilityFunctions";
import { Application, ParameterTypes } from "Applications";
import Cloud from "Authentication/lib";
import { Page } from "Types";


export const hpcJobQuery = (id: string, stdoutLine: number, stderrLine: number, stdoutMaxLines: number = 1000, stderrMaxLines: number = 1000) =>
    `hpc/jobs/follow/${id}?stdoutLineStart=${stdoutLine}&stdoutMaxLines=${stdoutMaxLines}&stderrLineStart=${stderrLine}&stderrMaxLines=${stderrMaxLines}`;

export const hpcJobsQuery = (itemsPerPage: number, page: number): string =>
    `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) => `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`


/**
* //FIXME Missing backend functionality
* Favorites an application. 
* @param {Application} Application the application to be favorited
* @param {Cloud} cloud The cloud instance for requests
*/
export const favoriteApplicationFromPage = (application: Application, page: Page<Application>, cloud: Cloud): Page<Application> => {
    const a = page.items.find(it => it.description.info.name === application.description.info.name);
    if (a) {
        a.favorite = !a.favorite;
        infoNotification("Backend functionality for favoriting applications missing");
    } else {
        failureNotification("An error occurred ")
    }
    return page;
    /*  
    const { info } = a.description;
    if (a.favorite) {
        cloud.post(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } else {
        cloud.delete(`/applications/favorite?name=${info.name}&version=${info.name}`, {})
    } 
    */
}

export const extractParameters = (parameters, allowedParameterKeys, siteVersion: number) => {
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

const compareType = (type, parameter): boolean => {
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
        default:
            return false;
    }
}