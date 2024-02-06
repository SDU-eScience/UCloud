import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Input, Label, Link, List} from "@/ui-components";
import React, {useCallback, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {useNavigate} from "react-router";
import {AppToolLogo} from "../AppToolLogo";
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

export const ApplicationGroups: React.FunctionComponent = () => {
    const [filter, setTitleFilter] = React.useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const navigate = useNavigate();

    const filterRef = React.useRef<HTMLInputElement>(null);

    const [allGroups, setGroups] = useCloudAPI(
        AppStore.browseGroups({itemsPerPage: 250}),
        emptyPageV2,
    );

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
            header={<Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Application Groups</Heading.h2>}
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

                        {onDevSite() &&
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
                        }
                    </Flex>

                    {errorMessage && <Box mb={"32px"}>
                        <Box color={"errorMain"}>ERROR:</Box>
                        <code>{errorMessage}</code>
                    </Box>}

                    <form onSubmit={async e => {
                        e.preventDefault();

                        const filterField = filterRef.current;
                        if (filterField === null) return;

                        const filterValue = filterField.value;
                        if (filterValue === "") return;

                        await invokeCommand(AppStore.createGroup({title: filterValue, description: "", categories: []}))
                        filterField.value = "";
                        setTitleFilter("");
                        refresh();
                    }}>
                        <Flex>
                            <Input
                                placeholder="Filter or create group.."
                                defaultValue={filter}
                                inputRef={filterRef}
                                type="text"
                                autoFocus
                                onChange={e => setTitleFilter("value" in (e.target) ? e.target.value as string : "")}
                                mb="20px"
                                rightLabel
                            />
                            <Button type="submit" attached>Create</Button>
                        </Flex>
                    </form>

                    <List width="100%">
                        {results.map(group => (
                            <ListRow
                                key={group.metadata.id}
                                navigate={() => navigate(`/applications/studio/g/${group.metadata.id}`)}
                                left={
                                    <Flex justifyContent="left">
                                        <AppToolLogo name={group.metadata.id.toString()} type="GROUP" size="25px"/>
                                        <Box ml="10px">
                                            {group.specification.title}
                                        </Box>
                                    </Flex>
                                } right={
                                <Button onClick={() => {
                                    invokeCommand(AppStore.deleteGroup({id: group.id})).then(doNothing);
                                    refresh();
                                }} height="25px" color="errorMain"><Icon name="trash"/></Button>
                            }
                            />
                        ))}
                    </List>
                </Box>
            }
        />);
};

export default ApplicationGroups;