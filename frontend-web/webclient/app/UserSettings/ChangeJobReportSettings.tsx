import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Select} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {SettingsCheckboxRow, SettingsSection} from "./SettingsComponents";
import {
    JobReportSampleRate,
    JobReportSettings,
    retrieveJobReportSettings,
    updateJobReportSettings,
} from "./settingsApi";

interface ChangeJobReportSettingsProps {
    setLoading: (loading: boolean) => void;
}

const defaultJobReportSettings: JobReportSettings = {
    toggled: false,
    sampleRateValue: null,
};

const SAMPLE_RATE_DEFAULT = "250ms";

const sampleRateOptions: Array<{value: JobReportSampleRate; label: string}> = [
    {value: "0ms", label: "Do not sample"},
    {value: "250ms", label: "250 ms (default)"},
    {value: "500ms", label: "500 ms"},
    {value: "750ms", label: "750 ms"},
    {value: "1000ms", label: "1 s"},
    {value: "5000ms", label: "5 s"},
    {value: "10000ms", label: "10 s"},
    {value: "30000ms", label: "30 s"},
    {value: "60000ms", label: "1 minute"},
    {value: "120000ms", label: "2 minutes"},
];

const allowedSampleRates = new Set(sampleRateOptions.map(option => option.value));

function normalizeSettings(settings: JobReportSettings | null): JobReportSettings {
    if (settings == null) return defaultJobReportSettings;

    if (settings.sampleRateValue == null) {
        return settings;
    }

    if (allowedSampleRates.has(settings.sampleRateValue)) {
        return settings;
    }

    return {
        ...settings,
        sampleRateValue: null,
    };
}

export const ChangeJobReportSettings: React.FunctionComponent<ChangeJobReportSettingsProps> = ({setLoading}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [settings, setSettings] = useState<JobReportSettings>(defaultJobReportSettings);

    const info = useCallback(async () => {
        const jobReportSettings = await invokeCommand(
            retrieveJobReportSettings({}),
            {defaultErrorHandler: false}
        );

        setSettings(normalizeSettings(jobReportSettings));
    }, []);

    useEffect(() => {
        info();
    }, []);

    useEffect(() => {
        setLoading(commandLoading);
    }, [commandLoading, setLoading]);

    const updateSettings = useCallback((nextSettings: JobReportSettings) => {
        if (commandLoading) return;

        const previousSettings = settings;

        setSettings(nextSettings);

        void (async () => {
            const wasSuccessful = await invokeCommand(updateJobReportSettings(nextSettings)) !== null;

            if (!wasSuccessful) {
                setSettings(previousSettings);
                snackbarStore.addFailure("Failed to update job report settings", false);
            }
        })();
    }, [commandLoading, settings, invokeCommand]);

    const toggleSubscription = useCallback(() => {
        updateSettings({...settings, toggled: !settings.toggled});
    }, [settings, updateSettings]);

    const onSampleRateChange = useCallback((event: React.ChangeEvent<HTMLSelectElement>) => {
        const value = event.target.value;
        const sampleRateValue = value === SAMPLE_RATE_DEFAULT ? null : value as JobReportSampleRate;
        updateSettings({...settings, sampleRateValue});
    }, [settings, updateSettings]);

    if (commandLoading) {
        return <HexSpin />
    }

    return (
        <SettingsSection id="job-report" title="Job report settings">
            <Box>
                <Box mb={24}>
                    <Heading.h5>Resource utilization</Heading.h5>
                    <Box mt={6}>
                        <SettingsCheckboxRow
                            title="Enable job report metrics"
                            description="Collects resource utilization data for the job report."
                            onClick={toggleSubscription}
                            checked={settings.toggled}
                            disabled={commandLoading}
                        />
                    </Box>
                </Box>

                <Box>
                    <Heading.h5>Sampling rate</Heading.h5>
                    <Box mt={6} maxWidth="320px">
                        <Select
                            value={settings.sampleRateValue ?? SAMPLE_RATE_DEFAULT}
                            onChange={onSampleRateChange}
                            disabled={commandLoading || !settings.toggled}
                            width="100%"
                        >
                            {sampleRateOptions.map(option => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                        </Select>
                    </Box>
                </Box>
            </Box>
        </SettingsSection>
    );
};
