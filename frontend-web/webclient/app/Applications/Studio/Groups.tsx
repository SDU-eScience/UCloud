import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Input, Label, Link, List, Select} from "@/ui-components";
import React, {useRef, useState} from "react";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useNavigate} from "react-router";
import {SafeLogo} from "../AppToolLogo";
import {ListRow} from "@/ui-components/List";
import * as AppStore from "@/Applications/AppStoreApi";
import * as Heading from "@/ui-components/Heading";
import {doNothing, inDevEnvironment} from "@/UtilityFunctions";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {Client} from "@/Authentication/HttpClientInstance";
import {fetchAll} from "@/Utilities/PageUtilities";
import {useProjectId} from "@/Project/Api";

export const ApplicationGroups: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [filter, setTitleFilter] = React.useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const navigate = useNavigate();
    const selectRef = useRef<HTMLSelectElement>(null);

    const createRef = React.useRef<HTMLInputElement>(null);
    const filterRef = React.useRef<HTMLInputElement>(null);

    const [allGroups, setGroups] = React.useState<AppStore.ApplicationGroup[]>([]);
    
    React.useEffect(() => {
        fetchGroups();
    }, [projectId]);

    usePage("Application groups", SidebarTabId.APPLICATION_STUDIO)

    const fetchGroups = () => {
        if (!projectId) {
            setGroups([]);
            return
        }
        fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})))
            .then(groups => setGroups(groups));
    };

    const results = React.useMemo(() => {
        return allGroups.filter(it => it.specification.title.toLowerCase().includes(filter.toLowerCase()))
    }, [allGroups, filter]);

    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    return (
        <MainContainer
            header={
                <Flex justifyContent="space-between">
                    <Heading.h2>Application groups</Heading.h2>
                    <ContextSwitcher />
                </Flex>
            }
            main={<>
                {!projectId || allGroups.length < 1 ? <>
                    <Box>No application groups found for this project.</Box>
                </> : <>
                    <Flex gap={"16px"} mb={"32px"} flexWrap={"wrap"}>
                        <label className={ButtonClass} style={{flexGrow: 1}}>
                            Upload application
                            <HiddenInputField
                                type="file"
                                onChange={async e => {
                                    const target = e.target;
                                    if (target.files) {
                                        const file = target.files[0];
                                        target.value = "";
                                        if (file.size > 1024 * 1024 * 5) {
                                            snackbarStore.addFailure("File exceeds 5MB. Not allowed.", false);
                                        } else {
                                            const error = (await AppStore.create(file)).error;
                                            if (error != null) {
                                                setErrorMessage(error);
                                            } else {
                                                snackbarStore.addSuccess("Application uploaded successfully", false);
                                                setErrorMessage(null);
                                            }
                                        }
                                        dialogStore.success();
                                    }
                                }}
                            />
                        </label>

                        <label className={ButtonClass} style={{flexGrow: 1}}>
                            Upload tool
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
                                            const error = (await AppStore.createTool(file)).error;
                                            if (error != null) {
                                                setErrorMessage(error);
                                            } else {
                                                snackbarStore.addSuccess("Tool uploaded successfully", false);
                                                setErrorMessage(null);
                                            }
                                        }
                                        dialogStore.success();
                                    }
                                }}
                            />
                        </label>

                        <Box flexGrow={1}>
                            <Button fullWidth onClick={() => {
                                AppStore.doExport().then(s => {
                                    const element = document.createElement("a");
                                    element.setAttribute("href", s);
                                    document.body.appendChild(element);
                                    element.click();
                                    document.body.removeChild(element);
                                });
                            }}>
                                Export to ZIP
                            </Button>
                        </Box>

                        <label className={ButtonClass} style={{flexGrow: 1}}>
                            Import from ZIP
                            <HiddenInputField
                                type="file"
                                onChange={async e => {
                                    const target = e.target;
                                    if (target.files) {
                                        const file = target.files[0];
                                        target.value = "";
                                        if (file.size > 1024 * 1024 * 64) {
                                            snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                                        } else {
                                            const error = (await AppStore.doImport(file)).error;
                                            if (error != null) {
                                                setErrorMessage(error);
                                            } else {
                                                snackbarStore.addSuccess("Tool uploaded successfully", false);
                                                setErrorMessage(null);
                                            }
                                        }
                                        dialogStore.success();
                                    }
                                }}
                            />
                        </label>
                    </Flex>

                    {errorMessage && <Box mb={"32px"}>
                        <Box color={"errorMain"}>ERROR:</Box>
                        <code>{errorMessage}</code>
                    </Box>}

                    <form onSubmit={async e => {
                        e.preventDefault();

                        const createField = createRef.current;
                        if (createField === null) return;

                        const createValue = createField.value;
                        if (createValue === "") return;

                        const res = await invokeCommand(AppStore.createGroup({title: createValue, description: "", categories: []}))
                        createField.value = "";
                        if (!res) {
                            return;
                        }
                        if (!res.id) {
                            return;
                        }
                        navigate(`/applications/studio/g/${res.id}`)
                    }}>
                        <Flex>
                            <Input
                                placeholder="Create group"
                                inputRef={createRef}
                                type="text"
                                mb="20px"
                                rightLabel
                            />
                            <Button type="submit" attached>Create</Button>
                        </Flex>
                    </form>

                    <Input
                        placeholder="Filter groups"
                        defaultValue={filter}
                        inputRef={filterRef}
                        type="text"
                        autoFocus
                        onChange={e => setTitleFilter("value" in (e.target) ? e.target.value as string : "")}
                        mb="20px"
                    />

                    <List width="100%">
                        {results.map(group => (
                            <ListRow
                                key={group.metadata.id}
                                navigate={() => navigate(`/applications/studio/g/${group.metadata.id}`)}
                                left={
                                    <Flex justifyContent="left">
                                        <SafeLogo name={group.metadata.id.toString()} type="GROUP" size="25px"/>
                                        <Box ml="10px" mt="5px">
                                            {group.specification.title}
                                        </Box>
                                    </Flex>
                                } right={
                                <ConfirmationButton onAction={() => {
                                    invokeCommand(AppStore.deleteGroup({id: group.metadata.id})).then(doNothing);
                                    fetchGroups();
                                }} icon="heroTrash" />
                            }
                            />
                        ))}
                    </List>
                </>}
            </>}
        />);
};

export default ApplicationGroups;