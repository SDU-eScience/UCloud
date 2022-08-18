import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {MainContainer} from "@/MainContainer/MainContainer";
import {useCloudAPI} from "@/Authentication/DataHook";
import {
    GrantApplicationFilter,
    grantApplicationFilterPrettify,
    GrantApplicationStatus,
    ingoingGrantApplications,
    IngoingGrantApplicationsResponse
} from "@/Project/Grant/index";
import {emptyPage} from "@/DefaultObjects";
import {useProjectManagementStatus} from "@/Project";
import * as Pagination from "@/Pagination";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {Flex, List, Text, Truncate, VerticalButtonGroup} from "@/ui-components";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {useHistory} from "react-router";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {dateToString} from "@/Utilities/DateUtilities";
import Icon, {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {EnumFilter, ResourceFilter} from "@/Resource/Filter";
import {BrowseType} from "@/Resource/BrowseType";
import {GrantApplication, State} from "@/Project/Grant/GrantApplicationTypes";

export const IngoingApplications: React.FunctionComponent = () => {
    const {projectId} = useProjectManagementStatus({isRootComponent: true});
    const [scrollGeneration, setScrollGeneration] = useState(0);
    const [ingoingApplications, fetchIngoingApplications] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPage
    );

    const [filters, setFilters] = useState<Record<string, string>>({});

    useRefreshFunction(() => {
        fetchIngoingApplications(
            ingoingGrantApplications({
                itemsPerPage: 50,
                filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.SHOW_ALL
            })
        );
        setScrollGeneration(prev => prev + 1);
    });

    useTitle("Ingoing Applications");

    useEffect(() => {
        setScrollGeneration(prev => prev + 1);
        fetchIngoingApplications(ingoingGrantApplications({
            itemsPerPage: 50, filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.SHOW_ALL
        }));
    }, [projectId, filters]);

    const loadMore = useCallback(() => {
        fetchIngoingApplications(ingoingGrantApplications({
            itemsPerPage: 50, next: ingoingApplications.data.next,
            filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.SHOW_ALL
        }));
    }, [ingoingApplications.data, filters]);

    useLoading(ingoingApplications.loading);
    useSidebarPage(SidebarPages.Projects);

    const onSortUpdated = React.useCallback((dir: "ascending" | "descending", column?: string) => { }, []);

    const [widget, pill] = EnumFilter("cubeSolid", "filterType", "Grant status", Object
        .keys(GrantApplicationFilter)
        .map(it => ({
            icon: "tags",
            title: grantApplicationFilterPrettify(it as GrantApplicationFilter),
            value: it
        }))
    );

    return <MainContainer
        header={<ProjectBreadcrumbs crumbs={[{title: "Ingoing Applications"}]} />}
        sidebar={<VerticalButtonGroup>
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
            <Pagination.ListV2
                infiniteScrollGeneration={scrollGeneration}
                page={ingoingApplications.data}
                loading={ingoingApplications.loading}
                onLoadMore={loadMore}
                pageRenderer={items => <GrantApplicationList applications={items} />}
            />
        }
    />;
};

export const GrantApplicationList: React.FunctionComponent<{
    applications: GrantApplication[],
    slim?: boolean
}> = ({applications, slim = false}) => {
    const history = useHistory();
    const avatars = useAvatars();
    useEffect(() => {
        avatars.updateCache(applications.map(it => it.createdBy));
    }, [applications]);
    return (
        <List>
            {applications.map(app => {
                let icon: IconName;
                let iconColor: ThemeColor;
                switch (app.status.overallState) {
                    case State.APPROVED:
                        icon = "check";
                        iconColor = "green";
                        break;
                    case State.CLOSED:
                    case State.REJECTED:
                        icon = "close";
                        iconColor = "red";
                        break;
                    case State.IN_PROGRESS:
                        icon = "ellipsis";
                        iconColor = "black";
                        break;
                }

                return <ListRow
                    key={app.id}
                    navigate={() => history.push(`/project/grants/view/${app.id}`)}
                    icon={
                        slim ? null : (
                            <UserAvatar
                                avatar={avatars.cache[app.createdBy] ?? defaultAvatar}
                                width={"45px"}
                            />
                        )
                    }
                    truncateWidth="230px"
                    //TODO (HENRIK: Might need to show something else here instead of an id. This is useless)
                    left={
                        <Flex width={1}>
                            <Truncate title={app.id}>
                                {app.id}
                            </Truncate>
                            {slim ? null : (
                                <Truncate>
                                    {/*
                                        Is small the correct tag to use here?
                                        Does it have the correct semantic meaning or should we just use
                                        <Text> with smaller font?
                                    */}
                                    <small>{app.currentRevision.document.form.text}</small>
                                </Truncate>
                            )}
                        </Flex>
                    }
                    right={<Icon ml="4px" name={icon} color={iconColor} />}
                    leftSub={
                        <>
                            {slim ? null : (
                                <ListRowStat icon="calendar">{dateToString(app.createdAt)}</ListRowStat>
                            )}

                            <ListRowStat icon={"edit"}>
                                {dateToString(app.updatedAt)}
                            </ListRowStat>
                            <ListRowStat
                                icon={(console.log(app.currentRevision.document.recipient), app.currentRevision.document.recipient.type === "personalWorkspace" ? "user" : "projects")}
                            >
                                {app.currentRevision.document.recipient.type === "personalWorkspace" ? "Personal" :
                                    app.currentRevision.document.recipient.type === "newProject" ? "New project" :
                                        "Existing project"}
                            </ListRowStat>
                        </>
                    }
                />
            })}
        </List>
    );
};

export default IngoingApplications;
