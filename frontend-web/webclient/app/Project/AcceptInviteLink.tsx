import {useCloudAPI} from "@/Authentication/DataHook";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router";
import styled from "styled-components";
import api, {AcceptInviteLinkResponse, Project, RetrieveInviteLinkInfoResponse} from "./Api";
import * as Heading from "@/ui-components/Heading";
import {dispatchSetProjectAction} from "./Redux";
import {Box, Button} from "@/ui-components";
import MainContainer from "@/MainContainer/MainContainer";
import Spinner from "@/LoadingIcon/LoadingIcon";

export const AcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [acceptedInvite, acceptInvite] = useCloudAPI<AcceptInviteLinkResponse|null>({noop: true}, null);
    const [linkInfo, fetchLinkInfo] = useCloudAPI<RetrieveInviteLinkInfoResponse|null>({noop: true}, null);

    useEffect(() => {
        if (token) {
            fetchLinkInfo(api.retrieveInviteLinkInfo({token}));
        }
    }, [token]);

    useEffect(() => {
        if (linkInfo.data) {
            if (linkInfo.data.isMember) {
                dispatchSetProjectAction(dispatch, linkInfo.data.project.id);
                navigate("/projects");
            }
        }
    }, [linkInfo]);
    
    useEffect(() => {
        if (acceptedInvite.data) {
            dispatchSetProjectAction(dispatch, acceptedInvite.data?.project);
            navigate("/projects");
        }
    }, [acceptedInvite]);

    return <MainContainer
        main={
            linkInfo.loading ? <Spinner /> :
            <AcceptProjectLinkContainer>
                <Heading.h3>You have been invited to join {linkInfo.data?.project.specification.title}</Heading.h3>
                <Box mt="15px">
                    <Button
                        color="green"
                        mr="10px"
                        onClick={() => {
                            if (token) {
                                acceptInvite(api.acceptInviteLink({token}))
                            }
                        }}
                    >Join project</Button>
                    <Button color="red" onClick={() => navigate("/")}>Ignore</Button>
                </Box>
            </AcceptProjectLinkContainer>
        }
    />;
}

const AcceptProjectLinkContainer = styled.div`
    text-align: center;
    margin-top: 50px;
`;


export default AcceptInviteLink;
