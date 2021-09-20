import * as UCloud from "@/UCloud";
import {JobState} from "@/Applications/Jobs";

export function isRunExpired(run: UCloud.compute.Job): boolean {
    return run.status.state === "EXPIRED";
}

export const inCancelableState = (state: JobState): boolean => [
    "IN_QUEUE",
    "RUNNING",
].includes(state);
