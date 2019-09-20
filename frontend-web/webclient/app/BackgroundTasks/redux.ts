import {TaskUpdate} from "BackgroundTasks/api";
import {Action} from "redux";
import {Dictionary} from "Types";

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
            const existingTask: TaskUpdate | undefined = state? state[action.update.jobId] : undefined;
            if (!existingTask) {
                const tasks = {...state};
                tasks[action.update.jobId] = action.update;
                return tasks;
            } else {
                const currentMessage = existingTask.messageToAppend ? existingTask.messageToAppend : "";
                const messageToAdd = action.update.messageToAppend ? action.update.messageToAppend : "";
                const newMessage = currentMessage + messageToAdd;

                const newStatus = action.update.newStatus ? action.update.newStatus : existingTask.newStatus;
                const newTitle = action.update.newTitle ? action.update.newTitle : existingTask.newTitle;
                const newProgress = action.update.progress ? action.update.progress : existingTask.progress;
                const newSpeed = action.update.speeds ? action.update.speeds : existingTask.speeds;
                const newComplete = action.update.complete ? action.update.complete : existingTask.complete;

                const tasks = {...state};
                tasks[action.update.jobId] = {
                    ...existingTask,
                    messageToAppend: newMessage,
                    progress: newProgress,
                    speeds: newSpeed,
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
