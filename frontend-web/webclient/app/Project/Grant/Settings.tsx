import * as React from "react";
import {APICallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    externalApplicationsEnabled,
    ExternalApplicationsEnabledResponse,
    ProjectGrantSettings,
    readGrantRequestSettings,
    readTemplates,
    ReadTemplatesResponse,
    uploadGrantRequestSettings, uploadTemplates,
    UserCriteria
} from "Project/Grant/index";
import {useCallback, useEffect, useRef, useState} from "react";
import {useProjectManagementStatus} from "Project";
import * as Heading from "ui-components/Heading";
import {Box, Button, DataList, Flex, Grid, Icon, Input, Label, Text, TextArea} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {wayfIdps} from "./wayf-idps.json";
import {snackbarStore} from "Snackbar/SnackbarStore";
import Table, {TableCell, TableHeaderCell, TableRow} from "ui-components/Table";
import {ConfirmCancelButtons} from "UtilityComponents";
import {ProductCategoryId, retrieveFromProvider, RetrieveFromProviderResponse, UCLOUD_PROVIDER} from "Accounting";
import {creditFormatter} from "Project/ProjectUsage";

const wayfIdpsPairs = wayfIdps.map(it => ({value: it, content: it}));

export const GrantProjectSettings: React.FunctionComponent = () => {
    const {projectId} = useProjectManagementStatus({isRootComponent: false});
    const [, runWork] = useAsyncCommand();
    const [enabled, fetchEnabled] = useCloudAPI<ExternalApplicationsEnabledResponse>(
        {noop: true},
        {enabled: false}
    );
    const [settings, fetchSettings] = useCloudAPI<ProjectGrantSettings>(
        {noop: true},
        {allowRequestsFrom: [], automaticApproval: {from: [], maxResources: []}}
    );

    const [products, fetchProducts] = useCloudAPI<RetrieveFromProviderResponse>(
        retrieveFromProvider({provider: UCLOUD_PROVIDER}),
        []
    );

    const [templates, fetchTemplates] = useCloudAPI<ReadTemplatesResponse>(
        {noop: true},
        {existingProject: "", newProject: "", personalProject: ""}
    );

    const templatePersonal = useRef<HTMLTextAreaElement>(null);
    const templateExisting = useRef<HTMLTextAreaElement>(null);
    const templateNew = useRef<HTMLTextAreaElement>(null);

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
            personalProject: templatePersonal.current!.value,
            newProject: templateNew.current!.value,
            existingProject: templateExisting.current!.value
        }));
        fetchTemplates(readTemplates({projectId}));
    }, [templates, templatePersonal, templateExisting, templateNew, projectId]);

    const addAllowFrom = useCallback(async (criteria: UserCriteria) => {
        const settingsCopy = {...settings.data};
        settingsCopy.allowRequestsFrom.push(criteria);
        await runWork(uploadGrantRequestSettings(settingsCopy));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const removeAllowFrom = useCallback(async (idx: number) => {
        const settingsCopy = {...settings.data};
        settingsCopy.allowRequestsFrom.splice(idx, 1);
        await runWork(uploadGrantRequestSettings(settingsCopy));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const addAutomaticApproval = useCallback(async (criteria: UserCriteria) => {
        const settingsCopy = {...settings.data};
        settingsCopy.automaticApproval.from.push(criteria);
        await runWork(uploadGrantRequestSettings(settingsCopy));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [settings]);

    const removeAutomaticApproval = useCallback(async (idx: number) => {
        const settingsCopy = {...settings.data};
        settingsCopy.automaticApproval.from.splice(idx, 1);
        await runWork(uploadGrantRequestSettings(settingsCopy));
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

        <Heading.h4>Automatic Approval of Grant Applications</Heading.h4>
        <AutomaticApprovalLimits
            products={products}
            settings={settings}
            reload={() => fetchSettings(readGrantRequestSettings({projectId}))}
        />

        <UserCriteriaEditor
            criteria={settings.data.automaticApproval.from}
            showSubprojects={false}
            onSubmit={addAutomaticApproval}
            onRemove={removeAutomaticApproval}
        />

        <Heading.h4>Default Template for Grant Applications</Heading.h4>
        <TemplateEditor
            templatePersonal={templatePersonal}
            templateExisting={templateExisting}
            templateNew={templateNew}
            onUploadTemplate={onUploadTemplate}
        />
    </Box>;
};

const AutomaticApprovalLimits: React.FunctionComponent<{
    products: APICallState<RetrieveFromProviderResponse>,
    settings: APICallState<ProjectGrantSettings>,
    reload: () => void
}> = ({products, settings, reload}) => {
    const [editingLimit, setEditingLimit] = useState<string | null>(null);
    const [, runWork] = useAsyncCommand();

    const updateApprovalLimit = useCallback(async (category: ProductCategoryId, e?: React.SyntheticEvent) => {
        e?.preventDefault();
        const settingsCopy = {...settings.data};
        const idx = settingsCopy.automaticApproval.maxResources
            .findIndex(it =>
                it.productCategory === category.id &&
                it.productProvider === category.provider
            );

        if (idx !== -1) {
            settingsCopy.automaticApproval.maxResources.splice(idx, 1);
        }

        const inputElement = document.getElementById(productCategoryId(category)) as HTMLInputElement;
        const parsedValue = parseInt(inputElement.value, 10);
        if (isNaN(parsedValue)) {
            snackbarStore.addFailure("Automatic approval limit must be a valid number", false);
            return;
        }
        settingsCopy.automaticApproval.maxResources.push({
            productProvider: category.provider,
            productCategory: category.id,
            creditsRequested: parsedValue * 1000000
        });
        await runWork(uploadGrantRequestSettings(settingsCopy));
        setEditingLimit(null);
        reload();
    }, [settings]);

    return <Grid gridGap={"32px"} gridTemplateColumns={"repeat(auto-fit, 500px)"} mb={32}>
        {products.data.map(it => {
            const key = productCategoryId(it.category);

            const credits = settings.data.automaticApproval
                .maxResources
                .find(
                    mr => mr.productCategory === it.category.id &&
                        mr.productProvider === it.category.provider
                )
                ?.creditsRequested ?? 0;
            return <React.Fragment key={key}>
                <form onSubmit={(e) => updateApprovalLimit(it.category, e)}>
                    <Label htmlFor={key}>
                        {it.category.id} / {it.category.provider}
                    </Label>
                    <Flex alignItems={"center"}>
                        {editingLimit !== key ?
                            <Text width={350} textAlign={"right"}>
                                {creditFormatter(credits, 0)}
                            </Text> : null}
                        {editingLimit !== key ?
                            <Button
                                type={"button"}
                                ml={8}
                                disabled={editingLimit !== null}
                                onClick={() => {
                                    setEditingLimit(key);
                                    const inputField = document.getElementById(key) as HTMLInputElement;
                                    inputField.value = (credits / 1000000).toString();
                                }}
                            >
                                Edit
                            </Button> : null}

                        <Input type={editingLimit !== key ? "hidden" : "text"} id={key} width={328}/>

                        {editingLimit === key ? (
                            <>
                                <Text ml={8} mr={8}>DKK</Text>
                                <ConfirmCancelButtons
                                    onConfirm={() => {
                                        updateApprovalLimit(it.category);
                                    }}
                                    onCancel={() => {
                                        setEditingLimit(null);
                                    }}
                                />
                            </>
                        ) : null}
                    </Flex>
                </form>
            </React.Fragment>;
        })}
    </Grid>;
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
                <TableHeaderCell/>
            </TableRow>
            </thead>
            <tbody>
            {!props.showSubprojects ? null :
                <TableRow>
                    <TableCell>Subprojects</TableCell>
                    <TableCell>None</TableCell>
                    <TableCell/>
                </TableRow>
            }
            {!props.showSubprojects && props.criteria.length === 0 && !showRequestFromEditor ? <>
                <TableRow>
                    <TableCell>No one</TableCell>
                    <TableCell>None</TableCell>
                    <TableCell/>
                </TableRow>
            </> : null}

            {props.criteria.map((it, idx) => <>
                <TableRow>
                    <TableCell textAlign={"left"}>{userCriteriaTypePrettifier(it.type)}</TableCell>
                    <TableCell textAlign={"left"}>
                        {it.type === "wayf" ? it.org : null}
                        {it.type === "email" ? it.domain : null}
                        {it.type === "anyone" ? "None" : null}
                    </TableCell>
                    <TableCell textAlign={"right"}>
                        <Icon color={"red"} name={"trash"} cursor={"pointer"} onClick={() => props.onRemove(idx)}/>
                    </TableCell>
                </TableRow>
            </>)}
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

    const options: { text: string, value: string }[] = [];
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
                        <Input ref={inputRef} placeholder={"Email domain"}/>
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
                    <ConfirmCancelButtons height={"unset"} onConfirm={onClick} onCancel={props.onCancel}/>
                </Flex>
            </form>
        </TableCell>
        <TableCell/>
    </TableRow>;
};

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.id}/${pid.provider}`;
}

const TemplateEditor: React.FunctionComponent<{
    templatePersonal: React.Ref<HTMLTextAreaElement>,
    templateExisting: React.Ref<HTMLTextAreaElement>,
    templateNew: React.Ref<HTMLTextAreaElement>,
    onUploadTemplate: () => Promise<void>
}> = ({templatePersonal, templateExisting, templateNew, onUploadTemplate}) => {
    return <>
        <Grid gridGap={32} gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"}>
            <Box>
                <Heading.h5>Personal</Heading.h5>
                <TextArea width={"100%"} rows={15} ref={templatePersonal}/>
            </Box>
            <Box>
                <Heading.h5>Existing Project</Heading.h5>
                <TextArea width={"100%"} rows={15} ref={templateExisting}/>
            </Box>
            <Box>
                <Heading.h5>New Project</Heading.h5>
                <TextArea width={"100%"} rows={15} ref={templateNew}/>
            </Box>
        </Grid>
        <Flex justifyContent={"center"} mt={32}>
            <Button width={"350px"} onClick={onUploadTemplate}>Update Templates</Button>
        </Flex>
    </>;
};
