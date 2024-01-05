import {useTitle} from "@/Navigation/Redux";
import {Feature, hasFeature} from "@/Features";
import * as React from "react";
import Providers from "@/Assets/provider_info.json";
import {useParams} from "react-router";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Box, Button, ExternalLink, Flex, Markdown, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import MainContainer from "@/ui-components/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "./ProviderLogo";
import {ProviderTitle} from "./ProviderTitle";
import TitledCard from "@/ui-components/HighlightedCard";
import {MachineView} from "@/Products/Products";

export default function DetailedProvider() {
    if (!hasFeature(Feature.PROVIDER_CONNECTION)) return null;

    const params = useParams<{id: string}>();
    const entry = React.useMemo(() => Providers.providers.find(it => it.id === params.id), [params.id]);

    useTitle(entry?.title ?? params.id!);

    if (!entry) return null;

    const main = <Box px="12px" maxWidth={"2200px"}>
        <Flex>
            <div>
                <ProviderLogo providerId={entry.id} size={200} />
            </div>
            <Flex flexDirection="column" ml="16px">
                <Heading.h3><ProviderTitle providerId={entry.id} /></Heading.h3>
                <Text>
                    {entry.description}
                </Text>
                <Box flexGrow={1} />
                {entry.url ? <ExternalLink href={entry.url}><Button>Provider website</Button></ExternalLink> : null}
            </Flex>
        </Flex>
        <Box height="48px" />
        {entry.texts.map((text, index) =>
            <Box key={index} my="32px">
                <TitledCard>
                    <Flex>
                        {text.image !== "" ? <Flex flexDirection="column" mr="24px" my="8px">
                            <Box flexGrow={1} />
                            <img style={{height: "150px", objectFit: "scale-down"}} src={text.image} />
                            <Box flexGrow={1} />
                        </Flex> : <Box />}
                        <div>
                            <Markdown>
                                {text.description}
                            </Markdown>
                        </div>
                    </Flex>
                </TitledCard>
            </Box>
        )}
        <MachineView color="var(--purple)" provider={entry.id} key={entry.id + "STORAGE"} area="STORAGE" />
        <Box my="32px" />
        <MachineView color="var(--purple)" provider={entry.id} key={entry.id + "COMPUTE"} area="COMPUTE" />
    </Box>;

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main} />);
}