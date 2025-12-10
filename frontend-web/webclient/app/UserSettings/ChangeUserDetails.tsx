import {apiRetrieve, apiUpdate, callAPI, callAPIWithErrorHandler, useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {Box, Button, Input, Label, Truncate} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PayloadAction} from "@reduxjs/toolkit";
import ResearchFields from "@/UserSettings/ResearchField.json";
import Positions from "@/UserSettings/Position.json";
import KnownDepartments from "@/UserSettings/KnownDepartments.json"
import KnownOrgs from "@/UserSettings/KnownOrgs.json";
import Genders from "@/UserSettings/Genders.json";
import OrgMapping from "@/UserSettings/OrganizationMapping.json";
import {Client} from "@/Authentication/HttpClientInstance";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {classConcat, injectStyle} from "@/Unstyled";
import {clamp} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {SelectorDialog} from "@/Products/Selector";

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

export function ChangeUserDetails(): React.ReactNode {
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
            <Heading.h2>Change user details</Heading.h2>
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

export interface OptionalInfo {
    organizationFullName?: string | null;
    department?: string | null;
    researchField?: string | null;
    position?: string | null;
    gender?: string | null;
}

type InfoAndValidation = OptionalInfo & {isValid: boolean};

export function optionalInfoRequest() {
    return apiRetrieve({}, "/auth/users", "optionalInfo");
}

export function optionalInfoUpdate(values: OptionalInfo) {
    return apiUpdate(values, "/auth/users", "optionalInfo")
}

export async function addOrgInfoModalIfNotFilled(): Promise<void> {
    const result = await callAPI<OptionalInfo>(optionalInfoRequest());
    if (!result.department || !result.organizationFullName || !result.position || !result.researchField || !result.gender) {
        let tries = 10;
        function addDialog() {
            if (tries < 0) return;
            const {pathname} = window.location;
            if (pathname.includes("/app/dashboard") || pathname.endsWith("/app") || pathname.endsWith("/app/projects/members")) {
                setTimeout(() => dialogStore.addDialog(<ChangeOrganizationDetails inModal onDidSubmit={() => dialogStore.success()} />, () => void 0), 1000);
            } else {
                tries--;
                setTimeout(addDialog, 200);
            }
        }

        addDialog();
    }
}

export function ChangeOrganizationDetails(props: {getValues?: React.RefObject<() => InfoAndValidation>; inModal?: boolean; onDidSubmit?: () => void;}) {
    const orgFullNameRef = useRef<HTMLInputElement>(null);
    const departmentRef = useRef<HTMLInputElement>(null);
    const researchFieldRef = useRef<HTMLInputElement>(null);
    const genderFieldRef = useRef<HTMLInputElement>(null);
    const positionRef = useRef<HTMLInputElement>(null);

    useLayoutEffect(() => {
        (async () => {
            const info = await callAPI<OptionalInfo>(optionalInfoRequest());

            if (orgFullNameRef.current) {
                const orgId = Client.orgId ? Client.orgId : info.organizationFullName;
                const orgMapping = orgId ? (OrgMapping[orgId] ?? orgId) : undefined;
                orgFullNameRef.current.value = orgMapping ?? info.organizationFullName ?? "";
            }
            if (departmentRef.current)
                departmentRef.current.value = info.department ?? "";
            if (researchFieldRef.current)
                researchFieldRef.current.value = info.researchField ?? "";
            if (positionRef.current)
                positionRef.current.value = info.position ?? "";
            if (genderFieldRef.current)
                genderFieldRef.current.value = info.gender ?? "";

        })();
    }, []);

    const extractValues = React.useCallback((): InfoAndValidation => {
        const validateOrganisation = (o: string | undefined): boolean => !!o;
        const organizationName = orgFullNameRef.current?.value.trim() ?? "";

        const org = findOrganisationIdFromName(organizationName) ?? organizationName;
        const organizationFullName = org ?? organizationName;
        const organizationValid = validateOrganisation(org);
        const errors: string[] = [];
        if (!organizationValid) {
            orgFullNameRef.current?.setAttribute("data-error", "true");
            errors.push("Organization cannot be empty");
        }

        const validateDepartment = (o: string | undefined, area: string | undefined): boolean => {
            if (!area) return false;
            if (!o) return true;
            const [faculty, dept] = area.split("/");
            if (faculty == null) return false;
            const orgDepartments = KnownDepartments[o];
            if (!orgDepartments) return true;
            if (orgDepartments === "freetext") return true;
            const group = orgDepartments.find((it: {faculty: string}) => it.faculty === faculty);
            if (!group) return false;
            if (group.freetext) return true;
            if (!group.departments || group.departments.length === 0) return true;
            return group.departments.find((it: string) => it === dept) != null;
        };
        const department = departmentRef.current?.value.trim();
        const departmentValid = validateDepartment(org, department);
        if (!departmentValid) {
            departmentRef.current?.setAttribute("data-error", "true");
            errors.push("Department/Faculty/Center isn't valid")
        }

        const validatePosition = (pos: string | undefined): boolean => Positions.map(it => it.key).includes(pos ?? "");
        const position = positionRef.current?.value.trim();
        const positionValid = validatePosition(position);
        if (!positionValid) {
            positionRef.current?.setAttribute("data-error", "true");
            errors.push("Please select a position from the available options")
        }

        const validateResearch = (r: string | undefined): boolean => ResearchFields.find(it => it.key === r) != null;
        const researchField = researchFieldRef.current?.value.trim();
        const researchValid = validateResearch(researchField);
        if (!researchValid) {
            researchFieldRef.current?.setAttribute("data-error", "true");
            errors.push("Please select a research field from the available options");
        }

        const validateGender = (g: string | undefined): boolean => Genders.find(it => it.key === g) != null;
        const gender = genderFieldRef.current?.value.trim();
        const genderValid = validateGender(gender);
        if (!genderValid) {
            genderFieldRef.current?.setAttribute("data-error", "true");
            errors.push("Please select a gender option from list");
        }

        const isValid = errors.length === 0;
        if (!isValid) {
            snackbarStore.addFailure(errors[0], false);
        }
        return {
            organizationFullName,
            department,
            researchField,
            position,
            gender,
            isValid
        };
    }, [orgFullNameRef.current, departmentRef.current, researchFieldRef.current, positionRef.current, genderFieldRef.current]);

    React.useEffect(() => {
        if (props.getValues) props.getValues.current = extractValues;
    }, [extractValues])

    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();
        const {isValid, ...values} = extractValues();
        if (!isValid) return;
        await callAPIWithErrorHandler(optionalInfoUpdate(values));
        props.onDidSubmit?.()
        snackbarStore.addSuccess("Your information has been updated.", false);
    }, []);

    const [org, setOrg] = useState(OrgMapping[Client.orgId ?? ""] ?? Client.orgId);

    React.useEffect(() => {
        if (departmentRef.current) {
            departmentRef.current.value = "";
        }
    }, [org]);

    return (
        <Box mb={16} width="100%">
            <Heading.h2>Additional user information</Heading.h2>
            {props.inModal ? <span>This can be filled out at a later time, but is required when applying for resources.</span> : null}
            <NewDataList ref={orgFullNameRef} disabled={!!Client.orgId} items={KnownOrgs} didUpdateQuery={setOrg} onSelect={({value}) => setOrg(value)} title={"Organization"} placeholder={"University of Knowledge"} />
            <Department org={org} ref={departmentRef} />
            <NewDataList title="Position" placeholder="VIP/TAP/Student" items={Positions} ref={positionRef} />
            <NewDataList title={"Research field"} ref={researchFieldRef} items={ResearchFields} disabled={false} placeholder={ResearchFields[RFIndex].value} />
            <NewDataList title={"Gender"} ref={genderFieldRef} items={Genders} disabled={false} placeholder="Prefer not to say" />
            {props.getValues ? null : <Button onClick={onSubmit} mt="1em" type="button" color="successMain">Update Information</Button>}
        </Box>
    );
}

type Departments = "freetext" | {faculty: string; departments?: string[]}[]

function Department(props: {org: string; ref: React.RefObject<HTMLInputElement | null>}) {
    const orgInfo = findOrganisationIdFromName(props.org);
    const title = "Department/Faculty/Center";
    const items = React.useMemo((): DataListItem[] => {
        const possibleDepartments: Departments = orgInfo ? KnownDepartments[orgInfo] : [];
        if (possibleDepartments === "freetext") return [];
        return possibleDepartments.flatMap(f => {
            if (!f.departments || f.departments.length === 0) return dataListItem(f.faculty, f.faculty, "");
            else return f.departments.map(d => dataListItem(`${f.faculty}/${d}`, `${f.faculty}/${d}`, ""));
        });
    }, [orgInfo]);
    return <NewDataList key={props.org} items={items} ref={props.ref} title={title} placeholder={"Department of Dreams"} />
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

function NewDataList({items, onSelect, title, disabled, placeholder, ref, didUpdateQuery}: {
    items: DataListItem[];
    onSelect?: (arg: DataListItem) => void;
    title: string;
    disabled?: boolean;
    placeholder: string;
    ref: React.RefObject<HTMLInputElement | null>
    didUpdateQuery?: (val: string) => void;
}) {
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);

    const [searchIndex, setSearchIndex] = useState(-1);
    const dropdownRef = useRef<HTMLDivElement>(null);

    React.useEffect(() => {
        if (!open) {
            setSearchIndex(-1);
        }
    }, [open]);

    React.useEffect(() => {
        if (searchIndex === -1) return;
        const row = dropdownRef.current?.children.item(searchIndex);
        row?.scrollIntoView({behavior: "smooth", block: "end", inline: "nearest"});
    }, [searchIndex]);

    React.useEffect(() => {
        function closeOnEscape(e: KeyboardEvent) {
            if (["Escape", "Tab"].includes(e.key)) {
                setOpen(false);
            }
        }

        function closeOnOutsideClick(e: PointerEvent) {
            if (e.target &&
                dropdownRef.current &&
                !dropdownRef.current.contains(e.target as any) &&
                !ref.current?.contains(e.target as any)
            ) {
                setOpen(false);
            }
        }

        window.addEventListener("keydown", closeOnEscape);
        window.addEventListener("click", closeOnOutsideClick);

        return () => {
            window.removeEventListener("keydown", closeOnEscape);
            window.removeEventListener("click", closeOnOutsideClick);
        }
    }, []);

    const result = React.useMemo(() => {
        const result = fuzzySearch(items, ["tags", "value"], query);
        setSearchIndex(index => {
            return clamp(index, -1, result.length - 1);
        });
        return result;
    }, [query, items]);

    const boxRect = ref?.current?.getBoundingClientRect() ?? {x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0};
    let dialogX = boxRect.x;
    let dialogY = boxRect.y + boxRect.height;
    let dialogHeight = Math.min(400, result.length * 32);
    const minimumWidth = 500;
    let dialogWidth = Math.min(Math.max(minimumWidth, boxRect.width), window.innerWidth - boxRect.x - 16);
    {
        const dialogOutOfBounds = (): boolean =>
            dialogX <= 0 || dialogY <= 0 ||
            dialogY + dialogHeight >= window.innerHeight || dialogHeight < 200;

        // Attempt to move the dialog box up a bit
        if (dialogOutOfBounds()) dialogY = boxRect.y + 30;

        // Try making it smaller
        if (dialogOutOfBounds()) {
            dialogHeight = window.innerHeight - dialogY - 50;
        }

        // What if we try putting it directly above?
        if (dialogOutOfBounds()) {
            dialogY = boxRect.y - 500;
            dialogHeight = 500;
        }

        // What about a smaller version?
        if (dialogOutOfBounds()) {
            dialogY = boxRect.y - 300;
            dialogHeight = 300;
        }

        // Display a modal, we cannot find any space for it.
        if (dialogOutOfBounds()) {
            dialogX = 50;
            dialogY = 50;
            dialogWidth = window.innerWidth - 50 * 2;
            dialogHeight = window.innerHeight - 50 * 2;
        }
    }


    return <Box mt="0.5em" pt="0.5em" >
        <Label>
            {title}
            <Input
                placeholder={`Example: ${placeholder}`}
                inputRef={ref}
                disabled={disabled}
                onFocus={() => setOpen(true)}
                // Note(Jonas): If already focused, but closed and user clicks again
                onClick={() => setOpen(true)}
                onKeyDown={e => {
                    ref.current?.removeAttribute("data-error");
                    if (e.key === "ArrowDown") {
                        setSearchIndex(idx => clamp(idx + 1, 0, result.length - 1));
                    } else if (e.key === "ArrowUp") {
                        setSearchIndex(idx => clamp(idx - 1, 0, result.length - 1));
                    } else if (e.key === "Enter") {
                        e.stopPropagation();
                        e.preventDefault();
                        const item = result[searchIndex];
                        if (!item || !ref.current) return;
                        ref.current.value = item.value;
                        didUpdateQuery?.(item.value);
                        onSelect?.(item);
                        setOpen(false);
                    } else {
                        if (!open) {
                            setOpen(true);
                        }
                    }
                }}
                onKeyUp={e => {
                    const value = e.target["value"];
                    setQuery(value);
                    didUpdateQuery?.(value);
                }} />
        </Label>
        {items.length > 0 && open ?
            <Box
                data-has-unselectable={hasUnselectable}
                className={classConcat(SelectorDialog, DataListWrapper)}
                style={{position: "fixed", paddingBottom: 0, left: dialogX, top: dialogY, width: dialogWidth, height: dialogHeight}}
                divRef={dropdownRef}
                width="100%"
                maxHeight={400}
                overflowY="scroll"
            >
                {result.map((it, idx) =>
                    <Truncate key={it.key}
                        cursor={it.unselectable ? "not-allowed" : "pointer"}
                        className={DataListRowItem}
                        data-active={searchIndex === idx}
                        data-unselectable={it.unselectable}
                            onClick={e => {
                                if (it.unselectable) {
                                    e.stopPropagation();
                                    return;
                                }
                                ref.current?.removeAttribute("data-error");
                                if (!ref.current) return;
                                setOpen(false);
                                ref.current.value = it.value;
                                onSelect?.(it);
                            }} height="32px">{it.value}</Truncate>)
                    }
                </Box>
                : null}
        </div>
    </Box >
}

const DataListRowItem = injectStyle("data-list-row-item", cl => `
    ${cl}[data-active="true"]:not([data-unselectable=true]), ${cl}:hover:not([data-unselectable=true]) {
        background-color: var(--rowHover);
    }

    ${cl}[data-unselectable=true] {
        font-weight: bold;
        color: var(--infoLight);
    }

    ${cl} {
        user-select: none;
        margin-left: -16px;
        margin-right: -16px;
        padding-top: 4px;
        padding-left: 10px;
    }
`);

function findOrganisationIdFromName(name: string): string | undefined {
    return Object.entries(OrgMapping).find(it => it[1] === name)?.[0];
}