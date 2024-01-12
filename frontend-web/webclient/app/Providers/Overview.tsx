import MainContainer from "@/ui-components/MainContainer";
import {useTitle} from "@/Navigation/Redux";
import {Box, Flex, Link} from "@/ui-components";
import {GridCardGroup} from "@/ui-components/Grid";
import * as React from "react";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import Providers from "@/Assets/provider_info.json";
import {Feature, hasFeature} from "@/Features";
import {classConcat, injectStyle} from "@/Unstyled";
import {ApplicationCardClass} from "@/Applications/Card";
import {CardClass} from "@/ui-components/Card";

interface ProviderType {
    id: string;
    title: string;
    logo: string;
    shortDescription: string;
    description: string;
}

const devProviders = ["k8", "slurm"];

export function ProviderEntry(props: {provider: ProviderType}): React.ReactNode {
    if (devProviders.indexOf(props.provider.id) !== -1) return null;

    return (
        <Link to={`/providers/detailed/${props.provider.id}`}>
            <div className={classConcat(CardClass, ApplicationCardClass, ProviderCard)}>
                <Flex mt="12px">
                    <Flex mx="auto">
                        <ProviderLogo providerId={props.provider.id} size={150} />
                    </Flex>
                </Flex>
                <h3 style={{textAlign: "center", marginTop: "8px", height: "50px"}}>
                    <ProviderTitle providerId={props.provider.id} />
                </h3>

                <div style={{textAlign: "start"}}>
                    {props.provider.shortDescription}
                </div>
            </div>
        </Link>
    );
}

export default function ProviderOverview() {
    if (!hasFeature(Feature.PROVIDER_CONNECTION)) return null;

    useTitle("Provider overview");

    const main = <Box m="12px 24px">
        <GridCardGroup minmax={250}>
            {Providers.providers.map(provider =>
                <ProviderEntry key={provider.title} provider={provider} />
            )}
        </GridCardGroup>
    </Box>

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <div>
            {main}
        </div>
    </>);

    return (<MainContainer main={main} />);
}

const ProviderCard = injectStyle("provider-card", k => `
    ${k} {
        height: 380px;
    }

    ${k}:hover {
        box-shadow: 0px 3px  5px -1px rgba(0, 106, 255, 0.2), 0px 6px 10px 0px rgba(0, 106, 255, .14), 0px 1px 18px 0px rgba(0, 106, 255, .12);
        transform: translateY(-2px);
    }
`);