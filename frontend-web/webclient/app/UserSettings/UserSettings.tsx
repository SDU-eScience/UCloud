import * as React from "react";
import { Header as SHeader, Grid as SGrid, Form as SForm, Input as SInput, Button as SButton } from "semantic-ui-react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import {
    successNotification,
    defaultErrorHandler
} from "UtilityFunctions";
import { UserSettingsFields, UserSettingsState } from ".";
import { TwoFactorSetup } from "./TwoFactorSetup";
import * as Heading from "ui-components/Heading";
import { MainContainer } from "MainContainer/MainContainer";
import { Flex, Box } from "ui-components";

class UserSettings extends React.Component<{}, UserSettingsState> {
    constructor(props) {
        super(props);
        this.state = this.initialState();
    }

    initialState(): UserSettingsState {
        return {
            promiseKeeper: new PromiseKeeper(),
            currentPassword: "",
            newPassword: "",
            repeatedPassword: "",
            error: false,
            repeatPasswordError: false
        };
    }

    updateField(field: UserSettingsFields, value: string | boolean): void {
        const state = { ...this.state }
        state[field] = value;
        state.error = false;
        state.repeatPasswordError = false;
        this.setState(() => state);
    }

    validateAndSubmit(e: React.SyntheticEvent): void {
        e.preventDefault();

        let error = false;
        let repeatPasswordError = false;

        const {
            currentPassword,
            newPassword,
            repeatedPassword,
        } = this.state;

        if (!currentPassword || !newPassword || !repeatedPassword) {
            error = true;
        }

        if (newPassword !== repeatedPassword) {
            error = true;
            repeatPasswordError = true;
        }

        this.setState(() => ({ error, repeatPasswordError }));

        if (!error) {
            this.state.promiseKeeper.makeCancelable(
                Cloud.post(
                    "/auth/users/password",
                    { currentPassword, newPassword },
                    ""
                )
            ).promise.then(f => {
                successNotification("Password successfully changed");
                this.setState(() => this.initialState());
            }).catch(error => {
                let status = defaultErrorHandler(error);
                this.setState(() => ({ error: true }));
            });
        }
    }

    render() {
        const {
            error,
            currentPassword,
            newPassword,
            repeatedPassword,
            repeatPasswordError
        } = this.state;

        return (
            <Flex alignItems="center" flexDirection="column">
                <Box width={0.7}>
                    <MainContainer
                        header={<Heading.h1>Change Password</Heading.h1>}
                        main={
                            <>
                                <SForm onSubmit={(e) => this.validateAndSubmit(e)}>
                                    <SForm.Field
                                        error={error && !currentPassword}
                                        label="Current password"
                                        control={SInput}
                                        value={currentPassword}
                                        type="password"
                                        onChange={(e, { value }) => this.updateField("currentPassword", value)}
                                        placeholder="Old password"
                                    />
                                    <SForm.Field
                                        error={repeatPasswordError}
                                        label="New password"
                                        control={SInput}
                                        value={newPassword}
                                        type="password"
                                        onChange={(e, { value }) => this.updateField("newPassword", value)}
                                        placeholder="New password"
                                    />

                                    <SForm.Field
                                        error={repeatPasswordError}
                                        label="Repeat password"
                                        control={SInput}
                                        value={repeatedPassword}
                                        type="password"
                                        onChange={(e, { value }) => this.updateField("repeatedPassword", value)}
                                        placeholder="Repeat password"
                                    />

                                    <SButton
                                        type="submit"
                                        positive
                                        icon="lock"
                                        content="Change password"
                                    />
                                </SForm>
                                <TwoFactorSetup />
                            </>
                        }
                    />
                </Box>
            </Flex>
        );
    }
}

export default UserSettings;