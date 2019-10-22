import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {setActivePage, setLoading} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {useCallback, useEffect} from "react";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page} from "Types";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import Divider from "ui-components/Divider";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {invalidateAllSessions, listUserSessions, UserSession} from "UserSettings/api";
import {dateToString} from "Utilities/DateUtilities";
import {addStandardDialog} from "UtilityComponents";

export interface SessionsProps {
    setLoading: (loading: boolean) => void;
    setRefresh: (fn?: () => void) => void;
}

export const Sessions: React.FunctionComponent<SessionsProps> = props => {
    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [sessions, setSessionParameters, sessionParameters] = useCloudAPI<Page<UserSession>>(
        listUserSessions({itemsPerPage: 50, page: 0}),
        emptyPage
    );

    useEffect(() => {
        props.setLoading(sessions.loading || commandLoading);
    }, [props.setLoading, sessions.loading, commandLoading]);

    const onPageChanged = useCallback((page: number) => {
        setSessionParameters(listUserSessions({...sessionParameters.parameters, page}));
    }, [sessionParameters]);

    const pageRenderer = useCallback((page: Page<UserSession>) => {
        return page.items.map((session, idx) => (
            <Box key={idx}>
                <p>
                    <b>{session.userAgent}</b> from <b>{session.ipAddress}</b>
                    <br />
                    <b>Session created at:</b> {dateToString(session.createdAt)}
                </p>

                <Divider />
            </Box>
        ));
    }, []);

    const onInvalidateSessions = useCallback(() => {
        addStandardDialog({
            title: "Invalidate all sessions",
            message: "This will log you out of SDUCloud on ALL devices. Are you sure you wish to do this?",
            onConfirm: async () => {
                await invokeCommand(invalidateAllSessions());
            },
            onCancel: () => {
                // Empty
            }
        });
    }, []);

    useEffect(() => {
        props.setRefresh(() => {
            setSessionParameters(listUserSessions({...sessionParameters.parameters}));
        });

        return () => {
            props.setRefresh();
        };
    }, [props.setRefresh]);

    return (
        <Box>
            <Heading.h2>Active Sessions</Heading.h2>

            <Pagination.List
                loading={sessions.loading}
                page={sessions.data}
                onPageChanged={onPageChanged}
                pageRenderer={pageRenderer}
            />

            <Button color={"red"} onClick={onInvalidateSessions} disabled={commandLoading}>
                Invalidate all sessions
            </Button>
        </Box>
    );
};
