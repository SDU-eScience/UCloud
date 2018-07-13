import * as React from "react";
import { Header, Grid, Form, Input, Button } from "semantic-ui-react";
import PromiseKeeper from "../PromiseKeeper";
import { Cloud } from "../../authentication/SDUCloudObject";
import {
    successNotification,
    defaultErrorHandler
} from "../UtilityFunctions";
import { UserSettingsFields, UserSettingsState } from ".";

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
            <React.StrictMode>
                <Grid container columns={1}>
                    <Grid.Column>
                        <Header><h1>Change Password</h1></Header>
                    </Grid.Column>
                    <Grid.Column>
                        <Form onSubmit={(e) => this.validateAndSubmit(e)}>
                            <Form.Field
                                error={error && !currentPassword}
                                label="Current password"
                                control={Input}
                                value={currentPassword}
                                type="password"
                                onChange={(e, { value }) => this.updateField("currentPassword", value)}
                                placeholder="Old password"
                            />
                            <Form.Field
                                error={repeatPasswordError}
                                label="New password"
                                control={Input}
                                value={newPassword}
                                type="password"
                                onChange={(e, { value }) => this.updateField("newPassword", value)}
                                placeholder="New password"
                            />

                            <Form.Field
                                error={repeatPasswordError}
                                label="Repeat password"
                                control={Input}
                                value={repeatedPassword}
                                type="password"
                                onChange={(e, { value }) => this.updateField("repeatedPassword", value)}
                                placeholder="Repeat password"
                            />

                            <Button
                                type="submit"
                                positive
                                icon="lock"
                                content="Change password"
                            />
                        </Form>
                    </Grid.Column>
                </Grid>
            </React.StrictMode >
        );
    }
}

export default UserSettings;