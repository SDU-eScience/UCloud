import {useCloudAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import {RetrieveLinkResponse, Share, shareLinksApi} from "@/UCloud/SharesApi";
import {buildQueryString} from "@/Utilities/URIUtilities";
import * as Heading from "@/ui-components/Heading";
import React, {PropsWithChildren, useEffect} from "react";
import {useNavigate, useParams} from "react-router";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {Box, Button} from "@/ui-components";
import {Client} from "@/Authentication/HttpClientInstance";

export const SharesAcceptLink: React.FunctionComponent = () => {
    const navigate = useNavigate();

    const locationParams = useParams<{id: string;}>();
    let token = locationParams.id ? decodeURIComponent(locationParams.id) : undefined;

    const [acceptedShare, acceptShare] = useCloudAPI<Share|null>({noop: true}, null);
    const [linkInfo, fetchLinkInfo] = useCloudAPI<RetrieveLinkResponse|null>({noop: true}, null);

    useEffect(() => {
        if (token) {
            fetchLinkInfo(shareLinksApi.retrieve({token}));
        }
    }, [token]);

    useEffect(() => {
        if (linkInfo.data) {
            if (linkInfo.data.sharedBy == Client.username && linkInfo.data.path) {
                navigate(buildQueryString("/files", {path: linkInfo.data.path}));
            } else if (linkInfo.data.sharePath) {
                navigate(buildQueryString("/files", {path: linkInfo.data.sharePath}));
            }
        }
    }, [linkInfo]);
    
    useEffect(() => {
        if (!acceptedShare.data && !acceptedShare.error) return;
        if (acceptedShare.loading) return;

        const sharePath = acceptedShare.data?.status.shareAvailableAt;

        if (sharePath) {
            navigate(buildQueryString("/files", {path: sharePath}));
        } else {
            navigate("/shares");
        }
    }, [acceptedShare]);

    return <MainContainer
        main={
            linkInfo.loading ? <Spinner /> :
            linkInfo.error ? <AcceptProjectLinkContainer>
                <Heading.h3>Link has expired</Heading.h3>
                Contact the owner of the folder to get a new link.
            </AcceptProjectLinkContainer>
            :
            <AcceptProjectLinkContainer>
                <Heading.h3><strong>{linkInfo.data?.sharedBy}</strong> wants to share folder <strong>{linkInfo.data?.path.split("/").pop()}</strong> with you</Heading.h3>
                <Box mt="15px">
                    <Button
                        color="successMain"
                        mr="10px"
                        onClick={() => {
                            if (token) {
                                acceptShare(shareLinksApi.accept({token}))
                            }
                        }}
                    >See files</Button>
                    <Button color="errorMain" onClick={() => navigate("/")}>Ignore</Button>
                </Box>
            </AcceptProjectLinkContainer>
        }
    />;
}

function AcceptProjectLinkContainer(props: PropsWithChildren): React.ReactNode {
    return <Box  textAlign="center" mt="50px">
        {props.children}
    </Box>
}

export default SharesAcceptLink;