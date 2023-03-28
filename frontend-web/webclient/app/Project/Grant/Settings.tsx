import * as React from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    externalApplicationsEnabled,
    ExternalApplicationsEnabledResponse,
    ProjectGrantSettings,
    readGrantRequestSettings,
    readTemplates,
    ReadTemplatesResponse, retrieveDescription, RetrieveDescriptionResponse, uploadDescription,
    uploadGrantRequestSettings, uploadTemplates,
    UserCriteria
} from "@/Project/Grant/index";
import {useCallback, useEffect, useRef, useState} from "react";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, DataList, Flex, Grid, Icon, Input, TextArea} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import WAYF from "./wayf-idps.json";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {ConfirmCancelButtons} from "@/UtilityComponents";
import {ProductCategoryId} from "@/Accounting";
import {HiddenInputField} from "@/ui-components/Input";
import {dialogStore} from "@/Dialog/DialogStore";
import {Client} from "@/Authentication/HttpClientInstance";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {inSuccessRange} from "@/UtilityFunctions";
import {Logo} from "@/Project/Grant/ProjectBrowser";
import Divider from "@/ui-components/Divider";
import {bulkRequestOf} from "@/DefaultObjects";
import {useProjectIdFromParams} from "../Api";
import {ButtonClass} from "@/ui-components/Button";

export interface UploadLogoProps {
    file: File;
    projectId: string;
}

export async function uploadProjectLogo(props: UploadLogoProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve) => {
        const request = new XMLHttpRequest();
        request.open("POST", Client.computeURL("/api", `/grant/logo/upload`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.setRequestHeader("Upload-Name", b64EncodeUnicode(props.projectId));
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Logo upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        // tslint:disable-next-line: no-console
                        console.log(e);
                        // Do nothing
                    }

                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.file);
    });
}

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

export const LogoAndDescriptionSettings: React.FunctionComponent = () => {
    const projectId = useProjectIdFromParams();
    const [, runWork] = useCloudCommand();
    const [enabled, fetchEnabled] = useCloudAPI<ExternalApplicationsEnabledResponse>(
        {noop: true},
        {enabled: false}
    );
    const [description, fetchDescription] = useCloudAPI<RetrieveDescriptionResponse>(
        retrieveDescription({projectId}),
        {description: ""}
    );
    const descriptionField = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!projectId) return;
        fetchEnabled((externalApplicationsEnabled({projectId})));
        fetchDescription(retrieveDescription({projectId}));
    }, [projectId]);

    useEffect(() => {
        if (descriptionField.current) {
            descriptionField.current.value = description.data.description;
        }
    }, [descriptionField, description]);

    const onUploadDescription = useCallback(async () => {
        if (!projectId) return;
        await runWork(uploadDescription(bulkRequestOf({
            description: descriptionField.current!.value,
            projectId
        })));
        fetchDescription(retrieveDescription({
            projectId
        }));
    }, [projectId, descriptionField, description]);

    const [, setLogoCacheBust] = useState("" + Date.now());

    if (!enabled.data.enabled || !projectId) return null;
    return <Box>
        <Heading.h4>Logo for Project</Heading.h4>
        Current Logo: <Logo projectId={projectId} size={"40px"}/> <br/>
        <label className={ButtonClass}>
            Upload Logo
            <HiddenInputField
                type="file"
                onChange={async e => {
                    const target = e.target;
                    if (target.files) {
                        const file = target.files[0];
                        target.value = "";
                        if (file.size > 1024 * 512) {
                            snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                        } else {
                            if (await uploadProjectLogo({file, projectId})) {
                                setLogoCacheBust("" + Date.now());
                                snackbarStore.addSuccess("Logo changed, refresh to see changes", false);
                            }
                        }
                        dialogStore.success();
                    }
                }}
            />
        </label>
        <Divider/>
        <Heading.h4>Description for Project</Heading.h4>
        <DescriptionEditor templateDescription={descriptionField} onUploadDescription={onUploadDescription}/>
    </Box>
}

export const GrantProjectSettings: React.FunctionComponent = () => {
    const projectId = useProjectIdFromParams();
    const [, runWork] = useCloudCommand();
    const [enabled, fetchEnabled] = useCloudAPI<ExternalApplicationsEnabledResponse>(
        {noop: true},
        {enabled: false}
    );
    const [settings, fetchSettings] = useCloudAPI<ProjectGrantSettings>(
        {noop: true},
        {projectId: "", allowRequestsFrom: [], excludeRequestsFrom: []}
    );

    const [templates, fetchTemplates] = useCloudAPI<ReadTemplatesResponse>(
        {noop: true},
        {type: "plain_text", existingProject: "", newProject: "", personalProject: ""}
    );

    const templatePersonal = useRef<HTMLInputElement>(null);
    const templateExisting = useRef<HTMLInputElement>(null);
    const templateNew = useRef<HTMLInputElement>(null);

    useEffect(() => {
        fetchEnabled((externalApplicationsEnabled({projectId})));
        fetchSettings(readGrantRequestSettings({projectId}));
        fetchTemplates(readTemplates({projectId}));
    }, [projectId]);

    useEffect(() => {
        if (templatePersonal.current) {
            templatePersonal.current.value = templates.data.personalProject;
        }
        if (templateExisting.current) {
            templateExisting.current.value = templates.data.existingProject;
        }
        if (templateNew.current) {
            templateNew.current.value = templates.data.newProject;
        }

    }, [templates, templatePersonal, templateExisting, templateNew]);

    const onUploadTemplate = useCallback(async () => {
        await runWork(uploadTemplates({
            type: "plain_text",
            personalProject: templatePersonal.current!.value,
            newProject: templateNew.current!.value,
            existingProject: templateExisting.current!.value
        }));
        fetchTemplates(readTemplates({projectId}));
    }, [templates, templatePersonal, templateExisting, templateNew, projectId]);

    const addExcludeFrom = useCallback(async (criteria: UserCriteria) => {
        const settingsCopy = {...settings.data};
        settingsCopy.excludeRequestsFrom.push(criteria);
        await runWork(uploadGrantRequestSettings(bulkRequestOf(settingsCopy)));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const removeExcludeFrom = useCallback(async (idx: number) => {
        const settingsCopy = {...settings.data};
        settingsCopy.excludeRequestsFrom.splice(idx, 1);
        await runWork(uploadGrantRequestSettings(bulkRequestOf(settingsCopy)));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const addAllowFrom = useCallback(async (criteria: UserCriteria) => {
        const settingsCopy = {...settings.data};
        settingsCopy.allowRequestsFrom.push(criteria);
        await runWork(uploadGrantRequestSettings(bulkRequestOf(settingsCopy)));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const removeAllowFrom = useCallback(async (idx: number) => {
        const settingsCopy = {...settings.data};
        settingsCopy.allowRequestsFrom.splice(idx, 1);
        await runWork(uploadGrantRequestSettings(bulkRequestOf(settingsCopy)));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    if (!enabled.data.enabled) return null;

    return <Box>
        <Heading.h4>Allow Grant Applications From</Heading.h4>
        <UserCriteriaEditor
            criteria={settings.data.allowRequestsFrom}
            onSubmit={addAllowFrom}
            onRemove={removeAllowFrom}
            showSubprojects={true}
        />

        <Heading.h4>Exclude Grant Applications From</Heading.h4>
        <ExcludeListEditor
            criteria={settings.data.excludeRequestsFrom}
            onSubmit={addExcludeFrom}
            onRemove={removeExcludeFrom}
            showSubprojects={true}
        />

        <Divider/>

        <Heading.h4>Default Template for Grant Applications</Heading.h4>
        <TemplateEditor
            templatePersonal={templatePersonal}
            templateExisting={templateExisting}
            templateNew={templateNew}
            onUploadTemplate={onUploadTemplate}
        />
    </Box>;
};

const ExcludeListEditor: React.FunctionComponent<{
    onSubmit: (c: UserCriteria) => any,
    onRemove: (idx: number) => any,
    criteria: UserCriteria[],
    showSubprojects: boolean
}> = props => {
    const [showRequestFromEditor, setShowRequestFromEditor] = useState<boolean>(false);
    return <>
        <Table mb={16}>
            <thead>
            <TableRow>
                <TableHeaderCell textAlign={"left"}>Email Domain</TableHeaderCell>
                <TableHeaderCell />
            </TableRow>
            </thead>
            <tbody>
            {props.criteria.length === 0 && !showRequestFromEditor ? <>
                <TableRow>
                    <TableCell>No exclusions yet</TableCell>
                    <TableCell />
                </TableRow>
            </> : null}

            {props.criteria.map((it, idx) => <>
                <TableRow>
                    <TableCell textAlign={"left"}>
                        {it.type === "email" ? it.domain : null}
                    </TableCell>
                    <TableCell textAlign={"right"}>
                        <Icon color={"red"} name={"trash"} cursor={"pointer"} onClick={() => props.onRemove(idx)} />
                    </TableCell>
                </TableRow>
            </>)}
            {showRequestFromEditor ?
                <UserExclusionRowEditor
                    onSubmit={(c) => {
                        props.onSubmit(c);
                        setShowRequestFromEditor(false);
                    }}
                    onCancel={() => setShowRequestFromEditor(false)}
                /> :
                null
            }
            </tbody>
        </Table>
        <Flex justifyContent={"center"} mb={32}>
            {!showRequestFromEditor ?
                <Button width={450} onClick={() => setShowRequestFromEditor(true)}>Add new row</Button> :
                null
            }
        </Flex>
    </>;
};

const UserCriteriaEditor: React.FunctionComponent<{
    onSubmit: (c: UserCriteria) => any,
    onRemove: (idx: number) => any,
    criteria: UserCriteria[],
    showSubprojects: boolean
}> = props => {
    const [showRequestFromEditor, setShowRequestFromEditor] = useState<boolean>(false);
    return <>
        <Table mb={16}>
            <thead>
                <TableRow>
                    <TableHeaderCell textAlign={"left"}>Type</TableHeaderCell>
                    <TableHeaderCell textAlign={"left"}>Constraint</TableHeaderCell>
                    <TableHeaderCell />
                </TableRow>
            </thead>
            <tbody>
                {!props.showSubprojects ? null :
                    <TableRow>
                        <TableCell>Subprojects</TableCell>
                        <TableCell>None</TableCell>
                        <TableCell />
                    </TableRow>
                }
                {!props.showSubprojects && props.criteria.length === 0 && !showRequestFromEditor ? <>
                    <TableRow>
                        <TableCell>No one</TableCell>
                        <TableCell>None</TableCell>
                        <TableCell />
                    </TableRow>
                </> : null}

                {props.criteria.map((it, idx) =>
                    <TableRow key={keyFromCriteria(it)}>
                        <TableCell textAlign={"left"}>{userCriteriaTypePrettifier(it.type)}</TableCell>
                        <TableCell textAlign={"left"}>
                            {it.type === "wayf" ? it.org : null}
                            {it.type === "email" ? it.domain : null}
                            {it.type === "anyone" ? "None" : null}
                        </TableCell>
                        <TableCell textAlign={"right"}>
                            <Icon color={"red"} name={"trash"} cursor={"pointer"} onClick={() => props.onRemove(idx)} />
                        </TableCell>
                    </TableRow>
                )}
                {showRequestFromEditor ?
                    <UserCriteriaRowEditor
                        onSubmit={(c) => {
                            props.onSubmit(c);
                            setShowRequestFromEditor(false);
                        }}
                        onCancel={() => setShowRequestFromEditor(false)}
                        allowAnyone={props.criteria.find(it => it.type === "anyone") === undefined}
                    /> :
                    null
                }
            </tbody>
        </Table>
        <Flex justifyContent={"center"} mb={32}>
            {!showRequestFromEditor ?
                <Button width={450} onClick={() => setShowRequestFromEditor(true)}>Add new row</Button> :
                null
            }
        </Flex>
    </>;
};

function keyFromCriteria(userCriteria: UserCriteria): string {
    switch (userCriteria.type) {
        case "anyone": {
            return "anyone";
        }
        case "email": {
            return userCriteria.domain;
        }
        case "wayf": {
            return userCriteria.org;
        }
    }
}


function userCriteriaTypePrettifier(t: string): string {
    switch (t) {
        case "anyone":
            return "Anyone";
        case "email":
            return "Email";
        case "wayf":
            return "WAYF";
        default:
            return t;
    }
}

const UserExclusionRowEditor: React.FunctionComponent<{
    onSubmit: (c: UserCriteria) => any,
    onCancel: () => void,
}> = props => {
    const inputRef = useRef<HTMLInputElement>(null);
    const onClick = useCallback((e) => {
        e.preventDefault();
        if (inputRef.current!.value.indexOf(".") === -1 || inputRef.current!.value.indexOf(" ") !== -1) {
            snackbarStore.addFailure("This does not look like a valid email domain. Try again.", false);
            return;
        }
        if (inputRef.current!.value.indexOf("@") !== -1) {
            snackbarStore.addFailure("Only the domain should be added. Example: 'sdu.dk'.", false);
            return;
        }
        const domain = inputRef.current!.value;
        props.onSubmit({type: "email", domain});
    }, [props.onSubmit]);

    return <TableRow>
        <TableCell>
            <form onSubmit={onClick}>
                <Flex height={47}>
                    <Input inputRef={inputRef} placeholder={"Email domain"} />
                    <ConfirmCancelButtons height={"unset"} onConfirm={onClick} onCancel={props.onCancel} />
                </Flex>
            </form>
        </TableCell>
        <TableCell />
    </TableRow>;
};


const UserCriteriaRowEditor: React.FunctionComponent<{
    onSubmit: (c: UserCriteria) => any,
    onCancel: () => void,
    allowAnyone: boolean
}> = props => {
    const [type, setType] = useState<Pick<UserCriteria, "type">>({type: "wayf"});
    const [selectedWayfOrg, setSelectedWayfOrg] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);
    const onClick = useCallback((e) => {
        e.preventDefault();
        switch (type.type) {
            case "email":
                if (inputRef.current!.value.indexOf(".") === -1 || inputRef.current!.value.indexOf(" ") !== -1) {
                    snackbarStore.addFailure("This does not look like a valid email domain. Try again.", false);
                    return;
                }
                if (inputRef.current!.value.indexOf("@") !== -1) {
                    snackbarStore.addFailure("Only the domain should be added. Example: 'sdu.dk'.", false);
                    return;
                }

                const domain = inputRef.current!.value;
                props.onSubmit({type: "email", domain});
                break;
            case "anyone":
                props.onSubmit({type: "anyone"});
                break;
            case "wayf":
                if (selectedWayfOrg === "") {
                    snackbarStore.addFailure("You must select a WAYF organization", false);
                    return;
                }
                props.onSubmit({type: "wayf", org: selectedWayfOrg});
                break;
        }
    }, [props.onSubmit, type, selectedWayfOrg]);

    const options: {text: string, value: string}[] = [];
    if (props.allowAnyone) {
        options.push({text: "Anyone", value: "anyone"});
    }

    options.push({text: "Email", value: "email"});
    options.push({text: "WAYF", value: "wayf"});

    return <TableRow>
        <TableCell>
            <ClickableDropdown
                trigger={userCriteriaTypePrettifier(type.type)}
                options={options}
                onChange={t => setType({type: t} as Pick<UserCriteria, "type">)}
                chevron
            />
        </TableCell>
        <TableCell>
            <form onSubmit={onClick}>
                <Flex height={47}>
                    {type.type !== "anyone" ? null : null}
                    {type.type !== "email" ? null : <>
                        <Input inputRef={inputRef} placeholder={"Email domain"} />
                    </>}
                    {type.type !== "wayf" ? null : <>
                        {/* WAYF idps extracted from https://metadata.wayf.dk/idps.js*/}
                        {/* curl https://metadata.wayf.dk/idps.js 2>/dev/null | jq 'to_entries[].value.schacHomeOrganization' | sort | uniq | xargs -I _ printf '"_",\n' */}
                        <DataList
                            options={wayfIdpsPairs}
                            onSelect={(item) => setSelectedWayfOrg(item)}
                            placeholder={"Type to search..."}
                        />
                    </>}
                    <ConfirmCancelButtons height={"unset"} onConfirm={onClick} onCancel={props.onCancel} />
                </Flex>
            </form>
        </TableCell>
        <TableCell />
    </TableRow>;
};

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.name}/${pid.provider}`;
}

const TemplateEditor: React.FunctionComponent<{
    templatePersonal: React.RefObject<HTMLInputElement>,
    templateExisting: React.RefObject<HTMLInputElement>,
    templateNew: React.RefObject<HTMLInputElement>,
    onUploadTemplate: () => Promise<void>
}> = ({templatePersonal, templateExisting, templateNew, onUploadTemplate}) => {
    return <>
        <Grid gridGap={32} gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"}>
            <Box>
                <Heading.h5>Personal</Heading.h5>
                <TextArea width={"100%"} rows={15} inputRef={templatePersonal} />
            </Box>
            <Box>
                <Heading.h5>Existing Project</Heading.h5>
                <TextArea width={"100%"} rows={15} inputRef={templateExisting} />
            </Box>
            <Box>
                <Heading.h5>New Project</Heading.h5>
                <TextArea width={"100%"} rows={15} inputRef={templateNew} />
            </Box>
        </Grid>
        <Flex justifyContent={"center"} mt={32}>
            <Button width={"350px"} onClick={onUploadTemplate}>Update Templates</Button>
        </Flex>
    </>;
};

const DescriptionEditor: React.FunctionComponent<{
    templateDescription: React.RefObject<HTMLInputElement>,
    onUploadDescription: () => Promise<void>
}> = ({templateDescription, onUploadDescription}) =>
        <>
            <Grid gridGap={32} gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"}>
                <Box>
                    <Heading.h5>Description</Heading.h5>
                    <TextArea width={"100%"} rows={15} inputRef={templateDescription} />
                </Box>
            </Grid>
            <Flex justifyContent={"center"} mt={32}>
                <Button width={"350px"} onClick={onUploadDescription}>Update Description</Button>
            </Flex>
        </>;
