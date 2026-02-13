import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {NotificationSettings, retrieveNotificationSettings, updateNotificationSettings} from "./settingsApi";
import {SettingsCheckboxRow, SettingsSection} from "./SettingsComponents";

export const defaultNotificationSettings: NotificationSettings = {
    jobStarted: true,
    jobStopped: true,
}

const notificationOptions: {key: keyof NotificationSettings; title: string; description?: string}[] = [
    {
        key: "jobStopped",
        title: "Job started or stopped",
        description: "Sends a notification when jobs start or stop"
    }
];

interface ChangeNotificationSettingsProps {
    setLoading: (loading: boolean) => void;
}

export const ChangeNotificationSettings: React.FunctionComponent<ChangeNotificationSettingsProps> = ({setLoading}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [settings, setSettings] = useState<NotificationSettings>(defaultNotificationSettings);

    const info = useCallback(async () => {

        const notificationSettings = await invokeCommand(
            retrieveNotificationSettings({}),
            {defaultErrorHandler: false}
        );

        setSettings(notificationSettings?.settings ?? defaultNotificationSettings);
    }, []);

    useEffect(() => {
        info();
    }, []);

    useEffect(() => {
        setLoading(commandLoading);
    }, [commandLoading, setLoading]);

    const toggleSubscription = useCallback((key: keyof NotificationSettings) => {
        if (commandLoading) return;

        const previousSettings = settings;
        const nextSettings = {
            ...settings,
            [key]: !settings[key]
        };

        setSettings(nextSettings);

        void (async () => {
            const wasSuccessful = await invokeCommand(updateNotificationSettings(
                nextSettings
            )) !== null;

            if (!wasSuccessful) {
                setSettings(previousSettings);
                snackbarStore.addFailure("Failed to update user notification settings", false);
            }
        })();
    }, [commandLoading, settings, invokeCommand]);

    if (commandLoading) {
        return <HexSpin />
    }
    return (
        <SettingsSection id="notifications" title="Notification settings">
            <Box>
                <Box mb={24}>
                    <Heading.h5>Jobs</Heading.h5>
                    <Box mt={6}>
                        {notificationOptions.map(option => (
                            <SettingsCheckboxRow
                                key={option.key}
                                title={option.title}
                                description={option.description}
                                onClick={() => toggleSubscription(option.key)}
                                checked={settings[option.key]}
                                disabled={commandLoading}
                            />
                        ))}
                    </Box>
                </Box>
            </Box>
        </SettingsSection>
    );
};
