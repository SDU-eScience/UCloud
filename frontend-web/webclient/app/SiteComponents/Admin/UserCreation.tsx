import * as React from "react";
import { Form, Button, Input, Message } from "semantic-ui-react";


interface UserCreationState {
    submitted: boolean
    username: string
    password: string
    repeatedPassword: string
    passwordError: boolean
    usernameError: boolean
}

type UserCreationField = keyof UserCreationState;

class UserCreation extends React.Component<{}, UserCreationState> {
    constructor(props) {
        super(props);
        this.state = {
            submitted: false,
            username: "",
            password: "",
            repeatedPassword: "",
            passwordError: false,
            usernameError: false
        }
    }

    updateFields(field: UserCreationField, value: string) {
        const state = { ...this.state }
        state[field] = value;
        state.usernameError = false;
        state.passwordError = false;
        this.setState(() => state);
    }

    submit() {
        let error = false;
        const {username, password, repeatedPassword } = this.state;
        if (!username) {
            this.setState(() => ({ usernameError: true }));
            error = true;
        }
        if (!password || password !== repeatedPassword) {
            this.setState(() => ({ passwordError: true }));
            error = true;
        }
        if (!error) {
            // submit
        }


    }

    render() {
        return (
            <React.StrictMode>
                <Form>
                    <Form.Field error={this.state.usernameError}>
                        <label>Username</label>
                        <Input value={this.state.username} onChange={(_e, { value }) => this.updateFields("username", value)} placeholder="Username..." />
                    </Form.Field>
                    <Form.Field error={this.state.passwordError}>
                        <label>Password</label>
                        <Input value={this.state.password} type="password" onChange={(_e, { value }) => this.updateFields("password", value)} placeholder="Password..." />
                    </Form.Field>
                    <Form.Field error={this.state.passwordError}>
                        <label>Repeat Password</label>
                        <Input value={this.state.repeatedPassword} type="password" onChange={(_e, { value }) => this.updateFields("repeatedPassword", value)} placeholder="Repeat password..." />
                    </Form.Field>
                    <Button type="button" content="Create" color="blue" onClick={() => this.submit()} loading={this.state.submitted} />
                </Form>
            </React.StrictMode>
        );
    }
}

export default UserCreation;