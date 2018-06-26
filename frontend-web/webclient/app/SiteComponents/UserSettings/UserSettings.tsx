import * as React from "react";
import { Form, Input, Button } from "semantic-ui-react";


interface UserSettingsState {
    fields: {
        oldPassword: string
        newPassword: string
        repeatedPassword: string
    }
    error: boolean
}
class UserSettings extends React.Component<{}, UserSettingsState> {
    constructor(props) {
        super(props);
        this.state = {
            fields: {
                oldPassword: "",
                newPassword: "",
                repeatedPassword: ""
            },
            error: false
        }
    }

    updateField(fieldName: string, value: string): void {
        
    }

    validateAndSubmit(): void {
        
    }    

    render() {
        return (
            <React.StrictMode>
                <Form>
                    <Form.Field label="Old password">
                        <Input placeholder="Old password" />
                    </Form.Field>
                    <Form.Field label="New password">
                        <Input placeholder="New password" />
                    </Form.Field>
                    <Form.Field label="Repeat password">
                        <Input placeholder="Repeat password" />
                    </Form.Field>
                    <Button type="button" onClick={() => this.validateAndSubmit()} content="Submit"/>
                </Form>
            </React.StrictMode>
        );
    }
}

export default UserSettings;