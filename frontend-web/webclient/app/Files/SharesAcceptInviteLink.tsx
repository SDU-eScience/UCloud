import {useCloudAPI} from "@/Authentication/DataHook";
import {AcceptInviteLinkResponse, shareLinksApi} from "@/UCloud/SharesApi";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router";

export const SharesAcceptInviteLink: React.FunctionComponent = () => {
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
            acceptInvite(shareLinksApi.acceptInvite({token}));
        }
    }, [token]);

    useEffect(() => {
        if (acceptedInvite) {
            navigate("/shares");
        }
    }, [acceptedInvite]);

    return <></>;
}


export default SharesAcceptInviteLink;