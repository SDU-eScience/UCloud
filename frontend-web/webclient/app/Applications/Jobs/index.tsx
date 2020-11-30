import {Create} from "./Create";
import {View} from "./View" ;
import {Browse} from "./Browse" ;
import * as UCloud from "UCloud";
import {PropType} from "UtilityFunctions";

export {View, Create, Browse};
export type JobState = NonNullable<PropType<UCloud.compute.JobUpdate, "state">>;

export function isJobStateTerminal(state: JobState): boolean {
    return state === "SUCCESS" || state === "FAILURE";
}

export const stateToOrder = (state: JobState): 0 | 1 | 2 | 3 | 4 | 5 => {
    switch (state) {
        case "IN_QUEUE":
            return 0;
        case "RUNNING":
            return 1;
        /*
        case JobState.READY:
        return 2;
        */
        case "SUCCESS":
            return 3;
        case "FAILURE":
            return 3;
        default:
            return 0;
    }
};

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
        /*
        case JobState.READY:
        return "Ready";
        */
        default:
            return "Unknown";
    }
};
