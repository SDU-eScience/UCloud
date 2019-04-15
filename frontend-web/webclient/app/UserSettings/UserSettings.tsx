import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { defaultErrorHandler } from "UtilityFunctions";
import { UserSettingsFields, UserSettingsState } from ".";
import { TwoFactorSetup } from "./TwoFactorSetup";
import * as Heading from "ui-components/Heading";
import { MainContainer } from "MainContainer/MainContainer";
import { Flex, Box, Icon, Input, Button, Label } from "ui-components";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { setActivePage } from "Navigation/Redux/StatusActions";
import { SidebarPages } from "ui-components/Sidebar";
import { SnackType, AddSnackOperation } from "Snackbar/Snackbars";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";

class UserSettings extends React.Component<UserSettingsOperations, UserSettingsState> {
    constructor(props: Readonly<UserSettingsOperations>) {
        super(props);
        this.state = this.initialState();
    }

    initialState = (): UserSettingsState => ({
        promiseKeeper: new PromiseKeeper(),
        currentPassword: "",
        newPassword: "",
        repeatedPassword: "",
        error: false,
        repeatPasswordError: false
    });

    private updateField(field: UserSettingsFields, value: string | boolean): void {
        const state = { ...this.state }
        state[field] = value;
        state.error = false;
        state.repeatPasswordError = false;
        this.setState(() => state);
    }

    private async validateAndSubmit(e: React.SyntheticEvent): Promise<void> {
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
            try {
                await this.state.promiseKeeper.makeCancelable(Cloud.post("/auth/users/password",{ currentPassword, newPassword }, "")).promise;
            
                this.props.addSnack({ message: "Password successfully changed", type: SnackType.Success });
                this.setState(() => this.initialState());

            } catch (e) {
                let status = defaultErrorHandler(e);
                this.setState(() => ({ error: true }));
            };
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

        const passwordUser = Cloud.principalType === "password";

        return (
            <Flex alignItems="center" flexDirection="column">
                <Box width={0.7}>
                    <MainContainer
                        header={<Heading.h1>User Settings</Heading.h1>}
                        main={
                            <>
                                {passwordUser ? <form onSubmit={e => this.validateAndSubmit(e)}>
                                    <Box mt="0.5em" pt="0.5em">
                                        <Label>
                                            Current Password
                                            <Input
                                                value={currentPassword}
                                                type="password"
                                                placeholder={"Current password"}
                                                onChange={({ target: { value } }) => this.updateField("currentPassword", value)}
                                            />
                                            {error && !currentPassword ? <Icon name="warning" color="red" /> : null}
                                        </Label>
                                    </Box>
                                    <Box mt="0.5em" pt="0.5em">
                                        <Label>
                                            New Password
                                            <Input
                                                value={newPassword}
                                                type="password"
                                                onChange={({ target: { value } }) => this.updateField("newPassword", value)}
                                                placeholder="New password"
                                            />
                                            {error && !newPassword ? <Icon name="warning" color="red" /> : null}
                                        </Label>
                                    </Box>
                                    <Box mt="0.5em" pt="0.5em">
                                        <Label>
                                            Repeat new password
                                            <Input
                                                value={repeatedPassword}
                                                type="password"
                                                onChange={({ target: { value } }) => this.updateField("repeatedPassword", value)}
                                                placeholder="Repeat password"
                                            />
                                            {error && !repeatedPassword ? <Icon name="warning" color="red" /> : null}
                                        </Label>
                                    </Box>
                                    <Button
                                        mt="1em"
                                        type="submit"
                                        color="green"
                                    >
                                        Change password
                                    </Button>
                                </form> : null}
                                <TwoFactorSetup />
                            </>
                        }
                    />
                </Box>
            </Flex>
        );
    }
}

interface UserSettingsOperations extends AddSnackOperation {
    setActivePage: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): UserSettingsOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    addSnack: snack => dispatch(addSnack(snack))
});

export default connect(() => ({}), mapDispatchToProps)(UserSettings);