import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import PromiseKeeper from "PromiseKeeper";
import {defaultErrorHandler} from "UtilityFunctions";
import {UserCreationState, UserCreationField} from ".";
import {Input, Label, Button} from "ui-components";
import * as Heading from "ui-components/Heading";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setActivePage, SetStatusLoading, setLoading} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {MainContainer} from "MainContainer/MainContainer";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";

const initialState: UserCreationState = {
    submitted: false,
    username: "",
    password: "",
    repeatedPassword: "",
    usernameError: false,
    passwordError: false
};

function UserCreation(props: UserCreationOperations) {
    // Use reducer instead, or break into smaller ones.
    const [state, setState] = React.useState(initialState);
    const [promiseKeeper] = React.useState(new PromiseKeeper());

    React.useEffect(() => {
        props.setActivePage();
        return () => promiseKeeper.cancelPromises();
    }, []);

    function updateFields(field: UserCreationField, value: string) {
        if (field === "username") state.usernameError = false;
        else if (field === "password" || field === "repeatedPassword") state.passwordError = false;
        setState({...state, [field]: value});
    }

    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let usernameError = false;
        let passwordError = false;
        const {username, password, repeatedPassword} = state;
        if (!username) usernameError = true;
        if (!password || password !== repeatedPassword) passwordError = true;
        setState({...state, usernameError, passwordError});
        if (!usernameError && !passwordError) {
            try {
                props.setLoading(true);
                await promiseKeeper.makeCancelable(Cloud.post("/auth/users/register", {username, password}, "")).promise;
                snackbarStore.addSnack({message: `User '${username}' successfully created`, type: SnackType.Success});
                setState(() => initialState);
            } catch (e) {
                const status = defaultErrorHandler(e);
                if (status == 400) {
                    snackbarStore.addSnack({
                        message: "User already exists",
                        type: SnackType.Information
                    });
                    setState({...state, usernameError: true});
                }
            } finally {
                props.setLoading(false);
            }
        }
    }


    if (!Cloud.userIsAdmin) return null;

    const {
        usernameError,
        passwordError,
        username,
        password,
        repeatedPassword,
        submitted
    } = state;


    const header = (<><Heading.h1>User Creation</Heading.h1>
        <p>Admins can create new users on this page.</p></>)

    return (
        <MainContainer
            header={header}
            headerSize={120}
            main={<form onSubmit={e => submit(e)}>
                <Label mb="1em">
                    Username
                    <Input
                        value={username}
                        color={usernameError ? "red" : "gray"}
                        onChange={({target: {value}}) => updateFields("username", value)}
                        placeholder="Username..."
                    />
                </Label>
                <Label mb="1em">
                    Password
                    <Input
                        value={password}
                        type="password"
                        color={passwordError ? "red" : "gray"}
                        onChange={({target: {value}}) => updateFields("password", value)}
                        placeholder="Password..."
                    />
                </Label>
                <Label mb="1em">
                    Repeat password
                    <Input
                        value={repeatedPassword}
                        type="password"
                        color={passwordError ? "red" : "gray"}
                        onChange={({target: {value}}) => updateFields("repeatedPassword", value)}
                        placeholder="Repeat password..."
                    />
                </Label>
                <Button
                    type="submit"
                    color="green"
                    disabled={submitted}
                >Create user</Button>
            </form>}
        />
    );
}

interface UserCreationOperations extends SetStatusLoading {
    setActivePage: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): UserCreationOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(UserCreation);
