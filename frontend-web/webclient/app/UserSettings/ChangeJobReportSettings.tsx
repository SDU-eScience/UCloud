import {useCloudCommand} from "@/Authentication/DataHook";
import {useCallback, useEffect, useState} from "react";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {SettingsSection} from "@/UserSettings/SettingsComponents";
import {Box} from "@/ui-components";
import {bulkRequestOf} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

interface ChangeJobReportSettingsProps {
    setLoading: (loading: boolean) => void;
}

// TODO insert JobReportSettings and others in the settingsApi.ts API
export const ChangeJobReportSettings: React.FunctionComponent<ChangeJobReportSettingsProps> = ({setLoading}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [settings, setSettings] = useState<JobReportSettings>(defaultJobReportSettings);

    const info = useCallback(async () => {

        const emailSettings = await invokeCommand(
            retrieveJobReportSettings({}),
            {defaultErrorHandler: false}
        );

        setSettings(JobReportSettings?.settings ?? defaultJobReportSettings);
    }, []);

    useEffect(() => {
        info();
    }, []);

    useEffect(() => {
        setLoading(commandLoading);
    }, [commandLoading, setLoading]);

    const toggleSubscription = useCallback((key: keyof JobReportSettings)=> {
        if (commandLoading) return;
        const previousSettings = settings;
        const nextSettings = {
          ...settings,
          [key]: !settings[key]
        };

        setSettings(nextSettings);

        void (async () => {
            const wasSuccessful = await invokeCommand(toggleJobReportSettings(bulkRequestOf({
                settings: nextSettings
            }))) !== null;

            if (!wasSuccessful) {
                setSettings(previousSettings);
                snackbarStore.addFailure("Failed to update job report settings", false)
            }
        })();
    }, [commandLoading, settings, invokeCommand]);

    if (commandLoading) {
        return <HexSpin />
    }
    return (
        <SettingsSection id="job-report" title="Job report settings">
            <Box></Box>
        </SettingsSection>
    );
};