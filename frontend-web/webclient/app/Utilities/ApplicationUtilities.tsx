import {
    ApplicationInvocationDescription,
    ApplicationMetadata,
    ApplicationParameter,
    JobState,
    ParameterTypes,
    RunsSortBy,
    JobWithStatus
} from "Applications";
import {RangeRef} from "Applications/Widgets/RangeParameters";
import HttpClient from "Authentication/lib";
import {SortOrder} from "Files";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {addStandardDialog} from "UtilityComponents";
import {errorMessageOrDefault, removeTrailingSlash} from "UtilityFunctions";
import {expandHomeOrProjectFolder} from "./FileUtilities";

export const hpcJobQueryPost = "/hpc/jobs";

export const hpcJobQuery = (id: string): string => `/hpc/jobs/${encodeURIComponent(id)}`;

export const toolImageQuery = (toolName: string, cacheBust?: string): string =>
    `/hpc/tools/logo/${toolName}?cacheBust=${cacheBust}`;

export function hpcJobsQuery(
    itemsPerPage: number,
    page: number,
    sortOrder?: SortOrder,
    sortBy?: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: JobState
): string {
    let query = `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (sortOrder) query = query.concat(`&order=${sortOrder}`);
    if (sortBy) query = query.concat(`&sortBy=${sortBy}`);
    if (minTimestamp != null) query = query.concat(`&minTimestamp=${minTimestamp}`);
    if (maxTimestamp != null) query = query.concat(`&maxTimestamp=${maxTimestamp}`);
    if (filter != null) query = query.concat(`&filter=${filter}`);
    return query;
}

export const advancedSearchQuery = "/hpc/apps/advancedSearch";

export const hpcFavoriteApp = (name: string, version: string): string =>
    `/hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`;

export const cancelJobQuery = `hpc/jobs`;

export const cancelJobDialog = ({jobId, onConfirm, jobCount = 1}: {
    jobCount?: number;
    jobId: string;
    onConfirm: () => void;
}): void =>
    addStandardDialog({
        title: `Cancel job${jobCount > 1 ? "s" : ""}?`,
        message: jobCount === 1 ? `Cancel job: ${jobId}?` : "Cancel jobs?",
        cancelText: "No",
        confirmText: `Cancel job${jobCount > 1 ? "s" : ""}`,
        onConfirm
    });

export const cancelJob = (client: HttpClient, jobId: string): Promise<{request: XMLHttpRequest; response: void}> =>
    client.delete(cancelJobQuery, {jobId});

export function isRunExpired(run: JobWithStatus): boolean {
    /* FIXME: This is not great in the long run. If this changes in the backend, it's very awkward to keep updated. */
    return run.status === "Job did not complete within deadline.";
}

interface FavoriteApplicationFromPage<T> {
    name: string;
    version: string;
    page: Page<{metadata: ApplicationMetadata; favorite: boolean} & T>;
    client: HttpClient;
}

/**
 * Favorites an application.
 * @param {Application} Application the application to be favorited
 * @param {HttpClient} client The http client instance for requests
 */
export async function favoriteApplicationFromPage<T>({
    name,
    version,
    page,
    client
}: FavoriteApplicationFromPage<T>): Promise<Page<T>> {
    const a = page.items.find(it => it.metadata.name === name && it.metadata.version === version)!;
    try {
        await client.post(hpcFavoriteApp(name, version));
        a.favorite = !a.favorite;
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, `An error ocurred favoriting ${name}`));
    }
    return page;
}


interface StringMap {
    [k: string]: string;
}

interface AllowedParameterKey {
    name: string;
    type: ParameterTypes;
}

interface ExtractParameters {
    nameToValue: StringMap;
    allowedParameterKeys: AllowedParameterKey[];
    siteVersion: number;
}

export const findKnownParameterValues = ({
    nameToValue,
    allowedParameterKeys,
    siteVersion
}: ExtractParameters): StringMap => {
    const extractedParameters = {};
    if (siteVersion === 1) {
        allowedParameterKeys.forEach(({name, type}) => {
            if (nameToValue[name] !== undefined) {
                if (typeMatchesValue(type, nameToValue[name])) {
                    if (typeof nameToValue[name] === "boolean") {
                        extractedParameters[name] = nameToValue[name] ? "Yes" : "No";
                    } else if (typeof nameToValue[name] === "object") {
                        extractedParameters[name] = (nameToValue[name] as any).source;
                    } else {
                        extractedParameters[name] = nameToValue[name];
                    }
                }
            }
        });
    }
    return extractedParameters;
};

export const isFileOrDirectoryParam = ({type}: {type: string}): boolean =>
    type === "input_file" || type === "input_directory";


type ParameterValueTypes = string | [number, number] | boolean | {source: string};
const typeMatchesValue = (type: ParameterTypes, parameter: ParameterValueTypes): boolean => {
    switch (type) {
        case ParameterTypes.Boolean:
            return parameter === "Yes" ||
                parameter === "No" ||
                parameter === "" ||
                parameter === true ||
                parameter === false;
        case ParameterTypes.Integer:
            return parseInt(parameter as string, 10) % 1 === 0;
        case ParameterTypes.FloatingPoint:
            return typeof parseFloat(parameter as string) === "number";
        case ParameterTypes.Range:
            return typeof parameter === "object" && "size" in parameter;
        case ParameterTypes.Enumeration:
            /* FIXME: Need we do more? */
            return typeof parameter === "string";
        case ParameterTypes.InputDirectory:
        case ParameterTypes.InputFile:
            return typeof parameter === "string" || "source" in (parameter as any);
        case ParameterTypes.Text:
        case ParameterTypes.LicenseServer:
        case ParameterTypes.Peer:
            return typeof parameter === "string";
    }
};

interface ExtractedParameters {
    [key: string]: string | number | boolean |
    {source: string; destination: string} |
    {min: number; max: number} |
    {fileSystemId: string} |
    {jobId: string};
}

export type ParameterValues = Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement | RangeRef>>;

interface ExtractParametersFromMap {
    map: ParameterValues;
    appParameters: ApplicationParameter[];
    client: HttpClient;
}

export function extractValuesFromWidgets({map, appParameters, client}: ExtractParametersFromMap): ExtractedParameters {
    const extracted: ExtractedParameters = {};
    map.forEach((r, key) => {
        const parameter = appParameters.find(it => it.name === key);
        if (!r.current) return;
        if (("value" in r.current && !r.current.value) || ("checkValidity" in r.current && !r.current.checkValidity()))
            return;
        if (!parameter) return;
        if ("value" in r.current) {
            switch (parameter.type) {
                case ParameterTypes.InputDirectory:
                case ParameterTypes.InputFile:
                    const expandedValue = expandHomeOrProjectFolder(r.current.value, client);
                    extracted[key] = {
                        source: expandedValue,
                        destination: removeTrailingSlash(expandedValue).split("/").pop()!
                    };
                    return;
                case ParameterTypes.Boolean:
                    switch (r.current.value) {
                        case "Yes":
                            extracted[key] = true;
                            return;
                        case "No":
                            extracted[key] = false;
                            return;
                        default:
                            return;
                    }
                case ParameterTypes.Enumeration:
                    if (parameter.options.map(it => it.value).includes(r.current.value)) {
                        extracted[key] = r.current.value;
                    }
                    return;
                case ParameterTypes.Integer:
                    extracted[key] = parseInt(r.current.value, 10);
                    return;
                case ParameterTypes.FloatingPoint:
                    extracted[key] = parseFloat(r.current.value);
                    return;
                case ParameterTypes.Text:
                    extracted[key] = r.current.value;
                    return;
                case ParameterTypes.Peer:
                    extracted[key] = {jobId: r.current.value};
                    return;
                case ParameterTypes.LicenseServer:
                    extracted[key] = r.current.value;
                    return;
            }
        } else {
            if (parameter.type === ParameterTypes.Range) {
                const {bounds} = r.current.state;
                extracted[key] = {min: bounds[0], max: bounds[1]};
                return;
            }
        }
    });
    return extracted;
}

export const inCancelableState = (state: JobState): boolean => [
    JobState.VALIDATED,
    JobState.PREPARED,
    JobState.SCHEDULED,
    JobState.RUNNING
].includes(state);

export function validateOptionalFields(
    invocation: ApplicationInvocationDescription,
    parameters: ParameterValues
): boolean {
    const optionalErrors: string[] = [];
    const optionalParams = invocation.parameters.filter(it => it.optional && it.visible).map(it =>
        ({name: it.name, title: it.title})
    );
    optionalParams.forEach(it => {
        const {current} = parameters.get(it.name)!;
        if (current == null || !("checkValidity" in current)) return;
        if (("checkValidity" in current! && !current!.checkValidity())) optionalErrors.push(it.title);

        /* FIXME/ERROR/TODO */
        // Do we need to do anything for enumeration?
    });

    if (optionalErrors.length > 0) {
        snackbarStore.addFailure(
            `Invalid values for ${optionalErrors.slice(0, 3).join(", ")}
                    ${optionalErrors.length > 3 ? `and ${optionalErrors.length - 3} others` : ""}`,
            5000
        );
        return false;
    }
    return true;
}

export function checkForMissingParameters(
    parameters: ExtractedParameters,
    invocation: ApplicationInvocationDescription
): boolean {
    const PT = ParameterTypes;
    const requiredParams = invocation.parameters.filter(it => !it.optional);
    const missingParameters: string[] = [];
    requiredParams.forEach(rParam => {
        const parameterValue = parameters[rParam.name];
        if (parameterValue == null) missingParameters.push(rParam.title);
        else if ([PT.Boolean, PT.FloatingPoint, PT.Integer, PT.Text, PT.Enumeration].includes[rParam.type] &&
            !["number", "string", "boolean"].includes(typeof parameterValue)) {
            missingParameters.push(rParam.title);
        } else if (rParam.type === ParameterTypes.InputDirectory || rParam.type === ParameterTypes.InputFile) {
            // tslint:disable-next-line: no-string-literal
            if (!parameterValue["source"]) {
                missingParameters.push(rParam.title);
            }
        }
    });

    // Check missing values for required input fields.
    if (missingParameters.length > 0) {
        snackbarStore.addFailure(
            `Missing values for ${missingParameters.slice(0, 3).join(", ")}
                ${missingParameters.length > 3 ? `and ${missingParameters.length - 3} others.` : ``}`,
            5000
        );
        return false;
    }
    return true;
}
