import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Input, Box, Flex, Button, List, Card} from "ui-components";
import * as Heading from "ui-components/Heading";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";
import * as Pagination from "Pagination";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {Page} from "Types";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {listGroupMembersRequest, addGroupMember, removeGroupMemberRequest} from "./api";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {Spacer} from "ui-components/Spacer";
import {GridCardGroup} from "ui-components/Grid";


interface DetailedGroupViewProps {
    name: string;
}

const memberNames = ["Jenny", "Kenny", "Tom", "Hello", "What", "Is", "This"];

function DetailedGroupView({name}: DetailedGroupViewProps): JSX.Element {
    const [activeGroup, fetchActiveGroup, params] = useCloudAPI<Page<string>>(
        listGroupMembersRequest({group: name ?? ""}),
        emptyPage
    );

    const memberRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (name)
            fetchActiveGroup(listGroupMembersRequest({group: name ?? ""}));
    }, [name]);

    if (activeGroup.loading) return <LoadingSpinner size={24} />;
    if (activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{name}&apos;.</Text>
    } />;

    return <MainContainer
        main={
            <Pagination.List
                loading={activeGroup.loading}
                onPageChanged={page => {throw new Error("TODO");}}
                customEmptyPage={<Text>No members in group.</Text>}
                page={activeGroup.data}
                pageRenderer={page =>
                    <GridCardGroup minmax={220}>
                        {page.items.map(member =>
                            <Card minWidth="220px" key={member}>
                                <Flex my="5px">
                                    <UserAvatar avatar={defaultAvatar} />
                                    <Text mt="10px">{member}</Text>
                                </Flex>
                                <Button my="5px" color="red" onClick={() => removeMember(member)}>Remove</Button>
                            </Card>
                        )}
                    </GridCardGroup>
                }
            />
        }
        sidebar={<Box>
            <Button width="100%" mb="5px">Rename group</Button>
            <Button width="100%" color="red">Delete group</Button>
        </Box>}
        headerSize={120}
        header={<>
            <Box>
                <Text
                    style={{wordBreak: "break-word"}}
                    fontSize="25px"
                    width="100%"
                >{name}</Text>
                <Heading.h5>Members: {activeGroup.data.itemsInTotal}</Heading.h5>
                <form onSubmit={addMember}>
                    <Flex><Input type="text" ref={memberRef} width="300px" /><Button attached>Add</Button></Flex>
                </form>
            </Box>
        </>}
    />;

    async function addMember(e: React.FormEvent): Promise<void> {
        e.preventDefault();
        e.stopPropagation();
        if (memberRef.current == null) return;

        const member = memberRef.current.value;
        if (!member) {
            snackbarStore.addFailure("Please add a username");
            return;
        }


        const {path, payload} = addGroupMember({group: name, memberUsername: member});
        try {
            Client.put(path!, payload);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to add member."));
        }
    }

    async function removeMember(member: string): Promise<void> {
        const {path, payload} = removeGroupMemberRequest({group: name, memberUsername: member});
        try {
            Client.delete(path!, payload);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to remove member."));
        }
    }
}

export default DetailedGroupView;
