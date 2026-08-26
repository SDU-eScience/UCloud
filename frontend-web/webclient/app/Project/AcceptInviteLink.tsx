
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router-dom";
import api, {AcceptInviteLinkResponse, RetrieveInviteLinkInfoResponse} from "./Api";
import * as Heading from "@/ui-components/Heading";
import {dispatchSetProjectAction} from "./ReduxState";
import {Box, Button, Flex, Text} from "@/ui-components";
import MainContainer from "@/ui-components/MainContainer";
import Spinner from "@/LoadingIcon/LoadingIcon";
import AppRoutes from "@/Routes";
import {addOrgInfoModalIfNotFilled} from "@/UserSettings/ChangeUserDetails";
import {prettierString} from "@/UtilityFunctions";
import {formatDate} from "date-fns";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {injectStyle} from "@/Unstyled";

export const AcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [linkInfo, fetchLinkInfo] = useCloudAPI<RetrieveInviteLinkInfoResponse | null>({noop: true}, null);

    useEffect(() => {
        if (token) {
            fetchLinkInfo(api.retrieveInviteLinkInfo({token}));
        }
    }, [token]);

    useEffect(() => {
        if (linkInfo.data) {
            if (linkInfo.data.isMember) {
                dispatchSetProjectAction(dispatch, linkInfo.data.project.id);
                navigate(AppRoutes.project.members());
            } else {
                navigate(AppRoutes.dashboard.dashboardA());
                window.setTimeout(() => {
                    dialogStore.addDialog(<>
                        {linkInfo.loading ? <Spinner /> :
                            linkInfo.error ? <div className={Container}>
                                <Heading.h3>Invitation link has expired</Heading.h3>
                                Contact the relevant PI or admin of the project to get a new link.
                            </div> : <div className={Container}>
                                <Box><Heading.h3>You have been invited to join '{linkInfo.data?.project.specification.title}'</Heading.h3></Box>
                                <Box>
                                    {linkInfo.data?.roleAssignment != null ? <Box>
                                        By accepting this invite, you will join the project as '{prettierString(linkInfo.data.roleAssignment)}'
                                    </Box> : null}
                                    {linkInfo.data?.expires != null ? <Box>
                                        <Text color="textSecondary">This invitation will expire on {formatDate(linkInfo.data.expires, "dd/MM/yyyy")}</Text>
                                    </Box> : null}
                                </Box>

                                <Box flexGrow={1} />

                                <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
                                    <Button
                                        color="successMain"
                                        onClick={async () => {
                                            if (token) {
                                                const acceptedInvite = await callAPI<AcceptInviteLinkResponse | null>(
                                                    api.acceptInviteLink({token})
                                                );
                                                if (!acceptedInvite) return;
                                                dispatchSetProjectAction(dispatch, acceptedInvite.project);
                                                navigate(AppRoutes.project.members());
                                                addOrgInfoModalIfNotFilled();
                                            }
                                        }}
                                    >Join project</Button>
                                    <Button color="errorMain" onClick={() => dialogStore.failure()}>Ignore</Button>
                                </Flex>
                            </div>}
                    </>, () => void 0, undefined, slimModalStyle);
                }, 100);
            }
        }
    }, [linkInfo]);

    return null;
}

const Container = injectStyle("container", k => `
    ${k} {
        display: flex;
        gap: 24px;
        flex-direction: column;
    }
`);

export default AcceptInviteLink;
