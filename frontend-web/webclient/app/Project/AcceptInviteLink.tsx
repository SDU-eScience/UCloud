import {useCloudAPI} from "@/Authentication/DataHook";
import React, {useEffect} from "react";
import {useNavigate, useParams} from "react-router";
import api, {AcceptInviteLinkResponse} from "./Api";

export const AcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();

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
            // TODO(Brian) Set active project
            navigate("/projects");
        }
    }, [acceptedInvite]);

    return <></>;
}


export default AcceptInviteLink;
