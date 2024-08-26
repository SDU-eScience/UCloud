import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Input, Label, Link, List, Select} from "@/ui-components";
import React, {useCallback, useRef, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {useNavigate} from "react-router";
import {AppToolLogo, SafeLogo} from "../AppToolLogo";
import {ListRow} from "@/ui-components/List";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {doNothing, onDevSite} from "@/UtilityFunctions";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";
import AppRoutes from "@/Routes";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

export const ApplicationGroups: React.FunctionComponent = () => {
    // TODO(Brian): Fetch repositories
    const repositories = [
        {"id": 1, "title": "Docker apps"},
        {"id": 2, "title": "Virtual machines"}
    ];

    const [filter, setTitleFilter] = React.useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const navigate = useNavigate();
    const [repository, setRepository] = React.useState(0);
    const selectRef = useRef<HTMLSelectElement>(null);

    const createRef = React.useRef<HTMLInputElement>(null);
    const filterRef = React.useRef<HTMLInputElement>(null);

    const [allGroups, setGroups] = useCloudAPI(
        AppStore.browseGroups({itemsPerPage: 250}),
        emptyPageV2,
    );

    usePage("Application groups", SidebarTabId.APPLICATION_STUDIO)

    const refresh = useCallback(() => {
        setGroups(AppStore.browseGroups({itemsPerPage: 250})).then(doNothing);
    }, []);

    const results = React.useMemo(() => {
        return allGroups.data.items.filter(it => it.specification.title.toLowerCase().includes(filter.toLowerCase()))
    }, [allGroups.data, filter]);

    useSetRefreshFunction(refresh);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    return (
        <MainContainer
            header={
                <Box maxWidth="900px" ml="auto" mr="auto">
                    <h3 className="title">Application groups</h3>
                    <Box mt="10px">
                        <Select selectRef={selectRef} onChange={e => {
                            if (!selectRef.current) return;
                            if (selectRef.current.value === "") return;
                            setRepository(parseInt(selectRef.current.value, 10));
                        }}>
                            <option disabled selected value="0">Select a repository</option>
                            {repositories.map(r =>
                                <option key={r.id} value={r.id}>
                                    {r.title}
                                </option>
                            )}
                        </Select>
                    </Box>
                </Box>
            }
            headerSize={100}
            main={
                <Box maxWidth="900px" width="100%" ml="auto" mr="auto">
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
                                    refresh();
                                }} icon="heroTrash" />
                            }
                            />
                        ))}
                    </List>
                </Box>
            }
        />);
};

export default ApplicationGroups;