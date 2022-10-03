import {useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import Providers from "@/assets/Providers/info.json";
import {useParams} from "react-router";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Box, Button, Card, Flex, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import MainContainer from "@/MainContainer/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "./ProviderLogo";
import {ProviderTitle} from "./ProviderTitle";

export default function DetailedProvider() {
    const params = useParams<{id: string}>();
    useTitle(params.id ?? "None");

    const entry = React.useMemo(() => {
        return Providers.providers.find(it => it.id === params.id) ?? {
            description: "Not found",
            id: "Not found",
            logo: "",
            skus: [],
            texts: [],
            title: "Not found"
        }
    }, [params.id]);

    const main = <div>
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
        {entry.texts.map(text => 
            <Card key={text.description} my="8px">
                <Flex>
                    {text.image !== "" ? <div>
                        <img style={{width: "150px", objectFit: "scale-down"}} src={text.image} />
                    </div> : <Box />}
                    <div>
                        {text.description}
                    </div>
                </Flex>
            </Card>
        )}
        {entry.skus.length === 0 ? null : (
            entry.skus.map(it => <div key={""}>
                {it}
            </div>)
        )}
    </div>;

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main} />);
}