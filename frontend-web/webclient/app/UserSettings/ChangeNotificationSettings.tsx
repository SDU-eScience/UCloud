import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Button, Checkbox, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {NotificationSettings, retrieveNotificationSettings, updateNotificationSettings} from ".";

interface UserDetailsState {
    settings: NotificationSettings
}

export const defaultNotificationSettings: NotificationSettings = {
    jobStarted: true,
    jobStopped: true,
}

export enum NotificationType {
    JOB_STARTED,
    JOB_STOPPED,
}

const initialState: UserDetailsState = {
    settings: defaultNotificationSettings
};

type UpdatePlaceholdersNotificationSettings = PayloadAction<"UpdatePlaceholdersNotificationSettings", UserDetailsState>;

function reducer(state: UserDetailsState, action: UpdatePlaceholdersNotificationSettings): UserDetailsState {
    switch (action.type) {
        case "UpdatePlaceholdersNotificationSettings":
            return {...state, ...action.payload};
    }
};

export const ChangeNotificationSettings: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = () => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);
    const info = useCallback(async () => {

        const notificationSettings = await invokeCommand(
            retrieveNotificationSettings({}),
            { defaultErrorHandler: false }
        );

        dispatch({
            type: "UpdatePlaceholdersNotificationSettings",
            payload: {
                settings: notificationSettings?.settings ?? defaultNotificationSettings
            }
        });
    }, []);

    useEffect(() => {
        info();
    }, []);

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const wasSuccessful = await invokeCommand(updateNotificationSettings(
            state.settings
        )) !== null;

        if (!wasSuccessful) {
            snackbarStore.addFailure("Failed to update user notification settings", false);
        } else {
            snackbarStore.addSuccess("User notification settings updated", false);
        }
    }, [commandLoading, state.settings]);

    function toggleSubscription(type: NotificationType) {
        switch (type) {
            case NotificationType.JOB_STARTED:
                state.settings.jobStarted = !state.settings.jobStarted
                break;
            case NotificationType.JOB_STOPPED:
                state.settings.jobStopped = !state.settings.jobStopped
        }
        dispatch({
            type: "UpdatePlaceholdersNotificationSettings",
            payload: state
        });
    }
    if (commandLoading) {
        return <HexSpin />
    }
    return (
        <Box mb={16}>
            <Heading.h2>Notifications</Heading.h2>
            <form onSubmit={onSubmit}>
                <Heading.h5>Jobs</Heading.h5>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(NotificationType.JOB_STARTED)}
                        onChange={() => undefined}
                        checked={state.settings.jobStarted}
                    />
                    <span>Job started</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(NotificationType.JOB_STOPPED)}
                        onChange={() => undefined}
                        checked={state.settings.jobStopped}
                    />
                    <span>Job stopped</span>
                </Label>

                <Heading.h5> </Heading.h5>
                <Button
                    mt="1em"
                    type="submit"
                    color="successMain"
                    disabled={commandLoading}
                >
                    Update Notification Settings
                </Button>
            </form>
        </Box>
    );
};
