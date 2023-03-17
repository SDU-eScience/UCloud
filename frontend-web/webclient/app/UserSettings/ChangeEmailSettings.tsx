import {useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Button, Checkbox, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {bulkRequestOf} from "@/DefaultObjects";
import {mail} from "@/UCloud";
import EmailSettings = mail.EmailSettings;
import retrieveEmailSettings = mail.retrieveEmailSettings;
import toggleEmailSettings = mail.toggleEmailSettings;
import HexSpin from "@/LoadingIcon/LoadingIcon";

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

type UpdatePlaceholdersEmailSettings = PayloadAction<"UpdatePlaceholdersEmailSettings", UserDetailsState>;

const reducer = (state: UserDetailsState, action: UpdatePlaceholdersEmailSettings): UserDetailsState => {
    switch (action.type) {
        case "UpdatePlaceholdersEmailSettings":
            return {...state, ...action.payload};
    }
};

export const ChangeEmailSettings: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = () => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);
    const info = useCallback(async () => {

        const emailSettings = await invokeCommand(
            retrieveEmailSettings({}),
            { defaultErrorHandler: false }
        );

        dispatch({
            type: "UpdatePlaceholdersEmailSettings",
            payload: {
                settings: emailSettings?.settings ?? defaultEmailSettings
            }
        });
    }, []);

    useEffect(() => {
        info();
    }, []);

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const wasSuccessful = await invokeCommand(toggleEmailSettings(bulkRequestOf({
            settings: state.settings
        }))) !== null;

        if (!wasSuccessful) {
            snackbarStore.addFailure("Failed to update user email settings", false);
        } else {
            snackbarStore.addSuccess("User email settings updated", false);
        }
    }, [commandLoading, state.settings]);

    function toggleSubscription(type: MailType) {
        switch (type) {
            case MailType.NEW_GRANT_APPLICATION:
                state.settings.newGrantApplication = !state.settings.newGrantApplication
                break;
            case MailType.GRANT_AUTO_APPROVE:
                state.settings.grantAutoApprove = !state.settings.grantAutoApprove
                break;
            case MailType.GRANT_APPLICATION_UPDATED:
                state.settings.grantApplicationUpdated = !state.settings.grantApplicationUpdated
                break;
            case MailType.GRANT_APPLICATION_APPROVED:
                state.settings.grantApplicationApproved = !state.settings.grantApplicationApproved
                break;
            case MailType.GRANT_APPLICATION_REJECTED:
                state.settings.grantApplicationRejected = !state.settings.grantApplicationRejected
                break;
            case MailType.GRANT_APPLICATION_WITHDRAWN:
                state.settings.grantApplicationWithdrawn = !state.settings.grantApplicationWithdrawn
                break;
            case MailType.NEW_COMMENT_ON_APPLICATION:
                state.settings.newCommentOnApplication = !state.settings.newCommentOnApplication
                break;
            case MailType.PROJECT_USER_INVITE:
                state.settings.projectUserInvite = !state.settings.projectUserInvite
                break;
            case MailType.PROJECT_USER_REMOVED:
                state.settings.projectUserRemoved = !state.settings.projectUserRemoved
                break;
            case MailType.VERIFICATION_REMINDER:
                state.settings.verificationReminder = !state.settings.verificationReminder
                break;
            case MailType.USER_ROLE_CHANGE:
                state.settings.userRoleChange = !state.settings.userRoleChange
                break;
            case MailType.USER_LEFT:
                state.settings.userLeft = !state.settings.userLeft
                break;
            case MailType.LOW_FUNDS:
                state.settings.lowFunds = !state.settings.lowFunds
                break;
            case MailType.APPLICATION_STATUS_CHANGE:
                state.settings.applicationStatusChange = !state.settings.applicationStatusChange
                break;
            case MailType.APPLICATION_TRANSFER:
                state.settings.applicationTransfer = !state.settings.applicationTransfer
                break;
        }
        dispatch({
            type: "UpdatePlaceholdersEmailSettings",
            payload: state
        });
    }
    if (commandLoading) {
        return <HexSpin />
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
                        onChange={() => undefined}
                        checked={state.settings.grantApplicationApproved}
                    />
                    <span>Application approved</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_REJECTED)}
                        onChange={() => undefined}
                        checked={state.settings.grantApplicationRejected}
                    />
                    <span>Application rejected</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_WITHDRAWN)}
                        onChange={() => undefined}
                        checked={state.settings.grantApplicationWithdrawn}
                    />
                    <span>Application withdrawn</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.NEW_GRANT_APPLICATION)}
                        onChange={() => undefined}
                        checked={state.settings.newGrantApplication}
                    />
                    <span>New application received</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.APPLICATION_STATUS_CHANGE)}
                        onChange={() => undefined}
                        checked={state.settings.applicationStatusChange}
                    />
                    <span>Status change by other admins</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.APPLICATION_TRANSFER)}
                        onChange={() => undefined}
                        checked={state.settings.applicationTransfer}
                    />
                    <span>Transfers from other projects</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_AUTO_APPROVE)}
                        onChange={() => undefined}
                        checked={state.settings.grantAutoApprove}
                    />
                    <span>On auto approval</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.NEW_COMMENT_ON_APPLICATION)}
                        onChange={() => undefined}
                        checked={state.settings.newCommentOnApplication}
                    />
                    <span>New comment in application</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.GRANT_APPLICATION_UPDATED)}
                        onChange={() => undefined}
                        checked={state.settings.grantApplicationUpdated}
                    />
                    <span>Application has been edited</span>
                </Label>

                <Heading.h5>Projects</Heading.h5>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.LOW_FUNDS)}
                        onChange={() => undefined}
                        checked={state.settings.lowFunds}
                    />
                    <span>Low on funds</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.PROJECT_USER_INVITE)}
                        onChange={() => undefined}
                        checked={state.settings.projectUserInvite}
                    />
                    <span>User invited to project</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.USER_LEFT)}
                        onChange={() => undefined}
                        checked={state.settings.userLeft}
                    />
                    <span>User left project</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.USER_ROLE_CHANGE)}
                        onChange={() => undefined}
                        checked={state.settings.userRoleChange}
                    />
                    <span>User role changed</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.PROJECT_USER_REMOVED)}
                        onChange={() => undefined}
                        checked={state.settings.projectUserRemoved}
                    />
                    <span>User removed from project</span>
                </Label>
                <Label ml={10} width="45%" style={{display: "inline-block"}}>
                    <Checkbox
                        size={27}
                        onClick={() => toggleSubscription(MailType.VERIFICATION_REMINDER)}
                        onChange={() => undefined}
                        checked={state.settings.verificationReminder}
                    />
                    <span>Verification reminders</span>
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
