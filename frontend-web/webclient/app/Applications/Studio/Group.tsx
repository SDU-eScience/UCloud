import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Checkbox, Flex, Icon, Input, Label, Relative, Select, TextArea} from "@/ui-components";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {useNavigate, useParams} from "react-router";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {groupLogoCache, SafeLogo} from "../AppToolLogo";
import {doNothing, stopPropagation} from "@/UtilityFunctions";
import ReactModal from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import List, {ListRow} from "@/ui-components/List";
import {CardClass} from "@/ui-components/Card";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {fetchAll} from "@/Utilities/PageUtilities";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";

export const AppGroup: React.FunctionComponent = () => {
    const id = parseInt(useParams<{ id: string }>().id ?? "-1");

    const [group, fetchGroup] = useCloudAPI(AppStore.retrieveGroup({id}), null);
    const [filter, setFilter] = useState("");
    const [appList] = useCloudAPI(
        AppStore.listAllApplications({}),
        { items: [] },
    );
    
    usePage("Edit group", SidebarTabId.ADMIN);

    const uniqueAppsSet = new Set<string>();
    appList.data.items.forEach(it => {
        if (filter == "") {
            uniqueAppsSet.add(it.name)
        } else if (it.name.includes(filter)) {
            uniqueAppsSet.add(it.name)
        }
    });

    const uniqueApps = Array.from(uniqueAppsSet);

    const groupTitleField = React.useRef<HTMLInputElement>(null);
    const groupDescriptionField = React.useRef<HTMLInputElement>(null);
    const appSearchField = React.useRef<HTMLInputElement>(null);

    const [defaultApplication, setDefaultApplication] = useState<string | undefined>(undefined);
    const [categories, setCategories] = useState<AppStore.ApplicationCategory[]>([]);

    const [addApplicationOpen, setAddApplicationOpen] = useState<boolean>(false);

    const [commandLoading, invokeCommand] = useCloudCommand();

    const didCancel = useDidUnmount();

    const refresh = useCallback(async () => {
        fetchGroup(AppStore.retrieveGroup({id})).then(doNothing);
        const categories = await fetchAll(next => {
            return callAPI(AppStore.browseCategories({itemsPerPage: 250, next}));
        });

        if (!didCancel.current) {
            setCategories(categories);
        }
    }, [id]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        if (group.data) {
            setDefaultApplication(group.data?.specification?.defaultFlavor ?? undefined);
        }
    }, [group.data]);

    const selectRef = useRef<HTMLSelectElement>(null);

    const addCategory = useCallback(async () => {
        const categoryId = parseInt(selectRef.current?.value ?? "");
        await callAPI(AppStore.addGroupToCategory({
            groupId: id,
            categoryId,
        }));
        await refresh();
    }, [id, refresh]);

    const removeCategory = useCallback(async (key: string) => {
        const categoryId = parseInt(key);
        await callAPI(AppStore.removeGroupFromCategory({
            groupId: id,
            categoryId,
        }));
        await refresh();
    }, [id, refresh]);

    const navigate = useNavigate();

    useSetRefreshFunction(refresh);

    return (
        !group.data ? <></> :
            <>
                <ReactModal
                    style={largeModalStyle}
                    isOpen={addApplicationOpen}
                    shouldCloseOnEsc
                    shouldCloseOnOverlayClick
                    onRequestClose={() => setAddApplicationOpen(false)}
                    className={CardClass}
                >
                    <Flex mb="20px" justifyContent="center">
                        <form onSubmit={e => {
                            e.preventDefault();

                            const searchField = appSearchField.current;
                            if (searchField === null) return;

                            const searchValue = searchField.value;
                            setFilter(searchValue);
                        }}>
                            <Flex>
                                <Input placeholder="Search..." inputRef={appSearchField} width="300px" type="text"/>
                                <Relative right="30px" top="8px" width="0px" height="0px">
                                    <button type="submit" style={{border: "none", background: "none"}}><Icon
                                        name="search"/></button>
                                </Relative>
                            </Flex>
                        </form>
                    </Flex>

                    {!uniqueApps? <>No apps found</> : (
                        <List width="100%" height="calc(80vh - 75px)" minHeight="325px" overflow="auto">
                            {uniqueApps.map(app => (
                                group.data?.status?.applications?.map(app => app.metadata.name).includes(app) ? null : (
                                    <ListRow
                                        left={
                                            <Flex gap="10px">
                                                <SafeLogo name={app} type="APPLICATION" size="30px"/>
                                                {app}
                                            </Flex>
                                        }
                                        right={
                                            <Button
                                                onClick={async () => {
                                                    await invokeCommand(AppStore.assignApplicationToGroup({
                                                        name: app,
                                                        group: group.data!.metadata.id,
                                                    }));
                                                    setAddApplicationOpen(false);
                                                    refresh();
                                                }}
                                                height="30px"
                                            >Add to group</Button>
                                        }
                                    />
                                )
                            ))}
                        </List>
                    )}
                </ReactModal>
                <MainContainer
                    header={
                        <Flex justifyContent="space-between">
                            <Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Edit group</Heading.h2>
                            <Flex justifyContent="right" mr="10px">
                                <Button type="button" onClick={async () => {
                                    if (!group.data) return;

                                    const titleField = groupTitleField.current;
                                    if (titleField === null) return;

                                    const newTitle = titleField.value;
                                    if (newTitle === "") {
                                        snackbarStore.addFailure("Title cannot be empty", false);
                                        return
                                    }

                                    const descriptionField = groupDescriptionField.current;
                                    if (descriptionField === null) return;

                                    const newDescription = descriptionField.value;

                                    await invokeCommand(AppStore.updateGroup({
                                        id: group.data?.metadata.id,
                                        newTitle: newTitle,
                                        newDescription: newDescription,
                                        newDefaultFlavor: defaultApplication,
                                        // tags
                                    }))
                                    navigate(`/applications/studio/groups`);
                                }}>Save changes</Button>
                            </Flex>
                        </Flex>
                    }
                    main={
                        !group.data ? <>Not found</> :
                            <Box maxWidth="800px" width="100%" ml="auto" mr="auto">
                                <Label>Title
                                    <Input mb="20px" inputRef={groupTitleField} type="text"
                                           defaultValue={group.data?.specification.title}/>
                                </Label>

                                <Label>Description
                                    <TextArea mb="20px" inputRef={groupDescriptionField}
                                              defaultValue={group.data?.specification.description}/>
                                </Label>

                                <Heading.h4>Logo</Heading.h4>
                                <Flex justifyContent="space-between">
                                    <Box><SafeLogo name={id.toString()} type="GROUP" size={"32px"}/></Box>
                                    <Flex justifyContent="right">
                                        <label className={ButtonClass}>
                                            Upload
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
                                                            await AppStore.addLogoToGroup(id, file)
                                                        }
                                                        groupLogoCache.forget(id.toString());
                                                        refresh();
                                                        dialogStore.success();
                                                    }
                                                }}
                                            />
                                        </label>

                                        <Button
                                            ml="5px"
                                            type="button"
                                            color="errorMain"
                                            disabled={commandLoading}
                                            onClick={async () => {
                                                await invokeCommand(AppStore.removeLogoFromGroup({id}));
                                                groupLogoCache.forget(id.toString());
                                                refresh();
                                            }}
                                        >
                                            <Icon name="trash"/>
                                        </Button>

                                    </Flex>
                                </Flex>

                                <Flex mt="30px" flexDirection={"column"} gap={"8px"}>
                                    <Heading.h4>Categories</Heading.h4>
                                    <Flex gap={"8px"}>
                                        <Select selectRef={selectRef}>
                                            {categories.map(c =>
                                                <option key={c.metadata.id} value={c.metadata.id} disabled={group.data?.specification?.categories?.includes(c.metadata.id)}>
                                                    {c.specification.title}
                                                </option>
                                            )}
                                        </Select>
                                        <Button onClick={addCategory}><Icon name={"heroPlus"} /></Button>
                                    </Flex>
                                    {group.data?.specification?.categories?.map(cat => {
                                        const resolved = categories.find(it => it.metadata.id === cat);
                                        if (!resolved) return null;

                                        return <Flex alignItems={"center"} key={cat}>
                                            <div>{resolved.specification.title}</div>
                                            <Box flexGrow={1}/>
                                            <ConfirmationButton icon={"heroTrash"} color={"errorMain"} actionKey={cat.toString()} onAction={removeCategory}/>
                                        </Flex>
                                    })}
                                </Flex>

                                <Box mt="20px">
                                    <Flex justifyContent="space-between">
                                        <Flex>
                                            <Heading.h4>Applications</Heading.h4>
                                            <Button
                                                type="button"
                                                onClick={() => {
                                                    setAddApplicationOpen(true);
                                                }}
                                                height="25px"
                                                ml="25px"
                                            >
                                                Add
                                            </Button>
                                        </Flex>
                                        <Box mt="25px" mr="60px">Default</Box>
                                    </Flex>
                                    <List width="100%">
                                        {group.data.status?.applications?.map(app => (
                                            <ListRow
                                                navigate={() => navigate(`/applications/studio/a/${app.metadata.name}`)}
                                                left={
                                                    <Flex justifyContent="left" gap="20px">
                                                        <Box><SafeLogo name={app.metadata.name} type="APPLICATION"
                                                                          size="30px"/></Box>
                                                        <Box>{app.metadata.title}</Box>
                                                    </Flex>
                                                }
                                                right={
                                                    <Flex justifyContent="right">
                                                        <Box>
                                                            <Label>
                                                                <Checkbox
                                                                    checked={app.metadata.name === defaultApplication}
                                                                    onChange={() => {
                                                                        stopPropagation;

                                                                        if (app.metadata.name === defaultApplication) {
                                                                            setDefaultApplication(undefined);
                                                                        } else {
                                                                            setDefaultApplication(app.metadata.name);
                                                                        }
                                                                    }}
                                                                />
                                                            </Label>
                                                        </Box>
                                                        <Box>
                                                            <Button
                                                                mt="2px"
                                                                color="errorMain"
                                                                type="button"
                                                                onClick={async () => {
                                                                    await invokeCommand(AppStore.assignApplicationToGroup({
                                                                        name: app.metadata.name,
                                                                        group: undefined
                                                                    }))
                                                                    refresh();
                                                                }}
                                                            >
                                                                <Icon name="trash"/>
                                                            </Button>
                                                        </Box>
                                                    </Flex>
                                                }
                                            />
                                        ))}
                                    </List>
                                </Box>
                            </Box>
                    }
                />
            </>
    );
};

export default AppGroup;