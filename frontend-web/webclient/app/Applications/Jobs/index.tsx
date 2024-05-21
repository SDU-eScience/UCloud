import * as UCloud from "@/UCloud";
import {PropType, shortUUID} from "@/UtilityFunctions";
import {compute} from "@/UCloud";
import Job = compute.Job;
import * as StatModel from "./StatisticsApiModel";
import {apiRetrieve} from "@/Authentication/DataHook";
import {JobStatistics} from "./StatisticsApiModel";

export type JobState = NonNullable<PropType<UCloud.compute.JobUpdate, "state">>;
export type JobSortBy = NonNullable<PropType<UCloud.compute.JobsBrowseRequest, "sortBy">>;

export function isJobStateTerminal(state: JobState): boolean {
    return state === "SUCCESS" || state === "FAILURE" || state === "EXPIRED";
}

export const stateToTitle = (state: JobState): string => {
    switch (state) {
        case "FAILURE":
            return "Failure";
        case "IN_QUEUE":
            return "In queue";
        case "RUNNING":
            return "Running";
        case "SUCCESS":
            return "Success";
        case "CANCELING":
            return "Canceling";
        case "EXPIRED":
            return "Expired";
        case "SUSPENDED":
            return "Suspended";
        default:
            return "Unknown";
    }
};

export function jobTitle(job: Job): string {
    return job.specification.name ?? shortUUID(job.id)
}

export function jobAppTitle(job: Job): string {
    return job.specification.resolvedApplication?.metadata?.title ?? job.specification.application.name;
}

export function jobAppVersion(job: Job): string {
    return job.specification.application.version;
}

const statisticsContext = "/api/jobs/statistics";
export function retrieveStatistics(request: {
    start: number;
    end: number;
}): APICallParametersBinary<StatModel.JobStatistics> {
    return {
        ...apiRetrieve(request, statisticsContext),
        responseConstructor: JobStatistics,
    };
}

export {
    JobStatistics, JobStatisticsCompanion,
    JobUsageByUser, JobUsageByUserDataPoint,
    MostUsedApplications, MostUsedApplicationsDataPoint,
    JobSubmissionStatistics, JobSubmissionStatisticsDataPoint,
} from "./StatisticsApiModel";
