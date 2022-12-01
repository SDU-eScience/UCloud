import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {MainContainer} from "@/MainContainer/MainContainer";
import {useCloudAPI} from "@/Authentication/DataHook";
import {
    GrantApplicationFilter,
    grantApplicationFilterPrettify,
} from "@/Project/Grant/index";
import {emptyPage} from "@/DefaultObjects";
import * as Pagination from "@/Pagination";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {Flex, List, VerticalButtonGroup} from "@/ui-components";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {useNavigate} from "react-router";
import {dateToString} from "@/Utilities/DateUtilities";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import Icon, {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {EnumFilter, ResourceFilter} from "@/Resource/Filter";
import {BrowseType} from "@/Resource/BrowseType";
import {browseGrantApplications, GrantApplication, State} from "@/Project/Grant/GrantApplicationTypes";
import {PageV2} from "@/UCloud";
import {useProjectFromParams} from "../Api";

export const GrantApplications: React.FunctionComponent<{ingoing: boolean}> = (props) => {
    const [scrollGeneration, setScrollGeneration] = useState(0);
    const [applications, fetchApplications] = useCloudAPI<PageV2<GrantApplication>>(
        {noop: true},
        emptyPage
    );

    const [filters, setFilters] = useState<Record<string, string>>({});
    const baseName = props.ingoing ? "Ingoing" : "Outgoing";

    const paramProject = useProjectFromParams(`${baseName} Applications`);
    const projectIdToUse = !paramProject.isPersonalWorkspace ? paramProject.projectId : undefined;

    useRefreshFunction(() => {
        fetchApplications({
            ...browseGrantApplications({
                itemsPerPage: 50,
                includeIngoingApplications: props.ingoing,
                includeOutgoingApplications: !props.ingoing,
                filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.ACTIVE
            }), projectOverride: projectIdToUse
        });
        setScrollGeneration(prev => prev + 1);
    });

    useTitle(`${baseName} Applications`);

    useEffect(() => {
        setScrollGeneration(prev => prev + 1);
        fetchApplications({
            ...browseGrantApplications({
                includeIngoingApplications: props.ingoing,
                includeOutgoingApplications: !props.ingoing,
                itemsPerPage: 50,
                filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.ACTIVE
            }), projectOverride: projectIdToUse
        });
    }, [projectIdToUse, filters]);

    const loadMore = useCallback(() => {
        fetchApplications(browseGrantApplications({
            includeIngoingApplications: props.ingoing,
            includeOutgoingApplications: !props.ingoing,
            itemsPerPage: 50, next: applications.data.next,
            filter: (filters.filterType as GrantApplicationFilter | undefined) ?? GrantApplicationFilter.ACTIVE
        }));
    }, [applications.data, filters]);

    useLoading(applications.loading);
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
        header={<ProjectBreadcrumbs
            allowPersonalProject={false}
            omitActiveProject
            crumbs={paramProject.breadcrumbs}
        />}
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
                page={applications.data}
                loading={applications.loading}
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
    const navigate = useNavigate();
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
                    navigate={() => navigate(`/project/grants/view/${app.id}`)}
                    icon={
                        slim ? null : (
                            <UserAvatar
                                avatar={avatars.cache[app.createdBy] ?? defaultAvatar}
                                width={"45px"}
                            />
                        )
                    }
                    truncateWidth="230px"
                    left={
                        <Flex width={1}>
                            {app.currentRevision.document.recipient.type === "personalWorkspace" ? app.currentRevision.document.recipient.username :
                                app.currentRevision.document.recipient.type === "newProject" ? app.currentRevision.document.recipient.title :
                                    app.status.projectTitle}
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
                                icon={app.currentRevision.document.recipient.type === "personalWorkspace" ? "user" : "projects"}
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

export default GrantApplications;
