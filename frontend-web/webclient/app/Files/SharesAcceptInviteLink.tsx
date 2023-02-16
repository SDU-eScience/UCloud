import {useCloudAPI} from "@/Authentication/DataHook";
import {Share, shareLinksApi} from "@/UCloud/SharesApi";
import {buildQueryString} from "@/Utilities/URIUtilities";
import React, {useEffect} from "react";
import {useNavigate, useParams} from "react-router";

export const SharesAcceptInviteLink: React.FunctionComponent = () => {
    const navigate = useNavigate();

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
        if (!acceptedInvite.data && !acceptedInvite.error) return;
        if (acceptedInvite.loading) return;

        const sharePath = acceptedInvite.data?.status.shareAvailableAt;

        if (sharePath) {
            navigate(buildQueryString("/files", {"path": sharePath}));
        } else {
            navigate("/shares");
        }
    }, [acceptedInvite]);

    return <></>;
}


export default SharesAcceptInviteLink;