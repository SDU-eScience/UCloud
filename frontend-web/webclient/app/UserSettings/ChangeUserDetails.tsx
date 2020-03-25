import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {useCallback, useEffect, useRef, useState} from "react";
import * as React from "react";
import {Box, Button, Icon, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UF from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";

export const ChangeUserDetails: React.FunctionComponent<{ setLoading: (loading: boolean) => void }> = props => {

    const userFirstNames = useRef<HTMLInputElement>(null);
    const userLastName = useRef<HTMLInputElement>(null);
    const userEmail = useRef<HTMLInputElement>(null);

    const [commandLoading, invokeCommand] = useAsyncCommand();
    // FIXME USE REDUCER INSTEAD!!!!!!!!!!!

    const [placeHolderFirstNames, setPlaceHolderFirstNames] = useState("Enter First Name(s)");
    const [placeHolderLastName, setPlaceHolderLastName] = useState("Enter Last Name");
    const [placeHolderEmail, setPlaceHolderEmail] = useState("Enter Email");

    const info = useCallback( async () => {

        const user = await invokeCommand( {
            reloadId: Math.random(),
            method: "GET",
            path: "auth/users/userInfo",
            context: ""
        });

        setPlaceHolderFirstNames(user.firstNames ?? "Enter First Name(s)");
        setPlaceHolderLastName(user.lastName ?? "Enter Last Name");
        setPlaceHolderEmail(user.email ?? "Enter Email");
    },[]);

    useEffect(() => {
        info()
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
                firstNames: firstNames?.value,
                lastName: lastName?.value,
                email: email?.value
            }
        }) !== null;

        if (!wasSuccessful) {
            snackbarStore.addSnack({message: "Failed to update user information", type: SnackType.Failure});
        } else {
            snackbarStore.addSnack({message: "User information updated", type: SnackType.Failure});
        }


    }, [commandLoading, userFirstNames.current, userLastName.current, userEmail.current]);

    if (Client.principalType !== "password") return null;

    return (
        <Box mb={16}>
            <Heading.h2>Change User Details</Heading.h2>
            <form onSubmit={onSubmit}>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                       First names
                        <Input
                            ref={userFirstNames}
                            type="text"
                            placeholder={placeHolderFirstNames}
                        />
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Last name
                        <Input
                            ref={userLastName}
                            type="text"
                            placeholder= {placeHolderLastName}
                        />
                    </Label>
                </Box>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Email
                        <Input
                            ref={userEmail}
                            type="email"
                            placeholder={placeHolderEmail}
                        />
                    </Label>
                </Box>


                <Button
                    mt={"1em"}
                    type={"submit"}
                    color="green"
                    disabled={commandLoading}
                >
                    Update Information
                </Button>
            </form>
        </Box>
    );
};
