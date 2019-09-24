import {Speed, TaskUpdate} from "BackgroundTasks/api";
import {Action} from "redux";
import {Dictionary} from "Types";
import {takeLast} from "Utilities/CollectionUtilities";

export interface TaskReduxState {
    tasks?: Dictionary<TaskUpdate>;
}

interface TaskUpdateAction extends Action<"TASK_UPDATE"> {
    update: TaskUpdate;
}

export function taskUpdateAction(update: TaskUpdate): TaskUpdateAction {
    return {
        type: "TASK_UPDATE",
        update
    };
}

type TypeActions = TaskUpdateAction;

export const reducer = (
    state: any = ({}),
    action: TypeActions
): TaskReduxState => {
    switch (action.type) {
        case "TASK_UPDATE": {
            const existingTask: TaskUpdate | undefined = state ? state[action.update.jobId] : undefined;
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
    }
    return state;
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
