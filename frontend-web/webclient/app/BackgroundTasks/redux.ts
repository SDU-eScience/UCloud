import {Speed, Task, TaskUpdate} from "BackgroundTasks/api";
import {Action} from "redux";
import {associateBy, takeLast} from "Utilities/CollectionUtilities";

export interface TaskReduxState {
    tasks?: Record<string ,TaskUpdate>;
}

interface TaskUpdateAction extends Action<"TASK_UPDATE"> {
    update: TaskUpdate;
}

interface TaskLoadAction extends Action<"TASK_LOAD"> {
    page: Page<Task>;
}

export function taskUpdateAction(update: TaskUpdate): TaskUpdateAction {
    return {type: "TASK_UPDATE", update};
}

export function taskLoadAction(page: Page<Task>): TaskLoadAction {
    return {type: "TASK_LOAD", page};
}

type TypeActions = TaskUpdateAction | TaskLoadAction;

export const reducer = (
    state: TaskReduxState = ({}),
    action: TypeActions
): TaskReduxState => {
    switch (action.type) {
        case "TASK_UPDATE": {
            const existingTask: TaskUpdate | undefined = state ? state[action.update.jobId] : undefined;
            if (action.update.complete) {
                const copy = {...state};
                delete copy[action.update.jobId];
                return copy;
            }

            if (!existingTask) {
                const tasks = {...state};
                tasks[action.update.jobId] = {
                    ...action.update,
                    speeds: insertTimestamps(action.update.speeds)
                };
                return tasks;
            } else {
                const currentMessage = existingTask.messageToAppend ? existingTask.messageToAppend : "";
                const messageToAdd = action.update.messageToAppend ? action.update.messageToAppend : "";
                const newMessage = currentMessage + messageToAdd;

                const newStatus = action.update.newStatus ? action.update.newStatus : existingTask.newStatus;
                const newTitle = action.update.newTitle ? action.update.newTitle : existingTask.newTitle;
                const newProgress = action.update.progress ? action.update.progress : existingTask.progress;
                const newSpeed = takeLast((existingTask.speeds || []).concat(action.update.speeds || []), 500);
                const newComplete = action.update.complete ? action.update.complete : existingTask.complete;

                const tasks = {...state};
                tasks[action.update.jobId] = {
                    ...existingTask,
                    messageToAppend: newMessage,
                    progress: newProgress,
                    speeds: insertTimestamps(newSpeed),
                    complete: newComplete,
                    newStatus,
                    newTitle
                };

                return tasks;
            }
        }

        case "TASK_LOAD": {
            const updates: TaskUpdate[] = action.page.items.map(item => {
                return {
                    jobId: item.jobId,
                    speeds: [],
                    messageToAppend: null,
                    progress: null,
                    newTitle: item.title,
                    complete: false,
                    newStatus: item.status
                };
            });

            return associateBy(updates, it => it.jobId);
        }
        default:
            return state;
    }
};

function insertTimestamps(speeds: Speed[]): Speed[] {
    return speeds.map(it => {
        if (it.clientTimestamp) {
            return it;
        } else {
            return {...it, clientTimestamp: Date.now()};
        }
    });
}
