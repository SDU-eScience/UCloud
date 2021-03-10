import {useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import * as React from "react";
import {useCallback, useEffect, useRef} from "react";
import {Box, Button, Checkbox, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {EmailSettings, retrieveEmailSettings, toggleEmailSettings} from "UserSettings/api";
import {bulkRequestOf} from "DefaultObjects";
import {file} from "UCloud";
import stat = file.stat;

interface UserDetailsState {
    settings: EmailSettings
}

export const defaultEmailSettings: EmailSettings = {
    newGrantApplication: true,
    grantAutoApprove: true,
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
    lowFunds: true
}

export enum MailType {
    NEW_GRANT_APPLICATION,
    GRANT_AUTO_APPROVE,
    GRANT_APPLICATION_UPDATED,
    GRANT_APPLICATION_APPROVED,
    GRANT_APPLICATION_REJECTED,
    GRANT_APPLICATION_WITHDRAWN,
    NEW_COMMENT_ON_APPLICATION,
    APPLICATION_TRANSFER,
    APPLICATION_STATUS_CHANGE,
    //Project
    PROJECT_USER_INVITE,
    PROJECT_USER_REMOVED,
    VERIFICATION_REMINDER,
    USER_ROLE_CHANGE,
    USER_LEFT,
    LOW_FUNDS
}

const initialState: UserDetailsState = {
    settings: defaultEmailSettings
};

type Action<T, B> = {type: T; payload: B};
type UpdatePlaceholdersEmailSettings = Action<"UpdatePlaceholdersEmailSettings", UserDetailsState>;

const reducer = (state: UserDetailsState, action: UpdatePlaceholdersEmailSettings): UserDetailsState => {
    switch (action.type) {
        case "UpdatePlaceholdersEmailSettings":
            return {...state, ...action.payload};
    }
};

export const ChangeEmailSettings: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = () => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);

    var currentEmailSettings: EmailSettings = defaultEmailSettings
    const info = useCallback(async () => {

        const emailSettings = await invokeCommand(
            retrieveEmailSettings({})
        )

        currentEmailSettings = emailSettings.settings ?? defaultEmailSettings

        dispatch({
            type: "UpdatePlaceholdersEmailSettings",
            payload: {
                settings: currentEmailSettings
            }
        });
    }, []);

    useEffect(() => {
        info();
    }, []);

    const update = useCallback(async () => {
        dispatch({
            type: "UpdatePlaceholdersEmailSettings",
            payload: {
                settings: currentEmailSettings
            }
        })
    }, [])

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const wasSuccessful = await invokeCommand(toggleEmailSettings(bulkRequestOf({
            settings: currentEmailSettings
        }))) !== null;

        if (!wasSuccessful) {
            snackbarStore.addFailure("Failed to update user email settings", false);
        } else {
            snackbarStore.addSuccess("User email settings updated", false);
        }
    }, [commandLoading]);

    function toggleSubscription(type: MailType) {
        switch (type) {
            case MailType.NEW_GRANT_APPLICATION:
                state.settings.newGrantApplication = !state.settings.newGrantApplication
                currentEmailSettings.newGrantApplication = state.settings.newGrantApplication
                break;
            case MailType.GRANT_AUTO_APPROVE:
                state.settings.grantAutoApprove = !state.settings.grantAutoApprove
                currentEmailSettings.grantAutoApprove = state.settings.grantAutoApprove
                break;
            case MailType.GRANT_APPLICATION_UPDATED:
                state.settings.grantApplicationUpdated = !state.settings.grantApplicationUpdated
                currentEmailSettings.grantApplicationUpdated = state.settings.grantApplicationUpdated
                break;
            case MailType.GRANT_APPLICATION_APPROVED:
                state.settings.grantApplicationApproved = !state.settings.grantApplicationApproved
                currentEmailSettings.grantApplicationApproved = state.settings.grantApplicationApproved
                break;
            case MailType.GRANT_APPLICATION_REJECTED:
                state.settings.grantApplicationRejected = !state.settings.grantApplicationRejected
                currentEmailSettings.grantApplicationRejected = state.settings.grantApplicationRejected
                break;
            case MailType.GRANT_APPLICATION_WITHDRAWN:
                state.settings.grantApplicationWithdrawn = !state.settings.grantApplicationWithdrawn
                currentEmailSettings.grantApplicationWithdrawn = state.settings.grantApplicationWithdrawn
                break;
            case MailType.NEW_COMMENT_ON_APPLICATION:
                state.settings.newCommentOnApplication = !state.settings.newCommentOnApplication
                currentEmailSettings.newCommentOnApplication = state.settings.newCommentOnApplication
                break;
            case MailType.PROJECT_USER_INVITE:
                state.settings.projectUserInvite = !state.settings.projectUserInvite
                currentEmailSettings.projectUserInvite = state.settings.projectUserInvite
                break;
            case MailType.PROJECT_USER_REMOVED:
                state.settings.projectUserRemoved = !state.settings.projectUserRemoved
                currentEmailSettings.projectUserRemoved = state.settings.projectUserRemoved
                break;
            case MailType.VERIFICATION_REMINDER:
                state.settings.verificationReminder = !state.settings.verificationReminder
                currentEmailSettings.verificationReminder = state.settings.verificationReminder
                break;
            case MailType.USER_ROLE_CHANGE:
                state.settings.userRoleChange = !state.settings.userRoleChange
                currentEmailSettings.userRoleChange = state.settings.userRoleChange
                break;
            case MailType.USER_LEFT:
                state.settings.userLeft = !state.settings.userLeft
                currentEmailSettings.userLeft = state.settings.userLeft
                break;
            case MailType.LOW_FUNDS:
                state.settings.lowFunds = !state.settings.lowFunds
                currentEmailSettings.lowFunds = state.settings.lowFunds
                break;
            case MailType.APPLICATION_STATUS_CHANGE:
                state.settings.applicationStatusChange = !state.settings.applicationStatusChange
                currentEmailSettings.applicationStatusChange = state.settings.applicationStatusChange
                break;
        }
    }

    return (
        <Box mb={16}>
            <Heading.h2>Email Settings</Heading.h2>
            <form onSubmit={onSubmit}>

                <Heading.h5>Grant Applications</Heading.h5>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_APPROVED)}
                        onChange={update}
                        checked={state.settings.grantApplicationApproved}
                    />
                    <Box as="span">Application Approved</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_REJECTED)}
                        onChange={update}
                        checked={state.settings.grantApplicationRejected}
                    />
                    <Box as="span">Application Rejected</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_WITHDRAWN)}
                        onChange={update}
                        checked={state.settings.grantApplicationWithdrawn}
                    />
                    <Box as="span">Application Withdrawn</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.NEW_GRANT_APPLICATION)}
                        onChange={update}
                        checked={state.settings.newGrantApplication}
                    />
                    <Box as="span">New Application Received</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.APPLICATION_STATUS_CHANGE)}
                        onChange={update}
                        checked={state.settings.applicationStatusChange}
                    />
                    <Box as="span">Status Change By Others Admins</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.APPLICATION_TRANSFER)}
                        onChange={update}
                        checked={state.settings.applicationTransfer}
                    />
                    <Box as="span">Transfers From Other Projects</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_AUTO_APPROVE)}
                        onChange={update}
                        checked={state.settings.grantAutoApprove}
                    />
                    <Box as="span">On Auto Approval</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.NEW_COMMENT_ON_APPLICATION)}
                        onChange={update}
                        checked={state.settings.newCommentOnApplication}
                    />
                    <Box as="span">New Comment In Application</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_UPDATED)}
                        onChange={update}
                        checked={state.settings.grantApplicationUpdated}
                    />
                    <Box as="span">Application Has Been Edited</Box>
                </Label>

                <Heading.h5>Projects</Heading.h5>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.LOW_FUNDS)}
                        onChange={update}
                        checked={state.settings.lowFunds}
                    />
                    <Box as="span">Low On Funds</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.PROJECT_USER_INVITE)}
                        onChange={update}
                        checked={state.settings.projectUserInvite}
                    />
                    <Box as="span">User Invited To Project</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.USER_LEFT)}
                        onChange={update}
                        checked={state.settings.userLeft}
                    />
                    <Box as="span">User Left Project</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.USER_ROLE_CHANGE)}
                        onChange={update}
                        checked={state.settings.userRoleChange}
                    />
                    <Box as="span">User Role Changed</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.PROJECT_USER_REMOVED)}
                        onChange={update}
                        checked={state.settings.projectUserRemoved}
                    />
                    <Box as="span">User Removed From Project</Box>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.VERIFICATION_REMINDER)}
                        onChange={update}
                        checked={state.settings.verificationReminder}
                    />
                    <Box as="span">Verification Reminders</Box>
                </Label>

                <Heading.h5> </Heading.h5>
                <Button
                    mt="1em"
                    type="submit"
                    color="green"
                    disabled={commandLoading}
                >
                    Update Email Settings
                </Button>
            </form>
        </Box>
    );
};
