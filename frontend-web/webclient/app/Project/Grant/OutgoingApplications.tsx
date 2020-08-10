import * as React from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import {listOutgoingInvites} from "Project";
import {List as PaginationList} from "Pagination";
import {MainContainer} from "MainContainer/MainContainer";
import {GrantApplication} from ".";
import {GrantApplicationList} from "./IngoingApplications";
import {useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {Box, Button, Flex, Link} from "ui-components";
import {Center} from "UtilityComponents";
import {TextP} from "ui-components/Text";

const listOutgoingApplications = (payload: PaginationRequest): APICallParameters<PaginationRequest> => ({
    path: "/grant/outgoing",
    method: "GET",
    payload
});

export const OutgoingApplications: React.FunctionComponent = () => {
    const [outgoingInvites, setParams] =
        useCloudAPI<Page<GrantApplication>>(listOutgoingApplications({itemsPerPage: 25, page: 0}), emptyPage);

    useSidebarPage(SidebarPages.Projects);
    useTitle("Active Resource Applications");

    return (
        <MainContainer
            headerSize={58}
            header={<Heading.h3>Active Resource Applications</Heading.h3>}
            sidebar={
                <VerticalButtonGroup>
                    <Link to={`/projects/browser/new`}><Button>Create Application</Button></Link>
                </VerticalButtonGroup>
            }
            main={
                <PaginationList
                    loading={outgoingInvites.loading}
                    onPageChanged={(newPage, oldPage) =>
                        setParams(listOutgoingInvites({itemsPerPage: oldPage.itemsPerPage, page: newPage}))}
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

                                <Center>
                                    <Link to={`/projects/browser/new`}><Button>Create Application</Button></Link>
                                </Center>
                            </Box>
                        </Flex>
                    }
                />
            }
        />
    );

    function pageRenderer(page: Page<GrantApplication>): JSX.Element {
        return <GrantApplicationList applications={page.items}/>;
    }
};
