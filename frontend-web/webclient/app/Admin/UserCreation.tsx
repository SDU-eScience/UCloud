import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage, setLoading, SetStatusLoading} from "Navigation/Redux/StatusActions";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {defaultErrorHandler} from "UtilityFunctions";
import {UserCreationState} from ".";

const initialState: UserCreationState = {
    username: "",
    password: "",
    repeatedPassword: "",
    usernameError: false,
    passwordError: false
};

function UserCreation(props: UserCreationOperations) {
    // FIXME: Use reducer instead, or break into smaller ones.
    const [state, setState] = React.useState(initialState);
    const [submitted, setSubmitted] = React.useState(false);
    const promiseKeeper = usePromiseKeeper();

    React.useEffect(() => {
        props.setActivePage();
    }, []);

    if (!Client.userIsAdmin) return null;

    const {
        usernameError,
        passwordError,
        username,
        password,
        repeatedPassword
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
                                autocomplete="off"
                                value={username}
                                error={usernameError}
                                onChange={e => updateField("username", e.target.value)}
                                placeholder="Username..."
                            />
                        </Label>
                        <Label mb="1em">
                            Password
                            <Input
                                value={password}
                                type="password"
                                error={passwordError}
                                onChange={e => updateField("password", e.target.value)}
                                placeholder="Password..."
                            />
                        </Label>
                        <Label mb="1em">
                            Repeat password
                            <Input
                                value={repeatedPassword}
                                type="password"
                                error={passwordError}
                                onChange={e => updateField("repeatedPassword", e.target.value)}
                                placeholder="Repeat password..."
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

    function updateField(field: keyof UserCreationState, value: string) {
        if (field === "username") state.usernameError = false;
        else if (field === "password" || field === "repeatedPassword") state.passwordError = false;
        setState({...state, [field]: value});
    }

    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let hasUsernameError = false;
        let hasPasswordError = false;
        const {username, password, repeatedPassword} = state;
        if (!username) hasUsernameError = true;
        if (!password || password !== repeatedPassword) {
            hasPasswordError = true;
            snackbarStore.addFailure("Passwords do not match.");
        }
        setState({...state, usernameError: hasUsernameError, passwordError: hasPasswordError});
        if (!hasUsernameError && !hasPasswordError) {
            try {
                props.setLoading(true);
                setSubmitted(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/auth/users/register", {username, password}, "")
                ).promise;
                snackbarStore.addSnack({message: `User '${username}' successfully created`, type: SnackType.Success});
                setState(initialState);
            } catch (e) {
                const status = defaultErrorHandler(e);
                if (status === 409) setState({...state, usernameError: true});
            } finally {
                props.setLoading(false);
                setSubmitted(false);
            }
        }
    }
}

interface UserCreationOperations extends SetStatusLoading {
    setActivePage: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): UserCreationOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(UserCreation);
