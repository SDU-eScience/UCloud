import {useCloudAPI} from "@/Authentication/DataHook";
import {Share, shareLinksApi} from "@/UCloud/SharesApi";
import {buildQueryString} from "@/Utilities/URIUtilities";
import React, {useEffect} from "react";
import {useDispatch} from "react-redux";
import {useNavigate, useParams} from "react-router";

export const SharesAcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [acceptedInvite, acceptInvite] = useCloudAPI<Share|null>(
        {noop: true},
        null
    );

    useEffect(() => {
        if (token) {
            acceptInvite(shareLinksApi.acceptInvite({token}));
        }
    }, [token]);

    useEffect(() => {
        if (!acceptedInvite.data) return;
        if (acceptedInvite.loading) return;

        const sharePath = acceptedInvite.data?.status.shareAvailableAt;

        if (sharePath) {
            navigate(buildQueryString("/files", {"path": sharePath}));
        } else {
            navigate("/drives");
        }
    }, [acceptedInvite.data]);

    return <></>;
}


export default SharesAcceptInviteLink;