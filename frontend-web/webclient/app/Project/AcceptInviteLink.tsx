import {useCloudAPI} from "@/Authentication/DataHook";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router";
import api, {AcceptInviteLinkResponse, RetrieveInviteLinkInfoResponse} from "./Api";
import * as Heading from "@/ui-components/Heading";
import {dispatchSetProjectAction} from "./Redux";
import {Button, Flex} from "@/ui-components";
import MainContainer from "@/MainContainer/MainContainer";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {injectStyleSimple} from "@/Unstyled";
import AppRoutes from "@/Routes";

export const AcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [acceptedInvite, acceptInvite] = useCloudAPI<AcceptInviteLinkResponse | null>({noop: true}, null);
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
            }
        }
    }, [linkInfo]);

    useEffect(() => {
        if (acceptedInvite.data) {
            dispatchSetProjectAction(dispatch, acceptedInvite.data?.project);
            navigate(AppRoutes.project.members());
        }
    }, [acceptedInvite]);

    return <MainContainer
        main={
            linkInfo.loading ? <Spinner /> :
                linkInfo.error ? <div className={AcceptProjectLinkContainer}>
                    <Heading.h3>Invitation link has expired</Heading.h3>
                    Contact the relevant PI or admin of the project to get a new link.
                </div> : <div className={AcceptProjectLinkContainer}>
                    <Heading.h3>You have been invited to join {linkInfo.data?.project.specification.title}</Heading.h3>
                    <Flex mt="15px">
                        <Button
                            color="green"
                            mr="10px"
                            onClick={() => {
                                if (token) {
                                    acceptInvite(api.acceptInviteLink({token}))
                                }
                            }}
                        >Join project</Button>
                        <Button color="red" onClick={() => navigate(AppRoutes.dashboard.dashboardA())}>Ignore</Button>
                    </Flex>
                </div>
        }
    />;
}

const AcceptProjectLinkContainer = injectStyleSimple("accept-project-link", `
    text-align: center;
    margin-top: 50px;
`);


export default AcceptInviteLink;
