import {WSFactory} from "@/Authentication/HttpClientInstance";
import {BackgroundTask, TaskOperations, taskStore} from "@/Services/BackgroundTasks/BackgroundTask";
import {WebSocketConnection} from "@/Authentication/ws";
import {Notification, normalizeNotification, sendNotification} from "@/Notifications";
import {Client} from "@/Authentication/HttpClientInstance";

let taskStream: WebSocketConnection | null = null;
let notificationStream: WebSocketConnection | null = null;
let isCore2 = false;
let reconnectionInterval = -1;
let reconnectionTimer = 0;
let prevConnectionTimer = 1;

export function deinitNotifications() {
    window.clearInterval(reconnectionInterval);
    taskStream?.close?.();
    notificationStream?.close?.();
}

export function initTaskAndNotificationStream() {
    taskStream?.close();
    notificationStream?.close();

    taskStream = null;
    notificationStream = null;
    isCore2 = false;

    if (Client.isLoggedIn) {
        reconnectionTimer = Math.min(50, prevConnectionTimer * 2);
        prevConnectionTimer = reconnectionTimer;

        WSFactory.open(
            "/tasks",
            {
                reconnect: false,
                init: async conn => {
                    taskStream = conn;
                    prevConnectionTimer = 1;

                    try {
                        const resp = await conn.call({call: "core2", payload: {}});
                        isCore2 = resp.status === 200;
                    } catch (e) {
                    }

                    if (isCore2) {
                        await initCore2Streams();
                    } else {
                        initCore1Streams();
                    }

                }
            }
        );
    }

    if (reconnectionInterval === -1) {
        reconnectionInterval = window.setInterval(() => {
            if (reconnectionTimer <= 0) {
                let isConnected = taskStream != null && !taskStream.closed;
                if (!isCore2) isConnected = isConnected && notificationStream != null && !notificationStream.closed;

                if (!isConnected) {
                    initTaskAndNotificationStream();
                }
            } else {
                reconnectionTimer--;
            }
        }, 100);
    }
}

function initCore1Streams() {
    (async () => {
        const conn = taskStream!;
        try {
            const page: PageV2<BackgroundTask> = (await conn.call(
                TaskOperations.calls.browse({
                    itemsPerPage: 250,
                } as any)
            )).payload;

            for (const item of page.items) {
                taskStore.addTask(item);
            }
        } catch (e) {
            console.warn(e);
        }

        try {
            await conn.subscribe({
                call: "tasks.listen",
                payload: {},
                handler: message => {
                    if (message.type === "message") {
                        if (message.payload) {
                            const task: BackgroundTask = message.payload;
                            taskStore.addTask(task);
                        }
                    }
                }
            });
        } catch (e) {
            console.warn(e);
        }
    })();

    (() => {
        notificationStream = WSFactory.open("/notifications", {
            reconnect: false,
            init: c => {
                c.subscribe({
                    call: "notifications.subscription",
                    payload: {},
                    handler: message => {
                        if (message.type === "message") {
                            sendNotification(normalizeNotification(message.payload));
                        }
                    }
                });
            }
        });
    })();
}

interface Core2StreamMessageBatch {
    messages: Core2StreamMessage[];
}

interface Core2StreamMessage {
    type: string;
    task?: BackgroundTask | null;
    notification?: Notification | null;
}

async function initCore2Streams() {
    const stream = taskStream!;
    const s = stream.socket;
    stream.hijack();

    s.onclose = () => {
        stream.closed = true;
    };

    s.onmessage = ev => {
        const batch: Core2StreamMessageBatch = JSON.parse(ev.data);
        for (const message of batch.messages) {
            switch (message.type) {
                case "TASK": {
                    taskStore.addTask(message.task!);
                    break;
                }

                case "NOTIFICATION": {
                    sendNotification(normalizeNotification(message.notification!));
                    break;
                }

                case "PROJECTS": {
                    Client.invalidateCurrentAccessToken();
                    break;
                }
            }
        }
    };

    s.send("READY");
}
