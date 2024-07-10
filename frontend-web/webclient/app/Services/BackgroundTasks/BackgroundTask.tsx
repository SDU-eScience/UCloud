import * as React from "react";
import {useEffect, useMemo} from "react";
import {Icon} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Flex from "@/ui-components/Flex";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {UploadState, useUploads} from "@/Files/Upload";
import {injectStyle} from "@/Unstyled";
import {TaskList, taskStore} from "@/Playground/Playground";
import {inDevEnvironment} from "@/UtilityFunctions";

const BackgroundTasks: React.FunctionComponent = () => {
    const [uploads] = useUploads();
    const inProgress = React.useSyncExternalStore(s => taskStore.subscribe(s), () => taskStore.inProgress);

    const activeUploadCount = useMemo(() => {
        let activeCount = 0;
        for (let i = 0; i < uploads.length; i++) {
            if (uploads[i].state === UploadState.UPLOADING) {
                activeCount++;
            }
        }
        return activeCount;
    }, [uploads]);

    useEffect(() => {
        function onBeforeUnload(): boolean {
            snackbarStore.addInformation(
                "You currently have uploads in progress. Are you sure you want to leave UCloud?",
                true
            );
            return false;
        }
        if (activeUploadCount > 0) {
            window.addEventListener("beforeunload", onBeforeUnload);
        }

        return () => {
            window.removeEventListener("beforeunload", onBeforeUnload);
        };
    }, [uploads]);

    if ((Object.values(inProgress).length + activeUploadCount) === 0 || !inDevEnvironment()) return null;
    return (
        <ClickableDropdown
            left="50px"
            bottom="-168px"
            trigger={<Flex justifyContent="center">{TasksIcon}</Flex>}
        >
            <TaskList />
        </ClickableDropdown>
    );
};

const TasksIconBase = injectStyle("task-icon-base", k => `
    @keyframes spin {
        0% {
           transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }

    ${k} {
        animation: spin 2s linear infinite;
        margin-bottom: 16px;
    }
`);

const TasksIcon = <Icon color="fixedWhite" color2="fixedWhite" height="20px" width="20px" className={TasksIconBase} name="notchedCircle" />;

export default BackgroundTasks;
