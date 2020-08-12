import * as React from "react";
import {useEffect} from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {useCloudAPI} from "Authentication/DataHook";
import {ingoingGrantApplications, IngoingGrantApplicationsResponse, GrantApplication} from "Project/Grant/index";
import {emptyPage} from "DefaultObjects";
import {useProjectManagementStatus} from "Project";
import * as Pagination from "Pagination";
import {ListRow, ListRowStat} from "ui-components/List";
import {Flex, Icon, List, Text, Tooltip} from "ui-components";
import {creditFormatter} from "Project/ProjectUsage";
import {EllipsedText} from "ui-components/Text";
import {useAvatars} from "AvataaarLib/hook";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {useHistory} from "react-router";
import {useDispatch} from "react-redux";
import {setActivePage} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {dateToString} from "Utilities/DateUtilities";

export const IngoingApplications: React.FunctionComponent = () => {
    const {projectId} = useProjectManagementStatus({isRootComponent: true});
    const dispatch = useDispatch();
    const [ingoingApplications, fetchIngoingApplications] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPage
    );

    useEffect(() => {
        fetchIngoingApplications(ingoingGrantApplications({itemsPerPage: 25, page: 0}));
    }, [projectId]);

    useEffect(() => {
        dispatch(setActivePage(SidebarPages.Projects));
        return () => {
            dispatch(setActivePage(SidebarPages.None));
        };
    }, []);

    return <MainContainer
        header={<ProjectBreadcrumbs crumbs={[{title: "Ingoing Applications"}]} />}
        sidebar={null}
        main={
            <Pagination.List
                page={ingoingApplications.data}
                loading={ingoingApplications.loading}
                onPageChanged={() => 42}
                pageRenderer={page => <GrantApplicationList applications={page.items} />}
            />
        }
    />;
};

export const GrantApplicationList: React.FunctionComponent<{applications: GrantApplication[]}> = props => {
    const history = useHistory();
    const avatars = useAvatars();
    useEffect(() => {
        avatars.updateCache(props.applications.map(it => it.requestedBy));
    }, [props.applications]);
    return (
        <List>
            {props.applications.map(app => {
                return <ListRow
                    key={app.id}
                    navigate={() => history.push(`/project/grants/view/${app.id}`)}
                    icon={
                        <UserAvatar
                            avatar={avatars.cache[app.requestedBy] ?? defaultAvatar}
                            width={"45px"}
                        />
                    }
                    left={
                        <Flex>
                            <Text mr={16}><strong>{app.grantRecipientTitle}</strong></Text>
                            <EllipsedText maxWidth={420}>
                                {/*
                                    Is small the correct tag to use here?
                                    Does it have the correct semantic meaning or should we just use
                                    <Text> with smaller font?
                                */}
                                <small>{app.document}</small>
                            </EllipsedText>
                        </Flex>
                    }
                    right={(
                        <Tooltip
                            trigger={
                                <Flex width={170} alignItems={"center"}>
                                    <Icon name={"grant"} mr={8} />
                                    <Flex flexGrow={1} justifyContent={"flex-end"}>
                                        {creditFormatter(
                                            app.requestedResources.reduce(
                                                (prev, curr) => prev + (curr.creditsRequested ?? 0), 0
                                            ), 0)}
                                    </Flex>
                                </Flex>
                            }
                        >
                            Resource request in DKK
                        </Tooltip>
                    )}
                    leftSub={
                        <>
                            <ListRowStat icon={"calendar"}>
                                {dateToString(app.createdAt)}
                            </ListRowStat>
                            <ListRowStat icon={"edit"}>
                                {dateToString(app.updatedAt)}
                            </ListRowStat>
                            <ListRowStat
                                icon={app.grantRecipient.type === "personal" ? "user" : "projects"}
                            >
                                {
                                    app.grantRecipient.type === "personal" ? "Personal" :
                                        app.grantRecipient.type === "new_project" ? "New project" :
                                            "Existing project"
                                }
                            </ListRowStat>
                        </>
                    }
                />;
            })}
        </List>
    );
};
