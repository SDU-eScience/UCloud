import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Input, Box, Flex, Button, Card, Truncate, Icon} from "ui-components";
import * as Heading from "ui-components/Heading";
import {defaultAvatar, AvatarType} from "UserSettings/Avataaar";
import * as Pagination from "Pagination";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {Page} from "Types";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {listGroupMembersRequest, addGroupMember, removeGroupMemberRequest, ListGroupMembersRequestProps} from "./api";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, inDevEnvironment} from "UtilityFunctions";
import {GridCardGroup} from "ui-components/Grid";
import {usePromiseKeeper} from "PromiseKeeper";
import {Spacer} from "ui-components/Spacer";
import {Avatar} from "AvataaarLib";


interface DetailedGroupViewProps {
    name: string;
}

const memberNames = ["Jenny the Long-named person", "Emiliano Molinaro", "Tom", "Hello", "What", "Is", "This"];

function DetailedGroupView({name}: DetailedGroupViewProps): JSX.Element {
    const [activeGroup, fetchActiveGroup,] = useCloudAPI<Page<string>, ListGroupMembersRequestProps>(
        listGroupMembersRequest({group: name ?? "", itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [loading, setLoading] = React.useState(false);

    const promises = usePromiseKeeper();
    const [avatars, setAvatars] = React.useState<AvatarType[]>([]);
    React.useEffect(() => {
        /* FIXME:  */
        promises.makeCancelable(
            Client.post<{avatars: {[key: string]: AvatarType}}>("/avatar/bulk", {usernames: activeGroup.data.items})
        ).promise.then(it =>
            setAvatars(Object.values(it.response.avatars))
        ).catch(it => console.warn(it));
    }, [activeGroup.data.items.length, activeGroup.data.pageNumber]);


    const memberRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (name) fetchActiveGroup(listGroupMembersRequest({group: name ?? "", itemsPerPage: 25, page: 0}));
    }, [name]);


    /* FIXME: Remove before pushing */
    /* if (inDevEnvironment() && activeGroup.data.items.length === 0) {
        activeGroup.data.items = memberNames;
        activeGroup.data.itemsInTotal = memberNames.length;
    } */

    if (activeGroup.loading) return <MainContainer main={<LoadingSpinner size={48} />} />;
    if (activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{name}&apos;.</Text>
    } />;

    return <MainContainer
        main={
            <Pagination.List
                loading={activeGroup.loading}
                onPageChanged={(newPage, page) => fetchActiveGroup(listGroupMembersRequest({
                    group: name,
                    itemsPerPage: page.itemsPerPage,
                    page: newPage
                }))}
                customEmptyPage={<Text>No members in group.</Text>}
                page={activeGroup.data}
                pageRenderer={page =>
                    <GridCardGroup minmax={220}>
                        {memberNames.map(member =>
                            <Card borderRadius="10px" height="auto" minWidth="220px" key={member}>
                                <Spacer
                                    left={<Flex ml="88px" width="60px" style={{textAlign: "center"}} alignItems="center" mt="8px" height="48px">
                                        <Avatar avatarStyle="Circle" {...avatars[member] ?? defaultAvatar} />
                                    </Flex>}
                                    right={
                                        <Icon
                                            cursor="pointer"
                                            mt="8px"
                                            mr="8px"
                                            color="red"
                                            name="close"
                                            onClick={() => removeMember(member)} size="20px"
                                        />
                                    }
                                />
                                <Text fontSize="20px" mx="8px" my="15px">{member}{member}{member}</Text>
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
                    <Flex><Input type="text" ref={memberRef} width="300px" />
                    <Button disabled={loading || activeGroup.loading} attached>Add</Button></Flex>
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
