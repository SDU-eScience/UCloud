import {APICallParameters} from "Authentication/DataHook";
import {PaginationRequest} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export interface Task {
    jobId: string;
    owner: string;
    processor: string;
    title: string | null;
    status: string | null;
    complete: boolean;
    startedAt: number;
}

export interface TaskUpdate {
    speeds: Speed[];
    progress: Progress | null;
    complete: boolean;
    messageToAppend: string | null;
    newStatus: string | null;
}

export interface Speed {
    title: string;
    speed: number;
    unit: string;
}

export interface Progress {
    title: string;
    current: number;
    maximum: number;
}

export function listTasks(props: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/task", props),
        parameters: props
    };
}

export interface ViewTaskProps {
    id: string;
}

export function viewTask(props: ViewTaskProps): APICallParameters<ViewTaskProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: `/task/${props.id}`,
        parameters: props
    };
}
