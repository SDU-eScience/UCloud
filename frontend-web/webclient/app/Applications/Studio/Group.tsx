import MainContainer from "@/ui-components/MainContainer";
import {
    Box,
    Button,
    Checkbox,
    Error,
    Flex,
    Icon,
    Input,
    Label,
    Link,
    Relative,
    Select,
    TextArea
} from "@/ui-components";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {useNavigate, useParams} from "react-router-dom";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {SafeLogo} from "../AppToolLogo";
import {doNothing, stopPropagation} from "@/UtilityFunctions";
import ReactModal from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import List, {ListRow} from "@/ui-components/List";
import {CardClass} from "@/ui-components/Card";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {fetchAll} from "@/Utilities/PageUtilities";
import {useDidUnmount, useForcedRender} from "@/Utilities/ReactUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {Toggle} from "@/ui-components/Toggle";
import {UploadAppAndTool} from "@/Applications/Studio/Uploader";
import {injectStyle, makeKeyframe} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";

export const AppGroup: React.FunctionComponent = () => {
    const id = parseInt(useParams<{id: string}>().id ?? "-1");

    const [group, fetchGroup] = useCloudAPI(AppStore.retrieveStudioGroup({id}), null);
    const [filter, setFilter] = useState("");
    const [reloadId, setReloadId] = useState(0);
    const [appUploadError, setAppUploadErr] = useState<string | null>(null);
    const [appList, fetchAppList] = useCloudAPI<Page<AppStore.NameAndVersion>>(
        { noop: true },
        { items: [], itemsInTotal: 0, itemsPerPage: 0, pageNumber: 0 },
    );

    usePage("Edit group", SidebarTabId.APPLICATION_STUDIO);

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
    const [logoHasText, setLogoHasText] = useState(false);

    const [commandLoading, invokeCommand] = useCloudCommand();

    const didCancel = useDidUnmount();

    const fetchCategories = () => {
        if (!group.data) return;
        fetchAll(next => {
            const categories = callAPI(AppStore.browseStudioCategories({itemsPerPage: 250, next}));

            if (!didCancel.current) {
                categories.then(fetched => setCategories(fetched.items));
            }
            return categories;
        });
    };

    const refresh = useCallback(async () => {
        fetchGroup(AppStore.retrieveStudioGroup({id})).then(doNothing);
        fetchCategories();
        setReloadId(p => p + 1);
        fetchAppList(AppStore.listAllApplications({}));
    }, [id]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        if (group.data) {
            setDefaultApplication(group.data?.specification?.defaultFlavor ?? undefined);
            setLogoHasText(group.data?.specification?.logoHasText ?? false);
            fetchCategories();
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

    const currentApp = useRef(-1);
    const forceRender = useForcedRender()
    const swapAppPositions = React.useCallback((draggedAppIndex: number, droppedAppIndex: number) => {
        if (draggedAppIndex === -1) return;
        if (!group.data?.status.applications) return;
        const draggedApp = group.data.status.applications[draggedAppIndex];
        const droppedApp = group.data.status.applications[droppedAppIndex];
        group.data.status.applications[draggedAppIndex] = droppedApp;
        group.data.status.applications[droppedAppIndex] = draggedApp;

        const list = document.querySelector("[data-app-list]");
        const dragEl = list?.querySelector<HTMLDivElement>(`[data-idx="${draggedAppIndex}"]`)
        if (dragEl) {
            dragEl.classList.add(MovedAnimation);
            dragEl.onanimationend = () => dragEl.classList.remove(MovedAnimation);
        }
        const dropEl = list?.querySelector<HTMLDivElement>(`[data-idx="${droppedAppIndex}"]`)
        if (dropEl) {
            dropEl.classList.add(MovedAnimation);
            dropEl.onanimationend = () => dropEl.classList.remove(MovedAnimation);
        }

        snackbarStore.addInformation("Backend support is not yet implemented for custom sort ordering.", false);

        forceRender();
    }, [group]);

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
                            {uniqueApps.map(appName => (
                                group.data?.status?.applications?.map(app => app.metadata.name).includes(appName) ? null : (
                                    <ListRow
                                        key={appName}
                                        left={
                                            <Flex gap="10px">
                                                <SafeLogo name={appName} type="APPLICATION" size="30px" cacheBust={reloadId.toString()}/>
                                                {appName}
                                            </Flex>
                                        }
                                        right={
                                            <Button
                                                onClick={async () => {
                                                    await invokeCommand(AppStore.assignApplicationToGroup({
                                                        name: appName,
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
                        <Box maxWidth="800px" ml="auto" mr="auto">
                            <Box mb="10px">
                                <Link to="/applications/studio/groups"><Icon name="heroArrowLeft" /> Back to application groups</Link>
                            </Box>
                            <h3 className="title">Edit group</h3>
                        </Box>
                    }
                    headerSize={80}
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

                                <Flex justifyContent="right" mb="30px">
                                    <Button type="button" onClick={async () => {
                                        if (!group.data) return;

                                        const titleField = groupTitleField.current;
                                        if (titleField === null) return;

                                        const newTitle = titleField.value;
                                        if (newTitle === "") {
                                            snackbarStore.addFailure("Title cannot be empty", false);
                                            return;
                                        }

                                        const descriptionField = groupDescriptionField.current;
                                        if (descriptionField === null) return;

                                        const newDescription = descriptionField.value;

                                        const success = await invokeCommand(AppStore.updateGroup({
                                            id: group.data.metadata.id,
                                            newTitle: newTitle,
                                            newDescription: newDescription,
                                            newDefaultFlavor: defaultApplication,
                                            newLogoHasText: logoHasText
                                            // tags
                                            // TODO: app ordering
                                        }));
                                        refresh();

                                        if (success) {
                                            snackbarStore.addSuccess("Changes saved", false);
                                        }
                                    }}>Save changes</Button>
                                </Flex>

                                <Heading.h4>Logo</Heading.h4>
                                <Flex justifyContent="space-between">
                                    <Box><SafeLogo name={id.toString()} type="GROUP" size={"32px"} cacheBust={reloadId.toString()}/></Box>
                                    <Flex justifyContent="right" gap={"8px"}>
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
                                                        refresh();
                                                        dialogStore.success();
                                                    }
                                                }}
                                            />
                                        </label>

                                        <ConfirmationButton
                                            icon="heroTrash"
                                            onAction={async () => {
                                                await invokeCommand(AppStore.removeLogoFromGroup({id}));
                                                refresh();
                                            }}
                                        />
                                    </Flex>
                                </Flex>

                                <Heading.h4 mt={"30px"}>Logo properties</Heading.h4>
                                <Flex alignItems={"center"} gap={"8px"} my={16}>
                                    <Toggle checked={logoHasText} onChange={() => setLogoHasText(!logoHasText)} />
                                    <span onClick={() => setLogoHasText(!logoHasText)}>Logo contains text</span>
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

                                            <UploadAppAndTool
                                                onError={setAppUploadErr}
                                                onSuccess={refresh}
                                                style={{marginLeft: "8px", height: "25px"}}
                                            />
                                        </Flex>
                                        <Flex width="max-content" ml="auto" mr="68px">Default</Flex>
                                    </Flex>

                                    {appUploadError ?
                                        <Error error={appUploadError} clearError={() => setAppUploadErr(null)} />
                                        : null
                                    }

                                    <List width="100%" data-app-list>
                                        {group.data.status?.applications?.map((app, index) => (
                                            <ListRow
                                                key={app.metadata.name}
                                                onDrop={() => swapAppPositions(currentApp.current, index)}
                                                data-idx={index}
                                                navigate={() => navigate(`/applications/studio/a/${app.metadata.name}`)}
                                                left={
                                                    <Flex
                                                        draggable
                                                        onDrag={() => currentApp.current = index}
                                                        onDragEnd={() => currentApp.current = -1}
                                                        justifyContent="left" gap="15px"
                                                    >
                                                        {hasFeature(Feature.REORDER_APP_GROUP) ? <Box height="32px" onClick={stopPropagation} onDrag={stopPropagation} marginLeft="12px" fontSize={"24px"}>=</Box> : null}
                                                        <Box><SafeLogo name={app.metadata.name} type="APPLICATION" size="30px" cacheBust={reloadId.toString()} /></Box>
                                                        <Box mt="6px">{app.metadata.title}</Box>
                                                    </Flex>
                                                }
                                                right={
                                                    <Flex justifyContent="right" gap="7px">
                                                        <Box mt="3px">
                                                            <Label>
                                                                <Checkbox
                                                                    checked={app.metadata.name === defaultApplication}
                                                                    onChange={() => {
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
                                                            <ConfirmationButton
                                                                icon="heroTrash"
                                                                onAction={async () => {
                                                                    await invokeCommand(AppStore.assignApplicationToGroup({
                                                                        name: app.metadata.name,
                                                                        group: undefined
                                                                    }))
                                                                    refresh();
                                                                }}
                                                            />
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


const RippleAnimation = makeKeyframe("ripple-animation", `
    0% {
        background-color: var(--rowActive);
    }
    100% {
        background-color: unset;
    }
`);

const PULSE_ANIMATION_SPEED = "1.2s";

const MovedAnimation = injectStyle("highlight", k => `
    ${k} {
        -webkit-animation: ${RippleAnimation} ${PULSE_ANIMATION_SPEED} ease-out;
                animation: ${RippleAnimation} ${PULSE_ANIMATION_SPEED} ease-out;
    }
`);

export default AppGroup;