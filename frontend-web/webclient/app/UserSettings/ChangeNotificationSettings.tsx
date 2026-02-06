import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {Checkbox} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {NotificationSettings, retrieveNotificationSettings, updateNotificationSettings} from "./settingsApi";
import {PayloadAction} from "@reduxjs/toolkit";
import {SettingsActions, SettingsCheckboxRow, SettingsSection} from "./SettingsComponents";

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

type UpdatePlaceholdersNotificationSettings = PayloadAction<UserDetailsState, "UpdatePlaceholdersNotificationSettings">;

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
            {defaultErrorHandler: false}
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
        <SettingsSection title="Notifications">
            <form onSubmit={onSubmit}>
                <Heading.h5>Jobs</Heading.h5>
                <SettingsCheckboxRow>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(NotificationType.JOB_STOPPED)}
                        onChange={() => undefined}
                        checked={state.settings.jobStopped}
                    />
                    <span>Job started or stopped</span>
                </SettingsCheckboxRow>

                <Heading.h5> </Heading.h5>
                <SettingsActions submitLabel="Update Notification Settings" disabled={commandLoading} />
            </form>
        </SettingsSection>
    );
};
