import {Client} from "@/Authentication/HttpClientInstance";
import {setLoading, usePage} from "@/Navigation/Redux";
import {usePromiseKeeper} from "@/PromiseKeeper";
import * as React from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Button, Input, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {defaultErrorHandler} from "@/UtilityFunctions";
import {UserCreationState} from ".";
import {useDispatch} from "react-redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const initialState: UserCreationState = {
    username: "",
    password: "",
    repeatedPassword: "",
    email: "",
    firstnames: "",
    lastname: "",
    usernameError: false,
    passwordError: false,
    emailError: false,
    firstnamesError: false,
    lastnameError: false
};

type Action<T, B> = {payload: B; type: T};
type UpdateUsername = Action<"UpdateUsername", {username: string}>;
type UpdatePassword = Action<"UpdatePassword", {password: string}>;
type UpdateRepeatedPassword = Action<"UpdateRepeatedPassword", {repeatedPassword: string}>;
type UpdateEmail = Action<"UpdateEmail", {email: string}>;
type UpdateFirstnames = Action<"UpdateFirstnames", {firstnames: string}>;
type UpdateLastname = Action<"UpdateLastname", {lastname: string}>;
type UpdateErrors = Action<"UpdateErrors", {
    usernameError: boolean; passwordError: boolean; emailError: boolean, firstnamesError: boolean, lastnameError: boolean
}>;
type Reset = Action<"Reset", {}>;
type UserCreationActionType = |
    UpdateUsername | UpdatePassword | UpdateRepeatedPassword | UpdateErrors | UpdateEmail | UpdateFirstnames | UpdateLastname | Reset;

const reducer = (state: UserCreationState, action: UserCreationActionType): UserCreationState => {
    switch (action.type) {
        case "UpdateUsername":
        case "UpdateRepeatedPassword":
        case "UpdateErrors":
        case "UpdateEmail":
        case "UpdatePassword":
        case "UpdateFirstnames":
        case "UpdateLastname":
            return {...state, ...action.payload};
        case "Reset":
            return initialState;
    }
};

function UserCreation(): React.ReactNode {
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);
    const reduxDispatch = useDispatch();
    const [submitted, setSubmitted] = React.useState(false);
    const promiseKeeper = usePromiseKeeper();

    usePage("User Creation", SidebarTabId.ADMIN);

    if (!Client.userIsAdmin) return null;

    const {
        usernameError,
        passwordError,
        emailError,
        firstnamesError,
        lastnameError,
        username,
        password,
        repeatedPassword,
        email,
        firstnames,
        lastname
    } = state;

    return (
        <Box maxWidth={"1200px"} mx="auto">
            <Heading.h1>User Creation</Heading.h1>
            <p>Admins can create new users on this page.</p>
            <form autoComplete="off" onSubmit={e => submit(e)}>
                <Label mb="1em">
                    Username
                    <Input
                        autoComplete="off"
                        value={username}
                        error={usernameError}
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
                        onChange={e => dispatch({type: "UpdateEmail", payload: {email: e.target.value}})}
                        placeholder="Email..."
                    />
                </Label>
                <Label mb="1em">
                    First Names
                    <Input
                        value={firstnames}
                        type="firstnames"
                        error={firstnamesError}
                        onChange={e => dispatch({type: "UpdateFirstnames", payload: {firstnames: e.target.value}})}
                        placeholder="First names..."
                    />
                </Label>
                <Label mb="1em">
                    Last Name
                    <Input
                        value={lastname}
                        type="lastname"
                        error={lastnameError}
                        onChange={e => dispatch({type: "UpdateLastname", payload: {lastname: e.target.value}})}
                        placeholder="Last name..."
                    />
                </Label>
                <Button
                    mt="8px"
                    type="submit"
                    color="successMain"
                    disabled={submitted}
                >
                    Create user
                </Button>
            </form>
        </Box>
    );

    async function submit(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        let hasUsernameError = false;
        let hasPasswordError = false;
        let hasEmailError = false;
        let hasFirstnamesError = false;
        let hasLastnameError = false;
        const {username, password, repeatedPassword, email, firstnames, lastname} = state;
        if (!username) hasUsernameError = true;
        if (!password || password !== repeatedPassword) {
            hasPasswordError = true;
            snackbarStore.addFailure("Passwords do not match.", false);
        }
        if (!email) {
            hasEmailError = true;
            snackbarStore.addFailure("Email is required", false);
        }
        if (!firstnames) {
            hasFirstnamesError = true;
            snackbarStore.addFailure("First names is required", false);
        }
        if (!lastname) {
            hasLastnameError = true;
            snackbarStore.addFailure("Last name is required", false);
        }
        dispatch({
            type: "UpdateErrors",
            payload: {
                usernameError: hasUsernameError,
                passwordError: hasPasswordError,
                emailError: hasEmailError,
                firstnamesError: hasFirstnamesError,
                lastnameError: hasLastnameError
            }
        });

        if (!hasUsernameError && !hasPasswordError && !hasEmailError && !hasFirstnamesError && !hasLastnameError) {
            try {
                reduxDispatch(setLoading(true));
                setSubmitted(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/auth/users/register", [{username, password, email, firstnames, lastname}], "")
                ).promise;
                snackbarStore.addSuccess(`User '${username}' successfully created`, false);
                dispatch({type: "Reset", payload: {}});
            } catch (err) {
                const status = defaultErrorHandler(err);
                if (status === 409) dispatch({
                    type: "UpdateErrors",
                    payload: {usernameError: true, passwordError: false, emailError: false, firstnamesError: false, lastnameError: false}
                });
            } finally {
                reduxDispatch(setLoading(false));
                setSubmitted(false);
            }
        }
    }
}


export default UserCreation;
