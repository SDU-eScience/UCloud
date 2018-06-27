import * as React from "react";
import { Form, Input, Button } from "semantic-ui-react";

interface UserSettingsState {
    currentPassword: string
    newPassword: string
    repeatedPassword: string
    error: boolean
}

type UserSettingsFields = keyof UserSettingsState

class UserSettings extends React.Component<{}, UserSettingsState> {
    constructor(props) {
        super(props);
        this.state = {
            currentPassword: "",
            newPassword: "",
            repeatedPassword: "",
            error: false
        }
    }

    updateField(field: UserSettingsFields, value: string | boolean): void {
        const state = { ...this.state }
        state[field] = value;
        state.error = false;
        this.setState(() => state);
    }

    validateAndSubmit(): void {
        let error = false;
        const { currentPassword, newPassword, repeatedPassword } = this.state;
        if (!currentPassword || !newPassword || !repeatedPassword) {
            error = true;
        }
        if (newPassword !== repeatedPassword) {
            error = true;
        }
        this.setState(() => ({ error }));
        if (!error) {
            // submit
        }
    }

    render() {
        const { error, currentPassword, newPassword, repeatedPassword } = this.state;
        return (
            <React.StrictMode>
                <Form>
                    <Form.Field error={error && !currentPassword}>
                        <label>Current password</label>
                        <Input value={currentPassword} onChange={(e, { value }) => this.updateField("currentPassword", value)} placeholder="Old password" />
                    </Form.Field>
                    <Form.Field error={error && (!newPassword || newPassword !== repeatedPassword)}>
                        <label>New password</label>
                        <Input value={newPassword} onChange={(e, { value }) => this.updateField("newPassword", value)} placeholder="New password" />
                    </Form.Field>
                    <Form.Field error={error && (!repeatedPassword || newPassword !== repeatedPassword)}>
                        <label>Repeat password</label>
                        <Input value={repeatedPassword} onChange={(e, { value }) => this.updateField("repeatedPassword", value)} placeholder="Repeat password" />
                    </Form.Field>
                    <Button type="button" color="blue" onClick={() => this.validateAndSubmit()} content="Submit" />
                </Form>
            </React.StrictMode>
        );
    }
}

export default UserSettings;