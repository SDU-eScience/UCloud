import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Flex, Input, Link, List} from "@/ui-components";
import React, {useCallback, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useNavigate} from "react-router";
import {SafeLogo} from "../AppToolLogo";
import {ListRow} from "@/ui-components/List";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {doNothing} from "@/UtilityFunctions";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";
import AppRoutes from "@/Routes";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

export const ApplicationGroups: React.FunctionComponent = () => {
    const [filter, setTitleFilter] = React.useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const navigate = useNavigate();

    const createRef = React.useRef<HTMLInputElement>(null);
    const filterRef = React.useRef<HTMLInputElement>(null);

    const [allGroups, setGroups] = useCloudAPI(
        AppStore.browseGroups({itemsPerPage: 250}),
        emptyPageV2,
    );
    
    usePage("Application groups", SidebarTabId.ADMIN)

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
                <Box maxWidth="800px" ml="auto" mr="auto">
                    <h3 className="title">Application groups</h3>
                </Box>
            }
            main={
                <Box maxWidth="800px" width="100%" ml="auto" mr="auto">
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

                        <Link to={AppRoutes.apps.studioHero()} flexGrow={1}><Button fullWidth>Carrousel</Button></Link>
                        <Link to={AppRoutes.apps.studioTopPicks()} flexGrow={1}><Button fullWidth>Top picks</Button></Link>
                        <Link to={AppRoutes.apps.studioSpotlights()} flexGrow={1}><Button fullWidth>Spotlights</Button></Link>
                        <Link to={AppRoutes.apps.studioCategories()} flexGrow={1}><Button fullWidth>Categories</Button></Link>
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