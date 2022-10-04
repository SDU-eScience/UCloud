import {useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import Providers from "@/assets/Providers/info.json";
import {useParams} from "react-router";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Box, Button, Card, Flex, Markdown, Relative, Text, theme} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import MainContainer from "@/MainContainer/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "./ProviderLogo";
import {ProviderTitle} from "./ProviderTitle";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {MachineView} from "@/Products/Products";

export default function DetailedProvider() {
    const params = useParams<{id: string}>();
    const entry = React.useMemo(() => Providers.providers.find(it => it.id === params.id), [params.id]);

    useTitle(entry?.title ?? params.id);

    if (!entry) return null;

    const main = <Box px="12px">
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
                <Button>Provider website</Button>
            </Flex>
        </Flex>
        <Box height="48px" />
        {entry.texts.map((text, index) =>
            <Box key={index} my="16px">
                <HighlightedCard color="purple">
                    <Flex>
                        {text.image !== "" ? <Flex flexDirection="column">
                            <Box flexGrow={1} />
                            <img style={{maxHeight: "150px", objectFit: "scale-down"}} src={text.image} />
                            <Box flexGrow={1} />
                        </Flex> : <Box />}
                        <Box ml="16px">
                            <Markdown>
                                {text.description}
                            </Markdown>
                        </Box>
                    </Flex>
                </HighlightedCard>
            </Box>
        )}
        <MachineView provider={entry.id} key={entry.id + "STORAGE"} area="STORAGE" />
        <MachineView provider={entry.id} key={entry.id + "COMPUTE"} area="COMPUTE" />
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