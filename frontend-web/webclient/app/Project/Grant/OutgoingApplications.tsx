import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import * as Heading from "@/ui-components/Heading";
import {ListV2 as PaginationList} from "@/Pagination";
import {MainContainer} from "@/MainContainer/MainContainer";
import {GrantApplication, GrantApplicationFilter, grantApplicationFilterPrettify, listOutgoingApplications} from ".";
import {GrantApplicationList} from "./IngoingApplications";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import VerticalButtonGroup from "@/ui-components/VerticalButtonGroup";
import {Box, Button, Flex, Link} from "@/ui-components";
import {Center} from "@/UtilityComponents";
import {TextP} from "@/ui-components/Text";
import styled from "styled-components";
import {PageV2} from "@/UCloud";
import {useEffectSkipMount} from "@/UtilityFunctions";
import {EnumFilter, ResourceFilter} from "@/Resource/Filter";
import {BrowseType} from "@/Resource/BrowseType";
import {useProjectId} from "..";

export const FilterTrigger = styled.div`
    user-select: none;
    display: inline-block;
    width: calc(100% - 30px);
`;

export const OutgoingApplications: React.FunctionComponent = () => {
    const [scrollGeneration, setScrollGeneration] = useState(0);
    const [outgoingInvites, setParams] = useCloudAPI<PageV2<GrantApplication>>({noop: true}, emptyPageV2);
    const [filters, setFilters] = useState<Record<string, string>>({});

    useSidebarPage(SidebarPages.Projects);
    useTitle("Grant Applications");

    const loadMore = useCallback(() => {
        setParams(listOutgoingApplications({
            itemsPerPage: 50,
            next: outgoingInvites.data.next,
            filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.SHOW_ALL
        }));
    }, [outgoingInvites.data, filters]);

    useEffect(() => {
        setParams(listOutgoingApplications({
            itemsPerPage: 25,
            filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.SHOW_ALL
        }));
    }, [filters]);

    useEffectSkipMount(() => {
        setScrollGeneration(prev => prev + 1);
    }, [filters]);


    const onSortUpdated = React.useCallback((dir: "ascending" | "descending", column?: string) => { }, []);

    const [widget, pill] = EnumFilter("cubeSolid", "filterType", "Grant status", Object
        .keys(GrantApplicationFilter)
        .map(it => ({
            icon: "tags",
            title: grantApplicationFilterPrettify(it as GrantApplicationFilter),
            value: it
        }))
    );

    const projectId = useProjectId();

    return (
        <MainContainer
            headerSize={58}
            header={<Heading.h3>Grant Applications</Heading.h3>}
            sidebar={
                <VerticalButtonGroup>
                    <Link to={`/projects/browser/new`}><Button>Create Application</Button></Link>

                    <ResourceFilter
                        browseType={BrowseType.MainContent}
                        pills={[pill]}
                        sortEntries={[]}
                        sortDirection={"ascending"}
                        filterWidgets={[widget]}
                        onSortUpdated={onSortUpdated}
                        readOnlyProperties={{}}
                        properties={filters}
                        setProperties={setFilters}
                    />
                </VerticalButtonGroup>
            }
            main={
                <PaginationList
                    loading={outgoingInvites.loading}
                    infiniteScrollGeneration={scrollGeneration}
                    onLoadMore={loadMore}
                    page={outgoingInvites.data}
                    pageRenderer={pageRenderer}
                    customEmptyPage={
                        <Flex alignItems={"center"} justifyContent={"center"} height={"300px"}>
                            <Box maxWidth={700}>
                                <Box mb={16}>
                                    <TextP>
                                        You don&#39;t currently have any active outgoing applications for resources.
                                        In order to create a project, you must submit an application. Projects grant
                                        you the following benefits:

                                        <ul>
                                            <li>More resources for compute and storage</li>
                                            <li>A space to collaborate with other users</li>
                                        </ul>
                                    </TextP>
                                </Box>

                                <div>
                                    <Center>
                                        <Box mb="8px">
                                            <Link to={projectId ? "/project/grants/existing" : "/project/grants/personal"}><Button width="165px">Create Application</Button></Link>
                                        </Box>
                                        <Box>
                                            <Link to="/project/grants/new"><Button width="165px">Create Project</Button></Link>
                                        </Box>
                                    </Center>
                                </div>
                            </Box>
                        </Flex>
                    }
                />
            }
        />
    );

    function pageRenderer(items: GrantApplication[]): JSX.Element {
        return <GrantApplicationList applications={items} />;
    }
};

export default OutgoingApplications;
