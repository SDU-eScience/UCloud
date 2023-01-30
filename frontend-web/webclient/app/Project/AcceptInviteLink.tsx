import {useCloudAPI} from "@/Authentication/DataHook";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router";
import api, {AcceptInviteLinkResponse} from "./Api";
import {dispatchSetProjectAction} from "./Redux";

export const AcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [acceptedInvite, acceptInvite] = useCloudAPI<AcceptInviteLinkResponse|null>(
        {noop: true},
        null
    );
    
    useEffect(() => {
        if (token) {
            acceptInvite(api.acceptInviteLink({token}));
        }
    }, [token]);

    useEffect(() => {
        if (acceptedInvite) {
            dispatchSetProjectAction(dispatch, acceptedInvite.data?.project);
            navigate("/projects");
        }
    }, [acceptedInvite]);

    return <></>;
}


export default AcceptInviteLink;
