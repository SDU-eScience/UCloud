import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import PromiseKeeper from "PromiseKeeper";
import { defaultErrorHandler } from "UtilityFunctions";
import { UserCreationState, UserCreationField } from ".";
import { Input, Label, LoadingButton } from "ui-components";
import * as Heading from "ui-components/Heading";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { setActivePage } from "Navigation/Redux/StatusActions";
import { SidebarPages } from "ui-components/Sidebar";
import { MainContainer } from "MainContainer/MainContainer";
import { AddSnackOperation, SnackType } from "Snackbar/Snackbars";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";

class UserCreation extends React.Component<UserCreationOperations, UserCreationState> {
    constructor(props: Readonly<UserCreationOperations>) {
        super(props);
        props.setActivePage();
        this.state = this.initialState;
    }

    private readonly initialState: UserCreationState = {
        promiseKeeper: new PromiseKeeper(),
        submitted: false,
        username: "",
        password: "",
        repeatedPassword: "",
        usernameError: false,
        passwordError: false
    };

    componentWillUnmount() {
        this.state.promiseKeeper.cancelPromises();
    }

    private updateFields(field: UserCreationField, value: string) {
        const state = { ...this.state }
        state[field] = value;
        if (field === "username") state.usernameError = false;
        else if (field === "password" || field === "repeatedPassword") state.passwordError = false;
        this.setState(() => state);
    }

    private async submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let usernameError = false;
        let passwordError = false;
        const { username, password, repeatedPassword } = this.state;
        if (!username) usernameError = true;
        if (!password || password !== repeatedPassword) passwordError = true;
        this.setState(() => ({ usernameError, passwordError }));
        if (!usernameError && !passwordError) {
            try {
                await this.state.promiseKeeper.makeCancelable(Cloud.post("/auth/users/register", { username, password }, "")).promise;
                this.props.addSnack({ message: `User '${username}' successfully created`, type: SnackType.Success });
                this.setState(() => this.initialState);
            } catch (e) {
                const status = defaultErrorHandler(e, this.props.addSnack);
                if (status == 400)  this.setState(() => ({ usernameError: true }));
            };
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


        const header = (<><Heading.h1>User Creation</Heading.h1>
            <p>Admins can create new users on this page.</p></>)

        return (
            <MainContainer
                header={header}
                headerSize={120}
                main={<form onSubmit={e => this.submit(e)}>
                    <Label mb="1em">
                        Username
                        <Input
                            value={username}
                            color={usernameError ? "red" : "gray"}
                            onChange={({ target: { value } }) => this.updateFields("username", value)}
                            placeholder="Username..."
                        />
                    </Label>
                    <Label mb="1em">
                        Password
                        <Input
                            value={password}
                            type="password"
                            color={passwordError ? "red" : "gray"}
                            onChange={({ target: { value } }) => this.updateFields("password", value)}
                            placeholder="Password..."
                        />
                    </Label>
                    <Label mb="1em">
                        Repeat password
                        <Input
                            value={repeatedPassword}
                            type="password"
                            color={passwordError ? "red" : "gray"}
                            onChange={({ target: { value } }) => this.updateFields("repeatedPassword", value)}
                            placeholder="Repeat password..."
                        />
                    </Label>
                    <LoadingButton
                        type="submit"
                        content="Create user"
                        hovercolor="darkGreen"
                        color="green"
                        loading={submitted}
                    />
                </form>}
            />
        );
    }
}

interface UserCreationOperations extends AddSnackOperation {
    setActivePage: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): UserCreationOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
    addSnack: snack => dispatch(addSnack(snack))
});

export default connect(null, mapDispatchToProps)(UserCreation);