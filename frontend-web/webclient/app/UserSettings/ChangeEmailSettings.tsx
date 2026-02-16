import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {bulkRequestOf} from "@/UtilityFunctions";
import {mail} from "@/UCloud";
import EmailSettings = mail.EmailSettings;
import retrieveEmailSettings = mail.retrieveEmailSettings;
import toggleEmailSettings = mail.toggleEmailSettings;
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {SettingsCheckboxRow, SettingsSection} from "./SettingsComponents";

export interface UserDetailsState {
    settings: EmailSettings
}

export const defaultEmailSettings: EmailSettings = {
    newGrantApplication: true,
    grantApplicationUpdated: true,
    grantApplicationApproved: true,
    grantApplicationRejected: true,
    grantApplicationWithdrawn: true,
    newCommentOnApplication: true,
    applicationTransfer: true,
    applicationStatusChange: true,
    //Project
    projectUserInvite: true,
    projectUserRemoved: true,
    verificationReminder: true,
    userRoleChange: true,
    userLeft: true,
    lowFunds: true,
    // Jobs
    jobStarted: false,
    jobStopped: false,
}

type SettingOption = {
    key: keyof EmailSettings;
    title: string;
    description?: string;
};

type SettingGroup = {
    title: string;
    options: SettingOption[];
};

const emailSettingGroups: SettingGroup[] = [
    {
        title: "Grant applications",
        options: [
            {
                key: "grantApplicationApproved",
                title: "Application approved",
                description: "Sends an email when an application is approved"
            },
            {
                key: "grantApplicationRejected",
                title: "Application rejected",
                description: "Sends an email when an application is rejected"
            },
            {
                key: "grantApplicationWithdrawn",
                title: "Application withdrawn",
                description: "Sends an email when an application is withdrawn"
            },
            {
                key: "newGrantApplication",
                title: "New application received",
                description: "Sends an email when your project receives a new application"
            },
            {
                key: "applicationStatusChange",
                title: "Status change by other admins",
                description: "Sends an email when another admin changes an application status"
            },
            {
                key: "applicationTransfer",
                title: "Transfers from other projects",
                description: "Sends an email when applications are transferred from other projects"
            },
            {
                key: "newCommentOnApplication",
                title: "New comment in application",
                description: "Sends an email when a new comment is added to an application"
            },
            {
                key: "grantApplicationUpdated",
                title: "Application has been edited",
                description: "Sends an email when an application is edited"
            },
        ]
    },
    {
        title: "Projects",
        options: [
            {
                key: "lowFunds",
                title: "Low on funds",
                description: "Sends an email when project balances are running low"
            },
            {
                key: "projectUserInvite",
                title: "User invited to project",
                description: "Sends an email when a user is invited to a project"
            },
            {
                key: "userLeft",
                title: "User left project",
                description: "Sends an email when a user leaves a project"
            },
            {
                key: "userRoleChange",
                title: "User role changed",
                description: "Sends an email when a user's role changes"
            },
            {
                key: "projectUserRemoved",
                title: "User removed from project",
                description: "Sends an email when a user is removed from a project"
            },
            {
                key: "verificationReminder",
                title: "Verification reminders",
                description: "Sends reminder emails about pending verification actions"
            },
        ]
    },
    {
        title: "Jobs",
        options: [
            {
                key: "jobStopped",
                title: "Job started or stopped",
                description: "Sends an email when jobs start or stop"
            },
        ]
    }
];

interface ChangeEmailSettingsProps {
    setLoading: (loading: boolean) => void;
}

export const ChangeEmailSettings: React.FunctionComponent<ChangeEmailSettingsProps> = ({setLoading}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [settings, setSettings] = useState<EmailSettings>(defaultEmailSettings);

    const info = useCallback(async () => {

        const emailSettings = await invokeCommand(
            retrieveEmailSettings({}),
            {defaultErrorHandler: false}
        );

        setSettings(emailSettings?.settings ?? defaultEmailSettings);
    }, []);

    useEffect(() => {
        info();
    }, []);

    useEffect(() => {
        setLoading(commandLoading);
    }, [commandLoading, setLoading]);

    const toggleSubscription = useCallback((key: keyof EmailSettings) => {
        if (commandLoading) return;

        const previousSettings = settings;
        const nextSettings = {
            ...settings,
            [key]: !settings[key]
        };

        setSettings(nextSettings);

        void (async () => {
            const wasSuccessful = await invokeCommand(toggleEmailSettings(bulkRequestOf({
                settings: nextSettings
            }))) !== null;

            if (!wasSuccessful) {
                setSettings(previousSettings);
                snackbarStore.addFailure("Failed to update user email settings", false);
            }
        })();
    }, [commandLoading, settings, invokeCommand]);

    if (commandLoading) {
        return <HexSpin />
    }
    return (
        <SettingsSection id="email" title="Email settings">
            <Box>
                {emailSettingGroups.map(group => (
                    <Box key={group.title} mb={24}>
                        <Heading.h5>{group.title}</Heading.h5>
                        <Box mt={6}>
                            {group.options.map(option => (
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
                ))}
            </Box>
        </SettingsSection>
    );
};
