import MainContainer from "@/MainContainer/MainContainer";
import {Box, Button, Flex, Icon, Input, Label, List, TextArea} from "@/ui-components";
import {ItemRow} from "@/ui-components/Browse";
import React, {useCallback, useEffect, useState} from "react";
import {ApplicationGroup, RetrieveGroupResponse, createGroup, deleteGroup, listGroups, retrieveGroup, updateGroup} from "../api";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import * as Heading from "@/ui-components/Heading";
import {useNavigate, useParams} from "react-router";
import {ButtonClass} from "@/ui-components/Button";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";


export const AppGroup: React.FunctionComponent = () => {
    const id = useParams<{id: string}>().id!;
    const [group, setGroup] = useCloudAPI<RetrieveGroupResponse|undefined>(
        {noop: true},
        undefined
    );

    const groupTitleField = React.useRef<HTMLInputElement>(null);
    const groupDescriptionField = React.useRef<HTMLInputElement>(null);

    const [commandLoading, invokeCommand] = useCloudCommand();

    useEffect(() => {
        setGroup(retrieveGroup({id: id}))
    }, [id]);



    const navigate = useNavigate();

    //useRefreshFunction(refresh);

    return (
        <form onSubmit={async e => {
            e.preventDefault()

            if (!group.data) return;

            const titleField = groupTitleField.current;
            if (titleField === null) return;

            const newTitle = titleField.value;
            if (newTitle === "") return;

            const descriptionField = groupDescriptionField.current;
            if (descriptionField === null) return;

            const newDescription = descriptionField.value;
            if (newDescription === "") return;

            invokeCommand(updateGroup({id: group.data.group.id, title: newTitle, description: newDescription}))
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

                    <Label>Logo</Label>
                    <Flex justifyContent="space-between">
                        <Box>{group.data.group.logo}</Box>
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
                                            /*if (await uploadLogo({name, file, type: "APPLICATION"})) {
                                                setLogoCacheBust("" + Date.now());
                                            }*/
                                        }
                                        dialogStore.success();
                                    }
                                }}
                            />
                        </label>

                        <Button
                            type="button"
                            color="red"
                            //disabled={commandLoading}
                            onClick={async () => {
                                /*await invokeCommand(clearLogo({type: "APPLICATION", name}));
                                setLogoCacheBust("" + Date.now());*/
                            }}
                        >
                            <Icon name="trash" />
                        </Button>

                        </Flex>
                    </Flex>

                    <Label>Default application</Label>
                </Box>
            }
        />
    </form>);
};

export default AppGroup;