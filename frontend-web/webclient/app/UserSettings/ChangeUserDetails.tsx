import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {useCallback, useEffect, useRef, useState} from "react";
import * as React from "react";
import {Box, Button, Checkbox, Icon, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UF from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";
import {stopPropagation} from "UtilityFunctions";

export const ChangeUserDetails: React.FunctionComponent<{ setLoading: (loading: boolean) => void }> = props => {

    const userFirstNames = useRef<HTMLInputElement>(null);
    const userLastName = useRef<HTMLInputElement>(null);
    const userEmail = useRef<HTMLInputElement>(null);

    const [commandLoading, invokeCommand] = useAsyncCommand();
    // FIXME USE REDUCER INSTEAD!!!!!!!!!!!

    const [placeHolderFirstNames, setPlaceHolderFirstNames] = useState("Enter First Name(s)");
    const [placeHolderLastName, setPlaceHolderLastName] = useState("Enter Last Name");
    const [placeHolderEmail, setPlaceHolderEmail] = useState("Enter Email");
    const [placeHolderWantEmails, setWantEmail] = useState(true)

    const info = useCallback( async () => {

        const user = await invokeCommand( {
            reloadId: Math.random(),
            method: "GET",
            path: "auth/users/userInfo",
            context: ""
        });

        const wantEmails = await invokeCommand( {
            method: "POST",
            path: "/auth/users/wantEmails",
            context: "",
            payload: {
                username: null
            }
        });

        setWantEmail(wantEmails ?? true);
        setPlaceHolderFirstNames(user.firstNames ?? "Enter First Name(s)");
        setPlaceHolderLastName(user.lastName ?? "Enter Last Name");
        setPlaceHolderEmail(user.email ?? "Enter Email");
    },[]);


    const toogleSubscription = useCallback( async () => {
        await invokeCommand( {
            method: "POST",
            path: "/auth/users/toggleEmail",
            context: ""
        });
    }, []);

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
            snackbarStore.addFailure("Failed to update user information", false);
        } else {
            snackbarStore.addSuccess("User information updated", false);
        }


    }, [commandLoading, userFirstNames.current, userLastName.current, userEmail.current]);

    if (Client.principalType !== "password") {
        return (
            <Box mb={16}>
                <Heading.h2>Change User Details</Heading.h2>
                    <Label ml={10} width="auto">
                        <Checkbox
                            size={27}
                            onClick={toogleSubscription}
                            checked={placeHolderWantEmails}
                            onChange={info}
                        />
                        <Box as={"span"}>Receive emails</Box>
                    </Label>
            </Box>
        );
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
                                placeholder={placeHolderLastName}
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

                    <Label ml={10} width="auto">
                        <Checkbox
                            size={27}
                            onClick={toogleSubscription}
                            checked={placeHolderWantEmails}
                            onChange={info}
                        />
                        <Box as={"span"}>Receive emails</Box>
                    </Label>


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
    }
};
