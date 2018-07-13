import * as React from "react";
import { Form, Button, Input, Grid, Header } from "semantic-ui-react";
import { Cloud } from "../../authentication/SDUCloudObject";
import PromiseKeeper from "../PromiseKeeper";
import {
    successNotification,
    defaultErrorHandler
} from "../UtilityFunctions";
import { UserCreationState, UserCreationField } from ".";

class UserCreation extends React.Component<{}, UserCreationState> {
    constructor(props) {
        super(props);
        this.state = this.initialState();
    }

    initialState = (): UserCreationState => ({
        promiseKeeper: new PromiseKeeper(),
        submitted: false,
        username: "",
        password: "",
        repeatedPassword: "",
        usernameError: false,
        passwordError: false
    });

    componentWillUnmount() {
        this.state.promiseKeeper.cancelPromises();
    }

    updateFields(field: UserCreationField, value: string) {
        const state = { ...this.state }
        state[field] = value;
        state.usernameError = false;
        state.passwordError = false;
        this.setState(() => state);
    }

    submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let usernameError = false;
        let passwordError = false;
        const { username, password, repeatedPassword } = this.state;
        if (!username) {
            usernameError = true;
        }
        if (!password || password !== repeatedPassword) {
            passwordError = true;
        }
        this.setState(() => ({ usernameError, passwordError }));

        if (!usernameError && !passwordError) {
            this.state.promiseKeeper.makeCancelable(
                Cloud.post("/auth/users/register", { username, password }, "")
            ).promise.then(f => {
                successNotification(`User '${username}' successfully created`);
                this.setState(() => this.initialState());
            }).catch(error => {
                let status = defaultErrorHandler(error);
                if (status == 400) {
                    this.setState(() => ({ usernameError: true }));
                }
            });
        }
    }

    render() {
        if (!Cloud.userIsAdmin) return null;

        const {
            usernameError,
            passwordError,
            username,
            password,
            repeatedPassword,
            submitted
        } = this.state;

        return (
            <React.StrictMode>
                <Grid container columns={16}>
                    <Grid.Row>
                        <Grid.Column width={16}>
                            <Header><h1>User Creation</h1></Header>
                            <p>Admins can create new users on this page.</p>
                        </Grid.Column>
                    </Grid.Row>

                    <Grid.Row>
                        <Grid.Column width={16}>
                            <Form onSubmit={(e) => this.submit(e)}>
                                <Form.Field
                                    error={usernameError}
                                    label="Username"
                                    control={Input}
                                    value={username}
                                    onChange={(_, { value }) => this.updateFields("username", value)}
                                    placeholder="Username..."
                                />
                                <Form.Field
                                    error={passwordError}
                                    control={Input}
                                    label="Password"
                                    value={password}
                                    type="password"
                                    onChange={(_, { value }) => this.updateFields("password", value)}
                                    placeholder="Password..."
                                />
                                <Form.Field
                                    error={passwordError}
                                    control={Input}
                                    label="Repeat password"
                                    value={repeatedPassword}
                                    type="password"
                                    onChange={(_, { value }) => this.updateFields("repeatedPassword", value)}
                                    placeholder="Repeat password..."
                                />
                                <Button
                                    type="submit"
                                    content="Create user"
                                    icon="user plus"
                                    positive
                                    loading={submitted}
                                />
                            </Form>
                        </Grid.Column>
                    </Grid.Row>
                </Grid>
            </React.StrictMode>
        );
    }
}

export default UserCreation;