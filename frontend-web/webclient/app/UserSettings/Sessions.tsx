import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import HttpClient from "Authentication/lib";
import {emptyPage} from "DefaultObjects";
import * as Pagination from "Pagination";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {UAParser} from "ua-parser-js";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import Divider from "ui-components/Divider";
import * as Heading from "ui-components/Heading";
import {invalidateAllSessions, listUserSessions, UserSession} from "UserSettings/api";
import {dateToString} from "Utilities/DateUtilities";
import {addStandardDialog} from "UtilityComponents";
import {PRODUCT_NAME} from "../../site.config.json";

export interface SessionsProps {
    setLoading: (loading: boolean) => void;
    setRefresh: (fn?: () => void) => void;
}

export const Sessions: React.FunctionComponent<SessionsProps> = props => {
    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [sessions, setSessionParameters, sessionParameters] = useCloudAPI<Page<UserSession>>(
        listUserSessions({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    useEffect(() => {
        props.setLoading(sessions.loading || commandLoading);
    }, [props.setLoading, sessions.loading, commandLoading]);

    const onPageChanged = useCallback((page: number) => {
        setSessionParameters(listUserSessions({...sessionParameters.parameters, page}));
    }, [sessionParameters]);

    const pageRenderer = useCallback((page: Page<UserSession>) => {
        return page.items.map((session, idx) => {
            const parsed = new UAParser(session.userAgent);
            let deviceText = "";
            if (!!parsed.getDevice().vendor) {
                deviceText += parsed.getDevice().vendor;
                deviceText += " ";
            }

            if (!!parsed.getDevice().model) {
                deviceText += parsed.getDevice().model;
                deviceText += " ";
            }

            if (!!parsed.getBrowser().name) {
                deviceText += parsed.getBrowser().name;
                deviceText += " ";
            }

            if (!!parsed.getOS().name) {
                deviceText += parsed.getOS().name;
                deviceText += " ";

                if (!!parsed.getOS().version) {
                    deviceText += parsed.getOS().version;
                }
            }

            if (deviceText === "") {
                deviceText = "Unknown device";
            }

            return(
                <Box key={idx}>
                <p>
                    <b>{deviceText}</b> from <b>{session.ipAddress}</b>
                    <br />
                    <b>Session created at:</b> {dateToString(session.createdAt)}
                </p>

                <Divider />
            </Box>
            );
        });
    }, []);

    const onInvalidateSessions = useCallback(() => {
        addStandardDialog({
            title: "Invalidate all sessions",
            message: `This will log you out of ${PRODUCT_NAME} on ALL devices. Are you sure you wish to do this?`,
            onConfirm: async () => {
                await invokeCommand(invalidateAllSessions());
                HttpClient.clearTokens();
                Client.openBrowserLoginPage();
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
