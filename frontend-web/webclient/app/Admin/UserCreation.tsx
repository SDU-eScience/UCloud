import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {setLoading, SetStatusLoading, useTitle} from "Navigation/Redux/StatusActions";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {defaultErrorHandler} from "UtilityFunctions";
import {UserCreationState} from ".";

const initialState: UserCreationState = {
    username: "",
    password: "",
    repeatedPassword: "",
    email: "",
    usernameError: false,
    passwordError: false,
    emailError: false
};

type Action<T, B> = {payload: B; type: T};
type UpdateUsername = Action<"UpdateUsername", {username: string}>;
type UpdatePassword = Action<"UpdatePassword", {password: string}>;
type UpdateRepeatedPassword = Action<"UpdateRepeatedPassword", {repeatedPassword: string}>;
type UpdateEmail = Action<"UpdateEmail", {email: string}>;
type UpdateErrors = Action<"UpdateErrors", {usernameError: boolean; passwordError: boolean; emailError: boolean}>;
type Reset = Action<"Reset", {}>;
type UserCreationActionType = |
    UpdateUsername | UpdatePassword | UpdateRepeatedPassword | UpdateErrors | UpdateEmail | Reset;

const reducer = (state: UserCreationState, action: UserCreationActionType): UserCreationState => {
    switch (action.type) {
        case "UpdateUsername":
        case "UpdateRepeatedPassword":
        case "UpdateErrors":
        case "UpdateEmail":
        case "UpdatePassword":
            return {...state, ...action.payload};
        case "Reset":
            return initialState;

    }
};

function UserCreation(props: SetStatusLoading): JSX.Element | null {
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);
    const [submitted, setSubmitted] = React.useState(false);
    const promiseKeeper = usePromiseKeeper();

    useTitle("User Creation");
    useSidebarPage(SidebarPages.Admin);

    if (!Client.userIsAdmin) return null;

    const {
        usernameError,
        passwordError,
        emailError,
        username,
        password,
        repeatedPassword,
        email
    } = state;

    return (
        <MainContainer
            header={<Heading.h1>User Creation</Heading.h1>}
            headerSize={64}
            main={(
                <>
                    <p>Admins can create new users on this page.</p>
                    <form autoComplete="off" onSubmit={e => submit(e)}>
                        <Label mb="1em">
                            Username
                            <Input
                                autoComplete="off"
                                value={username}
                                error={usernameError}
                                required
                                onChange={e => dispatch({type: "UpdateUsername", payload: {username: e.target.value}})}
                                placeholder="Username..."
                            />
                        </Label>
                        <Label mb="1em">
                            Password
                            <Input
                                value={password}
                                type="password"
                                error={passwordError}
                                required
                                onChange={e => dispatch({type: "UpdatePassword", payload: {password: e.target.value}})}
                                placeholder="Password..."
                            />
                        </Label>
                        <Label mb="1em">
                            Repeat password
                            <Input
                                value={repeatedPassword}
                                type="password"
                                error={passwordError}
                                required
                                onChange={e => dispatch({type: "UpdateRepeatedPassword", payload: {repeatedPassword: e.target.value}})}
                                placeholder="Repeat password..."
                            />
                        </Label>
                        <Label mb="1em">
                            Email
                            <Input
                                value={email}
                                type="email"
                                error={emailError}
                                required
                                onChange={e => dispatch({type: "UpdateEmail", payload: {email: e.target.value}})}
                                placeholder="Email..."
                            />
                        </Label>
                        <Button
                            type="submit"
                            color="green"
                            disabled={submitted}
                        >
                            Create user
                        </Button>
                    </form>
                </>
            )}
        />
    );

    async function submit(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        let hasUsernameError = false;
        let hasPasswordError = false;
        let hasEmailError = false;
        const {username, password, repeatedPassword, email} = state;
        if (!username) hasUsernameError = true;
        if (!password || password !== repeatedPassword) {
            hasPasswordError = true;
            snackbarStore.addFailure("Passwords do not match.", false);
        }
        if (!email) {
            hasEmailError = true;
            snackbarStore.addFailure("Email is required", false);
        }
        dispatch({
            type: "UpdateErrors",
            payload: {usernameError: hasUsernameError, passwordError: hasPasswordError, emailError: hasEmailError}
        });

        if (!hasUsernameError && !hasPasswordError && !hasEmailError) {
            try {
                props.setLoading(true);
                setSubmitted(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/auth/users/register", {username, password, email}, "")
                ).promise;
                snackbarStore.addSuccess(`User '${username}' successfully created`, false);
                dispatch({type: "Reset", payload: {}});
            } catch (err) {
                const status = defaultErrorHandler(err);
                if (status === 409) dispatch({
                    type: "UpdateErrors",
                    payload: {usernameError: true, passwordError: false, emailError: false}
                });
            } finally {
                props.setLoading(false);
                setSubmitted(false);
            }
        }
    }
}

const mapDispatchToProps = (dispatch: Dispatch): SetStatusLoading => ({
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(UserCreation);
