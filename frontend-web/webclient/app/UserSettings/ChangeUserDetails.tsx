import {useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import * as React from "react";
import {useCallback, useEffect, useRef} from "react";
import {Box, Button, Checkbox, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {snackbarStore} from "Snackbar/SnackbarStore";

interface UserDetailsState {
    placeHolderFirstNames: string;
    placeHolderLastName: string;
    placeHolderEmail: string;
}

const initialState: UserDetailsState = {
    placeHolderFirstNames: "Enter First Name(s)",
    placeHolderLastName: "Enter Last Name",
    placeHolderEmail: "Enter Email"
};

type UpdatePlaceholderFirstNames = PayloadAction<"UpdatePlaceholders", UserDetailsState>;

const reducer = (state: UserDetailsState, action: UpdatePlaceholderFirstNames): UserDetailsState => {
    switch (action.type) {
        case "UpdatePlaceholders":
            return {...state, ...action.payload};
    }
};

export const ChangeUserDetails: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = () => {
    const userFirstNames = useRef<HTMLInputElement>(null);
    const userLastName = useRef<HTMLInputElement>(null);
    const userEmail = useRef<HTMLInputElement>(null);

    const [commandLoading, invokeCommand] = useCloudCommand();
    const [state, dispatch] = React.useReducer(reducer, initialState, () => initialState);

    const info = useCallback(async () => {

        const user = await invokeCommand({
            reloadId: Math.random(),
            method: "GET",
            path: "auth/users/userInfo",
            context: ""
        });

        dispatch({
            type: "UpdatePlaceholders",
            payload: {
                placeHolderFirstNames: user.firstNames ?? "Enter First Name(s)",
                placeHolderLastName: user.lastName ?? "Enter Last Name",
                placeHolderEmail: user.email ?? "Enter Email"
            }
        });
    }, []);

    useEffect(() => {
        info();
    }, []);

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const firstNames = userFirstNames.current;
        const lastName = userLastName.current;
        const email = userEmail.current;

        const wasSuccessful = await invokeCommand({
            reloadId: Math.random(),
            method: "POST",
            path: "/auth/users/updateUserInfo",
            context: "",
            payload: {
                firstNames: firstNames?.value ? firstNames.value : undefined,
                lastName: lastName?.value ? lastName.value : undefined,
                email: email?.value ? email.value : undefined
            }
        }) !== null;

        if (!wasSuccessful) {
            snackbarStore.addFailure("Failed to update user information", false);
        } else {
            snackbarStore.addSuccess("User information updated", false);
        }
    }, [commandLoading, userFirstNames.current, userLastName.current, userEmail.current]);

    if (Client.principalType !== "password") {
        return null
    }
    else {
        return (
            <Box mb={16}>
                <Heading.h2>Change User Details</Heading.h2>
                <form onSubmit={onSubmit}>
                    <Box mt="0.5em" pt="0.5em">
                        <Label>
                            First names
                            <Input
                                ref={userFirstNames}
                                style={{maxWidth: "700px"}}
                                type="text"
                                placeholder={state.placeHolderFirstNames}
                            />
                        </Label>
                    </Box>

                    <Box mt="0.5em" pt="0.5em">
                        <Label>
                            Last name
                            <Input
                                ref={userLastName}
                                style={{maxWidth: "700px"}}
                                type="text"
                                placeholder={state.placeHolderLastName}
                            />
                        </Label>
                    </Box>
                    <Box mt="0.5em" pt="0.5em">
                        <Label>
                            Email
                            <Input
                                ref={userEmail}
                                style={{maxWidth: "700px"}}
                                type="email"
                                placeholder={state.placeHolderEmail}
                            />
                        </Label>
                    </Box>
                    <Button
                        mt="1em"
                        type="submit"
                        color="green"
                        disabled={commandLoading}
                    >
                        Update Information
                    </Button>
                </form>
            </Box>
        );
    }
};
