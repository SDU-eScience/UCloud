import * as React from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {
    externalApplicationsEnabled,
    ExternalApplicationsEnabledResponse,
    ProjectGrantSettings, UserCriteria
} from "Project/Grant/index";
import {useCallback, useEffect, useRef, useState} from "react";
import {useProjectManagementStatus} from "Project";
import * as Heading from "ui-components/Heading";
import {Box, Button, DataList, Flex, Grid, Icon, Input, Label, Text} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {wayfIdps} from "./wayf-idps.json";
import {snackbarStore} from "Snackbar/SnackbarStore";
import Table, {TableCell, TableHeaderCell, TableRow} from "ui-components/Table";
import {ConfirmCancelButtons} from "UtilityComponents";
import {ProductCategoryId, retrieveFromProvider, RetrieveFromProviderResponse, UCLOUD_PROVIDER} from "Accounting";

const wayfIdpsPairs = wayfIdps.map(it => ({value: it, content: it}));

export const GrantProjectSettings: React.FunctionComponent = () => {
    const {projectId} = useProjectManagementStatus();
    const [showRequestFromEditor, setShowRequestFromEditor] = useState<boolean>(false);
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

    useEffect(() => {
        fetchEnabled((externalApplicationsEnabled({projectId})));
    }, [projectId]);

    if (!enabled.data.enabled) return null;

    return <Box>
        <Heading.h4>Ingoing Applications</Heading.h4>
        <Heading.h5>Allow Requests From</Heading.h5>
        <UserCriteriaEditor
            criteria={settings.data.allowRequestsFrom}
            onSubmit={console.log}
            showSubprojects={true}
        />

        <Heading.h4>Automatic Approval</Heading.h4>
        <Grid gridGap={"16px"} gridTemplateColumns={"repeat(auto-fit, minmax(auto, 350px))"} mb={32}>
            {products.data.map(it => {
                const key = productCategoryId(it.category);

                return <React.Fragment key={key}>
                    <Box>
                        <Label htmlFor={key}>
                            {it.category.id} / {it.category.provider}
                        </Label>
                        <Flex alignItems={"center"}>
                            <Input id={key}/>
                            <Text ml={8}>DKK</Text>
                        </Flex>
                    </Box>
                </React.Fragment>;
            })}
        </Grid>
        <UserCriteriaEditor
            criteria={settings.data.automaticApproval.from}
            showSubprojects={false}
            onSubmit={console.log}
        />
    </Box>;
};

const UserCriteriaEditor: React.FunctionComponent<{
    onSubmit: () => void,
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
            </TableRow>
            </thead>
            <tbody>
            {!props.showSubprojects ? null :
                <TableRow>
                    <TableCell>Subprojects</TableCell>
                    <TableCell>None</TableCell>
                </TableRow>
            }
            {!props.showSubprojects && props.criteria.length === 0 && !showRequestFromEditor ? <>
                <TableRow>
                    <TableCell>No one</TableCell>
                    <TableCell>None</TableCell>
                    <TableCell/>
                </TableRow>
            </> : null}

            {props.criteria.map(it => <>
                <TableRow>
                    <TableCell textAlign={"left"}>{userCriteriaTypePrettifier(it.type)}</TableCell>
                    <TableCell textAlign={"left"}>
                        {it.type === "wayf" ? it.org : null}
                        {it.type === "email" ? it.domain : null}
                        {it.type === "anyone" ? "None" : null}
                    </TableCell>
                    <TableCell>
                        <Icon color={"red"} name={"trash"} cursor={"pointer"}/>
                    </TableCell>
                </TableRow>
            </>)}
            {showRequestFromEditor ?
                <UserCriteriaRowEditor onSubmit={props.onSubmit} onCancel={() => setShowRequestFromEditor(false)}/> :
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
    onSubmit: (c: UserCriteria) => void,
    onCancel: () => void
}> = props => {
    const [type, setType] = useState<Pick<UserCriteria, "type">>({type: "anyone"});
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

    return <TableRow>
        <TableCell>
            <ClickableDropdown
                trigger={userCriteriaTypePrettifier(type.type)}
                options={[
                    {text: "Anyone", value: "anyone"},
                    {text: "Email", value: "email"},
                    {text: "WAYF", value: "wayf"},
                ]}
                onChange={t => setType({type: t})}
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
    </TableRow>;
};

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.id}/${pid.provider}`;
}
