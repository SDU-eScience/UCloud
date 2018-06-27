import * as React from "react";
import { Form, Button, Input } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";

interface UserCreationState {
    submitted: boolean
    username: string
    password: string
    repeatedPassword: string
    error: boolean
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
            error: false
        }
    }

    updateFields(field: UserCreationField, value: string) {
        const state = { ...this.state }
        state[field] = value;
        state.error = false;
        this.setState(() => state);
    }

    submit() {
        let error = false;
        const { username, password, repeatedPassword } = this.state;
        if (!username || !password || !repeatedPassword) {
            error = true;
        }
        if (password !== repeatedPassword) {
            error = true;
        }
        this.setState(() => ({ error }));
        if (!error) {
            // submit
        }


    }

    render() {
        if (!Cloud.userIsAdmin) return null;
        const { error, username, password, repeatedPassword, submitted } = this.state;
        return (
            <React.StrictMode>
                <Form>
                    <Form.Field error={error}>
                        <label>Username</label>
                        <Input value={username} onChange={(_e, { value }) => this.updateFields("username", value)} placeholder="Username..." />
                    </Form.Field>
                    <Form.Field error={error}>
                        <label>Password</label>
                        <Input value={password} type="password" onChange={(_e, { value }) => this.updateFields("password", value)} placeholder="Password..." />
                    </Form.Field>
                    <Form.Field error={error}>
                        <label>Repeat Password</label>
                        <Input value={repeatedPassword} type="password" onChange={(_e, { value }) => this.updateFields("repeatedPassword", value)} placeholder="Repeat password..." />
                    </Form.Field>
                    <Button type="button" content="Create" color="blue" onClick={() => this.submit()} loading={submitted} />
                </Form>
            </React.StrictMode>
        );
    }
}

export default UserCreation;