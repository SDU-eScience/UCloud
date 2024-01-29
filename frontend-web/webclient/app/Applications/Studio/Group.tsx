import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Checkbox, DataList, Flex, Icon, Input, Label, Relative, TextArea} from "@/ui-components";
import React, {useCallback, useEffect, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {useNavigate, useParams} from "react-router";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {AppToolLogo, groupLogoCache} from "../AppToolLogo";
import {stopPropagation} from "@/UtilityFunctions";
import {Tag} from "../Card";
import ReactModal from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import List, {ListRow} from "@/ui-components/List";
import {CardClass} from "@/ui-components/Card";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";

export const AppGroup: React.FunctionComponent = () => {
    const id = parseInt(useParams<{ id: string }>().id ?? "-1");

    const [group, fetchGroup] = useCloudAPI(AppStore.retrieveGroup({id}), null);
    const [filter, setFilter] = useState("");
    const [appList] = useCloudAPI(
        AppStore.listAllApplications({}),
        { items: [] },
    );

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
    const [tags, setTags] = useState<string[]>([]);
    const [selectedTag, setSelectedTag] = useState<string>("");
    const [allTags, fetchAllTags] = useCloudAPI<string[]>(
        {noop: true},
        []
    );
    const [addApplicationOpen, setAddApplicationOpen] = useState<boolean>(false);

    const [commandLoading, invokeCommand] = useCloudCommand();

    useEffect(() => {
        fetchGroup(AppStore.retrieveGroup({id}));
    }, [id]);

    const refresh = useCallback(() => {
        fetchGroup(AppStore.retrieveGroup({id}));
    }, [id]);

    useEffect(() => {
        if (group.data) {
            setDefaultApplication(group.data?.specification?.defaultFlavor ?? undefined);
        }
    }, [group.data]);

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
                                                <AppToolLogo name={app} type="APPLICATION" size="30px"/>
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
                            <Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Edit Group</Heading.h2>
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
                                    <Box><AppToolLogo name={id.toString()} type="GROUP"/></Box>
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

                                <Box mt="30px">
                                    <Heading.h4>Tags</Heading.h4>
                                    <form onSubmit={async e => {
                                        snackbarStore.addFailure("TODO", false);
                                        /*
                                        e.preventDefault();

                                        if (commandLoading) return;

                                        if (selectedTag === null) return;
                                        if (selectedTag === "") return;
                                        if (!group.data) return;

                                        await invokeCommand(UCloud.compute.apps.createTag({
                                            groupId: group.data.group.id,
                                            tags: [selectedTag]
                                        }));

                                        setSelectedTag("");

                                        refresh();
                                         */
                                    }}>
                                        <Box mb={46} mt={26}>
                                            {tags.map(tag => (
                                                <Flex key={tag} mb={16}>
                                                    <Box flexGrow={1}>
                                                        <Tag key={tag} label={tag}/>
                                                    </Box>
                                                    <Box>
                                                        <Button
                                                            color={"errorMain"}
                                                            type={"button"}

                                                            disabled={commandLoading}
                                                            onClick={async () => {
                                                                snackbarStore.addFailure("TODO", false);
                                                                /*
                                                                if (!group.data) return;

                                                                await invokeCommand(UCloud.compute.apps.removeTag({
                                                                    groupId: group.data.group.id,
                                                                    tags: [tag]
                                                                }));
                                                                refresh();
                                                                 */
                                                            }}
                                                        >
                                                            <Icon size={16} name="trash"/>
                                                        </Button>
                                                    </Box>
                                                </Flex>
                                            ))}
                                            <Flex>
                                                <Box flexGrow={1}>
                                                    <DataList
                                                        rightLabel
                                                        options={allTags.data.map(tag => ({value: tag, content: tag}))}
                                                        onSelect={async item => {
                                                            setSelectedTag(item);
                                                        }}
                                                        onChange={item => setSelectedTag(item)}
                                                        placeholder={"Enter or choose a tag..."}
                                                    />
                                                </Box>
                                                <Button
                                                    disabled={commandLoading}
                                                    type="submit"
                                                    width={100}
                                                    attached
                                                >
                                                    Add tag
                                                </Button>
                                            </Flex>
                                        </Box>
                                    </form>
                                </Box>

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
                                                        <Box><AppToolLogo name={app.metadata.name} type="APPLICATION"
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