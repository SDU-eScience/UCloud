import MainContainer from "@/MainContainer/MainContainer";
import {Box, Button, Flex, Icon, Input, Link, List} from "@/ui-components";
import {ItemRow} from "@/ui-components/Browse";
import React, {useCallback, useEffect} from "react";
import {ApplicationGroup, createGroup, deleteGroup, listGroups} from "../api";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import * as Heading from "@/ui-components/Heading";
import {useNavigate} from "react-router";
import {AppToolLogo} from "../AppToolLogo";
import {ListRow} from "@/ui-components/List";


export const ApplicationGroups: React.FunctionComponent = () => {
    const [filter, setTitleFilter] = React.useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const navigate = useNavigate();

    const filterRef = React.useRef<HTMLInputElement>(null);

    const [allGroups, setGroups] = useCloudAPI<ApplicationGroup[]>(
        {noop: true},
        []
    );

    useEffect(() => {
        setGroups(listGroups({}));
    }, []);


    const refresh = useCallback(() => {
        setGroups(listGroups({}));
    }, []);

    const results = React.useMemo(() =>
        allGroups.data.filter(it => it.title.toLocaleLowerCase().includes(filter.toLocaleLowerCase()))
        , [allGroups.data, filter]);
 

    useRefreshFunction(refresh);

    return (
        <MainContainer
        header={<Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Application Groups</Heading.h2>}
        main={
            <Box maxWidth="800px" width="100%" ml="auto" mr="auto">
                <form onSubmit={async e => {
                    e.preventDefault();

                    const filterField = filterRef.current;
                    if (filterField === null) return;

                    const filterValue = filterField.value;
                    if (filterValue === "") return;

                    await invokeCommand(createGroup({title: filterValue}))
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
                            navigate={() => navigate(`/applications/studio/g/${group.id}`)} 
                            left={
                                <Flex justifyContent="left">
                                        <AppToolLogo name={group.id.toString()} type="GROUP" size="25px" />
                                        <Box ml="10px">
                                            {group.title}
                                        </Box>
                                </Flex>
                            } right={
                                <Button onClick={() => {
                                    invokeCommand(deleteGroup({id: group.id}));
                                    refresh();
                                }} height="25px" color="red"><Icon name="trash" /></Button>
                            }
                        />
                    ))}
                </List>
            </Box>
        }
    />);
};

export default ApplicationGroups;