import {apiRetrieve, apiUpdate, callAPI, callAPIWithErrorHandler, useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {Box, Button, Input, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PayloadAction} from "@reduxjs/toolkit";
import ResearchFields from "@/UserSettings/ResearchField.json";
import Positions from "@/UserSettings/Position.json";
import OrgMapping from "@/UserSettings/OrganizationMapping.json";
import {Client} from "@/Authentication/HttpClientInstance";

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

type UpdatePlaceholderFirstNames = PayloadAction<UserDetailsState, "UpdatePlaceholders">;

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
    const [message, setMessage] = useState<string | null>(null);

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
                placeHolderFirstNames: user?.firstNames ?? "Enter First Name(s)",
                placeHolderLastName: user?.lastName ?? "Enter Last Name",
                placeHolderEmail: user?.email ?? "Enter Email"
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
            setMessage("Failed to update user information");
        } else {
            setMessage("Success! Please check your email to verify the update.")
        }
    }, [commandLoading, userFirstNames.current, userLastName.current, userEmail.current]);

    return (
        <Box mb={16}>
            <Heading.h2>Change User Details</Heading.h2>
            <form onSubmit={onSubmit}>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        First names
                        <Input
                            inputRef={userFirstNames}
                            type="text"
                            placeholder={state.placeHolderFirstNames}
                        />
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Last name
                        <Input
                            inputRef={userLastName}
                            type="text"
                            placeholder={state.placeHolderLastName}
                        />
                    </Label>
                </Box>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Email
                        <Input
                            inputRef={userEmail}
                            type="email"
                            placeholder={state.placeHolderEmail}
                        />
                    </Label>
                </Box>
                <Button
                    mt="1em"
                    type="submit"
                    color="successMain"
                    disabled={commandLoading || !!message}
                >
                    {message ?? "Update Information"}
                </Button>
            </form>
        </Box>
    );
};

interface OptionalInfo {
    organizationFullName?: string | null;
    department?: string | null;
    researchField?: string | null;
    position?: string | null;
}

type InfoAndValidation = OptionalInfo & {isValid: boolean};

export function ChangeOrganizationDetails(props: {getValues?: React.RefObject<() => InfoAndValidation>}) {
    const orgFullNameRef = useRef<HTMLInputElement>(null);
    const departmentRef = useRef<HTMLInputElement>(null);
    const researchFieldRef = useRef<HTMLInputElement>(null);
    const positionRef = useRef<HTMLInputElement>(null);

    useLayoutEffect(() => {
        (async () => {
            const info = await callAPI<OptionalInfo>(apiRetrieve({}, "/auth/users", "optionalInfo"));

            orgFullNameRef.current!.value = info.organizationFullName ?? "";
            departmentRef.current!.value = info.department ?? "";
            researchFieldRef.current!.value = info.researchField ?? "";
            positionRef.current!.value = info.position ?? "";
        })();
    }, []);

    const extractValues = React.useCallback((): InfoAndValidation => {
        const organizationFullName = orgFullNameRef.current?.value;
        const department = departmentRef.current?.value
        const researchField = researchFieldRef.current?.value;
        const position = positionRef.current?.value;
        const isValid = !!organizationFullName && !!department && !!researchField && !!position;
        return {
            organizationFullName,
            department,
            researchField,
            position,
            isValid
        }
    }, [orgFullNameRef.current, departmentRef.current, researchFieldRef.current, positionRef.current]);

    React.useEffect(() => {
        if (props.getValues) props.getValues.current = extractValues;
    }, [extractValues])

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        await callAPIWithErrorHandler(
            apiUpdate(extractValues(), "/auth/users", "optionalInfo")
        );

        snackbarStore.addSuccess("Your information has been updated.", false);
    }, []);

    React.useEffect(() => {
        const selectedOrganization = OrgMapping[Client.orgId];
        if (selectedOrganization && orgFullNameRef.current)
            orgFullNameRef.current.value = selectedOrganization;
    }, [orgFullNameRef.current]);

    return (
        <Box mb={16}>
            <Heading.h2>Additional User Information</Heading.h2>
            <form onSubmit={onSubmit}>
                <Organization ref={orgFullNameRef} />
                <Department disabled={false} ref={departmentRef} />
                <OrgField title="Position" placeholder="" ref={positionRef} />
                <OrgField title="Research field(s)" placeholder="Experimental examples" ref={researchFieldRef} />
                {props.getValues ? null : <Button mt="1em" type="submit" color="successMain">Update Information</Button>}
            </form>
        </Box>
    );
}

function OrgField(props: {
    title: string;
    placeholder: string;
    ref: React.RefObject<HTMLInputElement | null>;
    disabled?: boolean;
}): React.ReactElement {
    return <Box mt="0.5em" pt="0.5em">
        <Label>
            {props.title}
            <Input disabled={props.disabled} inputRef={props.ref} type="text" placeholder={"Example: " + props.placeholder} />
        </Label>
    </Box>
};

function Organization(props: {ref: React.RefObject<HTMLInputElement | null>}) {
    return <OrgField disabled ref={props.ref} title="Full name of organization" placeholder="University of Example" />
}

function Department(props: {disabled: boolean; ref: React.RefObject<HTMLInputElement | null>}) {
    // TODO(Jonas): Disable on no value passed from 
    return <OrgField disabled={props.disabled} title="Department" placeholder="Department of Examples" ref={props.ref} />
}