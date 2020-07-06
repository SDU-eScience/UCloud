import * as React from "react";
import {useCloudAPI, APICallParameters} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import {listOutgoingInvites} from "Project";
import {Page, PaginationRequest} from "Types";
import {List as PaginationList} from "Pagination";
import {MainContainer} from "MainContainer/MainContainer";
import {GrantApplication} from ".";
import {GrantApplicationList} from "./IngoingApplications";
import {useDispatch} from "react-redux";
import {setActivePage} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";

const listOutgoingApplications = (payload: PaginationRequest): APICallParameters<PaginationRequest> => ({
    path: "/grant/outgoing",
    method: "GET",
    payload
});

export const OutgoingApplications: React.FunctionComponent = () => {
    const [outgoingInvites, setParams] =
        useCloudAPI<Page<GrantApplication>>(listOutgoingApplications({itemsPerPage: 25, page: 0}), emptyPage);

    const dispatch = useDispatch();

    React.useEffect(() => {
        dispatch(setActivePage(SidebarPages.Projects));
        return () => {
            dispatch(setActivePage(SidebarPages.None));
        };
    }, []);

    return (
        <MainContainer
            header={<Heading.h3>Outgoing Applications</Heading.h3>}
            sidebar={null}
            main={
                <PaginationList
                    loading={outgoingInvites.loading}
                    onPageChanged={(newPage, oldPage) =>
                        setParams(listOutgoingInvites({itemsPerPage: oldPage.itemsPerPage, page: newPage}))}
                    page={outgoingInvites.data}
                    pageRenderer={pageRenderer}
                />
            }
        />
    );

    function pageRenderer(page: Page<GrantApplication>): JSX.Element {
        return <GrantApplicationList applications={page.items} />;
    }
};
