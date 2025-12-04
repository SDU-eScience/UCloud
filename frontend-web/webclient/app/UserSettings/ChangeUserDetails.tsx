import {apiRetrieve, apiUpdate, callAPI, callAPIWithErrorHandler, useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {Box, Button, Input, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PayloadAction} from "@reduxjs/toolkit";
import ResearchFields from "@/UserSettings/ResearchField.json";
import Positions from "@/UserSettings/Position.json";
import KnownDepartments from "@/UserSettings/KnownDepartments.json"
import KnownOrgs from "@/UserSettings/KnownOrgs.json";
import OrgMapping from "@/UserSettings/OrganizationMapping.json";
import {Client} from "@/Authentication/HttpClientInstance";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {injectStyle} from "@/Unstyled";
import {clamp, stopPropagationAndPreventDefault} from "@/UtilityFunctions";

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

const RFIndex = (Math.random() * ResearchFields.filter(it => it.key[1] === ".").length) | 0

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
        const organizationFullName = orgFullNameRef.current?.value; // Non-empty string is enough
        const department = departmentRef.current?.value // validate department relative to org (if known org)
        const researchField = researchFieldRef.current?.value; // Validate that this research-field exists
        const position = positionRef.current?.value; // Validate that it is one of the correct 3 options
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

    const [org, setOrg] = useState(Client.orgId ?? "");

    return (
        <Box mb={16} width="100%">
            <Heading.h2>Additional User Information</Heading.h2>
            <form onSubmit={onSubmit}>
                <NewDataList ref={orgFullNameRef} allowFreetext disabled={!!Client.orgId} items={KnownOrgs} didUpdateQuery={org => {
                    console.log("didUpdate", org);
                    setOrg(org);
                }} onSelect={org => {
                    console.log("onSelect", org.value);
                    setOrg(org.value)
                }} title={"Organization"} placeholder={"University of Knowledge"} />
                <Department org={org} ref={departmentRef} />
                <NewDataList title="Position" placeholder="VIP/TAP/Student" items={Positions} onSelect={() => void 0} allowFreetext={false} ref={positionRef} />
                <NewDataList ref={researchFieldRef} items={ResearchFields} onSelect={() => void 0} allowFreetext={false} title={"Research field"} disabled={false} placeholder={ResearchFields[RFIndex].value} />
                {props.getValues ? null : <Button mt="1em" type="submit" color="successMain">Update Information</Button>}
            </form>
        </Box>
    );
}

function Department(props: {org: string; ref: React.RefObject<HTMLInputElement | null>}) {
    // TODO(Jonas): This is just a reversemapping
    const orgInfo = Object.entries(OrgMapping).find(it => it[1] === props.org);
    const possibleDepartments: "freetext" | {faculty: string; departments: string[]}[] = orgInfo ? KnownDepartments[orgInfo?.[0]] : [];
    const items = React.useMemo((): DataListItem[] => {
        if (possibleDepartments === "freetext") return [];
        console.log(possibleDepartments);
        return possibleDepartments.flatMap(f => {
            if (f.departments.length === 0) return dataListItem(f.faculty, f.faculty, "");
            else return f.departments.map(d => dataListItem(`${f.faculty}/${d}`, `${d} - ${f.faculty}`, ""));
        });
    }, [possibleDepartments]);
    return <NewDataList disabled={!props.org} items={items} onSelect={() => void 0} ref={props.ref} allowFreetext title={"Department/Faculty/Center"} placeholder={"Department of Dreams"} />
}

function dataListItem(key: string, value: string, tags: string, unselectable?: boolean): DataListItem {
    return {
        key, value, tags, unselectable
    };
}

interface DataListItem {
    key: string;
    value: string;
    tags: string;
    unselectable?: boolean;
}

function NewDataList({items, onSelect, allowFreetext, title, disabled, placeholder, ref, didUpdateQuery}: {
    items: DataListItem[];
    onSelect: (arg: DataListItem) => void;
    allowFreetext: boolean;
    title: string;
    disabled?: boolean;
    placeholder: string;
    ref: React.RefObject<HTMLInputElement | null>
    didUpdateQuery?: (val: string) => void;
}) {
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);

    const [searchIndex, setSearchIndex] = useState(-1);

    const result = React.useMemo(() => {
        const result = fuzzySearch(items, ["tags", "value"], query);
        setSearchIndex(index => {
            return clamp(index, -1, result.length - 1);
        });
        return result;
    }, [query]);

    return <Box mt="0.5em" pt="0.5em">
        <Box>{title}</Box>
        <ClickableDropdown open={open} fullWidth onClose={() => {
            setSearchIndex(-1);
        }} trigger={
            <Input placeholder={`Example: ${placeholder}`}
                inputRef={ref}
                disabled={disabled}
                data-allow-freetext={allowFreetext}
                onFocus={() => setOpen(true)}
                onKeyDown={e => {
                    let doPrevent = false;
                    if (e.key === "ArrowDown") {
                        doPrevent = true;
                        setSearchIndex(idx => clamp(idx + 1, 0, result.length - 1));
                    } else if (e.key === "ArrowUp") {
                        doPrevent = true;
                        setSearchIndex(idx => clamp(idx - 1, 0, result.length - 1));
                    } else if (e.key === "Enter") {
                        doPrevent = true;
                        const item = result[searchIndex];
                        if (!item || !ref.current) return;
                        ref.current.value = item.value;
                        didUpdateQuery?.(item.value);
                        onSelect(item);
                    } else if (e.key === "Escape") {
                        doPrevent = true;
                        setOpen(false);
                    }

                    if (doPrevent) {
                        stopPropagationAndPreventDefault(e);
                        return;
                    }
                }}
                onKeyUp={e => {
                    const value = e.target["value"];
                    setQuery(value)
                    didUpdateQuery?.(value);
                }} />}
        >
            {result.map((it, idx) => <Box key={it.key} data-active={searchIndex === idx} className={Unselectables} data-unselectable={it.unselectable} onClick={e => {
                if (it.unselectable) {
                    e.stopPropagation();
                    return;
                }
                if (!ref.current) return;
                setOpen(false);
                ref.current.value = it.value;
                onSelect(it);
            }} height="32px">{it.value}</Box>)}
        </ClickableDropdown>
    </Box >
}

// TODO(Jonas): Close on outside click

const Unselectables = injectStyle("unselectable", cl => `
    ${cl}[data-unselectable=true] {
        cursor: not-allowed;
    }

    ${cl}[data-unselectable=true]:hover {
        background-color: unset;
    }

    ${cl}[data-active="true"] {
        background-color: var(--rowHover);
    }
`);