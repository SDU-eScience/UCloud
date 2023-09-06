import * as UCloud from "@/UCloud";
import MainContainer from "@/MainContainer/MainContainer";
import {Box, Button, Checkbox, DataList, Flex, Icon, Input, Label, Relative, TextArea} from "@/ui-components";
import React, {useEffect, useState} from "react";
import {RetrieveGroupResponse, clearLogo, setGroup, retrieveGroup, updateGroup, uploadLogo} from "../api";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import * as Heading from "@/ui-components/Heading";
import {useNavigate, useParams} from "react-router";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";
import {AppToolLogo, groupLogoCache} from "../AppToolLogo";
import {stopPropagation} from "@/UtilityFunctions";
import {Tag} from "../Card";
import {compute} from "@/UCloud";
import ReactModal from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {pageV2Of} from "@/DefaultObjects";

export const AppGroup: React.FunctionComponent = () => {
    const id = useParams<{id: string}>().id!;
    const [group, fetchGroup] = useCloudAPI<RetrieveGroupResponse|undefined>(
        {noop: true},
        undefined
    );

    const [appList, setAppList] = useCloudAPI<Page<compute.ApplicationSummaryWithFavorite>|undefined>(
        {noop: true},
        undefined
    );

    const [, setLogoCacheBust] = useState("" + Date.now());

    const groupTitleField = React.useRef<HTMLInputElement>(null);
    const groupDescriptionField = React.useRef<HTMLInputElement>(null);
    const appSearchField = React.useRef<HTMLInputElement>(null);

    const [defaultApplication, setDefaultApplication] = useState<{name: string; version: string;}|undefined>(undefined);
    const [tags, setTags] = useState<string[]>([]);
    const [selectedTag, setSelectedTag] = useState<string>("");
    const [allTags, fetchAllTags] = useCloudAPI<string[]>(
        {noop: true},
        []
    );
    const [addApplicationOpen, setAddApplicationOpen] = useState<boolean>(false);

    const [commandLoading, invokeCommand] = useCloudCommand();

    useEffect(() => {
        fetchGroup(retrieveGroup({id: id}));
        fetchAllTags(UCloud.compute.apps.listTags({}));
        setAppList(compute.apps.listAll({}));
    }, [id]);

    const refresh = () => {
        fetchGroup(retrieveGroup({id: id}));
        fetchAllTags(UCloud.compute.apps.listTags({}));
    };

    useEffect(() => {
        if (group.data) {
            setDefaultApplication(group.data.group.defaultApplication);
            setTags(group.data.group.tags);
        }
    }, [group.data]);


    const navigate = useNavigate();

    useRefreshFunction(refresh);

    return (
        !group.data ? <></> :
        <>
            <ReactModal  
                style={largeModalStyle}
                isOpen={addApplicationOpen}
                shouldCloseOnEsc
                shouldCloseOnOverlayClick
                onRequestClose={() => setAddApplicationOpen(false)}
            >
                <Flex mb="20px">
                    <form onSubmit={e => {
                        e.preventDefault();

                        const searchField = appSearchField.current;
                        if (searchField === null) return;

                        const searchValue = searchField.value;
                        if (searchValue === "") {
                            setAppList(compute.apps.listAll({}));
                            return;
                        }

                        setAppList(compute.apps.searchApps({query: searchValue}));
                    }}> 
                        <Flex>
                            <Input placeholder="Search..." inputRef={appSearchField} type="text" />
                            <Relative right="30px" top="8px" width="0px" height="0px"><button type="submit" style={{border: "none", background: "none"}}><Icon name="search" /></button></Relative>
                        </Flex>
                    </form>
                </Flex>

                {!appList.data ? <>No apps found</> : (
                    <>
                        {appList.data.items.map(app => (
                            group.data!.applications.map(app => app.metadata.name).includes(app.metadata.name) ? null : (
                                <Flex justifyContent="space-between" style={{lineHeight: "45px"}}>
                                    {app.metadata.title}
                                    <Button
                                        onClick={async () => {
                                            await invokeCommand(setGroup({applicationName: app.metadata.name, groupId: group.data!.group.id}));
                                            setAddApplicationOpen(false);
                                            refresh();
                                        }}
                                    >Add to group</Button>
                                </Flex> 
                            )
                        ))}
                    </>
                )}
            </ReactModal>
            <form onSubmit={async e => {
                e.preventDefault()

                if (!group.data) return;

                const titleField = groupTitleField.current;
                if (titleField === null) return;

                const newTitle = titleField.value;
                if (newTitle === "") {
                    snackbarStore.addFailure("Title cannot be empty", false);
                    return
                };

                const descriptionField = groupDescriptionField.current;
                if (descriptionField === null) return;

                const newDescription = descriptionField.value;

                invokeCommand(updateGroup({id: group.data.group.id, title: newTitle, description: newDescription, defaultApplication, tags}))
                navigate(`/applications/studio/groups`);
            }}>
                <MainContainer
                header={
                    <Flex justifyContent="space-between">
                        <Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Edit Group</Heading.h2>
                        <Flex justifyContent="right" mr="10px">
                            <Button type="submit">Save changes</Button>
                        </Flex>
                    </Flex>
                }
                main={
                    !group.data ? <>Not found</> : 
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto">
                        <Label>Title
                            <Input mb="20px" inputRef={groupTitleField} type="text" defaultValue={group.data.group.title}  />
                        </Label>

                        <Label>Description
                            <TextArea mb="20px" inputRef={groupDescriptionField} defaultValue={group.data.group.description} />
                        </Label>

                        <Heading.h4>Logo</Heading.h4>
                        <Flex justifyContent="space-between">
                            <Box><AppToolLogo name={id} type="GROUP" /></Box>
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
                                                if (await uploadLogo({name: id, file, type: "GROUP"})) {
                                                    setLogoCacheBust("" + Date.now());
                                                }
                                            }
                                            groupLogoCache.forget(id);
                                            refresh();
                                            dialogStore.success();
                                        }
                                    }}
                                />
                            </label>

                            <Button
                                ml="5px"
                                type="button"
                                color="red"
                                disabled={commandLoading}
                                onClick={async () => {
                                    await invokeCommand(clearLogo({type: "GROUP", name: id}));
                                    setLogoCacheBust("" + Date.now());
                                    groupLogoCache.forget(id);
                                    refresh();
                                }}
                            >
                                <Icon name="trash" />
                            </Button>

                            </Flex>
                        </Flex>

                        <Box mt="30px">
                            <Heading.h4>Tags</Heading.h4>
                            <Box mb={46} mt={26}>
                                {tags.map(tag => (
                                    <Flex key={tag} mb={16}>
                                        <Box flexGrow={1}>
                                            <Tag key={tag} label={tag} />
                                        </Box>
                                        <Box>
                                            <Button
                                                color={"red"}
                                                type={"button"}

                                                disabled={commandLoading}
                                                onClick={async () => {
                                                    if (!group.data) return;

                                                    await invokeCommand(UCloud.compute.apps.removeTag({
                                                        groupId: group.data.group.id,
                                                        tags: [tag]
                                                    }));
                                                    refresh();
                                                }}
                                            >
                                                <Icon size={16} name="trash" />
                                            </Button>
                                        </Box>
                                    </Flex>
                                ))}
                                <Flex>
                                    <Box flexGrow={1}>
                                        {allTags.data.length > 0 ?
                                            <DataList
                                                rightLabel
                                                options={allTags.data.map(tag => ({value: tag, content: tag}))}
                                                onSelect={async item => {
                                                    setSelectedTag(item);

                                                    if (commandLoading) return;
                                                    if (selectedTag === null) return;
                                                    if (selectedTag === "") return;
                                                    if (!group.data) return;

                                                    await invokeCommand(UCloud.compute.apps.createTag({
                                                        groupId: group.data.group.id,
                                                        tags: [selectedTag]
                                                    }));

                                                    refresh();
                                                }}
                                                onChange={item => setSelectedTag(item)}
                                                placeholder={"Enter or choose a tag..."}
                                            />
                                            : <></>
                                        }
                                    </Box>
                                    <Button
                                        disabled={commandLoading}
                                        type="button"
                                        width={100}
                                        attached
                                        onClick={async () => {
                                            if (commandLoading) return;

                                            if (selectedTag === null) return;
                                            if (selectedTag === "") return;
                                            if (!group.data) return;

                                            await invokeCommand(UCloud.compute.apps.createTag({
                                                groupId: group.data.group.id,
                                                tags: [selectedTag]
                                            }));

                                            refresh();
                                        }}
                                    >
                                        Add tag
                                    </Button>
                                </Flex>
                            </Box>
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
                                        mt="20px"
                                        ml="25px"
                                    >
                                        Add
                                    </Button>
                                </Flex>
                                <Box mt="25px" mr="60px">Default</Box>
                            </Flex>
                            {group.data.applications.map(app => (
                                <Flex justifyContent="space-between" key={app.metadata.name + app.metadata.version} style={{lineHeight: "48px"}}>
                                    <Flex justifyContent="left" gap="20px">
                                        <Box><AppToolLogo name={app.metadata.name} type="APPLICATION" /></Box>
                                        <Box>{app.metadata.title}</Box>
                                    </Flex>
                                    <Flex justifyContent="right">
                                        <Box>
                                            <Label>
                                                <Checkbox
                                                    checked={app.metadata.name === defaultApplication?.name}
                                                    onChange={() => {
                                                        stopPropagation;

                                                        if (app.metadata.name === defaultApplication?.name) {
                                                            setDefaultApplication(undefined);
                                                        } else {
                                                            setDefaultApplication({name: app.metadata.name, version: app.metadata.version});
                                                        }
                                                    }}
                                                />
                                            </Label>
                                        </Box>
                                        <Box>
                                            <Button
                                                mt="2px"
                                                color="red"
                                                type="button"
                                                onClick={async () => {
                                                    await invokeCommand(setGroup({applicationName: app.metadata.name, groupId: undefined}))
                                                    refresh();
                                                }}
                                            >
                                                <Icon name="trash" />
                                            </Button>
                                        </Box>
                                    </Flex>
                                </Flex>
                            ))}
                        </Box>
                    </Box>
                }
            />
        </form>
    </>);
};

export default AppGroup;