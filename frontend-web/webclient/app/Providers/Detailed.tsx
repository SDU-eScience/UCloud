import {usePage} from "@/Navigation/Redux";
import * as React from "react";
import {useParams} from "react-router-dom";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Box, Button, ExternalLink, Flex, Markdown, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import MainContainer from "@/ui-components/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "./ProviderLogo";
import {ProviderTitle} from "./ProviderTitle";
import TitledCard from "@/ui-components/HighlightedCard";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useSelector} from "react-redux";
import {ProviderBranding} from "@/UCloud/ProviderBrandingApi";

function useProviderBranding(id?: string): ProviderBranding | undefined {
    const data = useSelector((it: ReduxObject) => it.providerBrandings);
    if (!id) return undefined;
    return data.providers[id];
}

export default function DetailedProvider() {
    const params = useParams<{id: string}>();
    const entry = useProviderBranding(params.id);

    usePage(entry?.title ?? params.id!, SidebarTabId.NONE);

    if (!entry) {
        return null;
    }

    const main = <Box px="12px" maxWidth={"2200px"}>
        <Flex>
            <div>
                <ProviderLogo providerId={entry.id} size={200} />
            </div>
            <Flex flexDirection="column" ml="16px">
                <Heading.h3><ProviderTitle providerId={entry.id} /></Heading.h3>
                <Markdown>
                    {entry.description}
                </Markdown>
                <Box flexGrow={1} />
                {entry.url ? <ExternalLink href={entry.url}><Button>Provider website</Button></ExternalLink> : null}
            </Flex>
        </Flex>
        <Box height="48px" />
        {entry.sections.map((section, index) =>
            <Box key={index} my="32px">
                <TitledCard>
                    <Flex>
                        {section.image !== "" ? <Flex flexDirection="column" mr="24px" my="8px">
                            <Box flexGrow={1} />
                            <img alt={`Provider detail image`}  style={{height: "150px", objectFit: "scale-down"}} src={section.image} />
                            <Box flexGrow={1} />
                        </Flex> : <Box />}
                        <div>
                            <Markdown>
                                {section.description}
                            </Markdown>
                        </div>
                    </Flex>
                </TitledCard>
            </Box>
        )}
        {entry.productDescription.map((prod, index) => 
            <Box key={index} my="32px">
                <TitledCard>
                    <Flex>
                        <h2>{prod.category}</h2>
                        <Box flexGrow={1}/>
                        <div>
                            {prod.shortDescription}
                        </div>
                    </Flex>
                    <Flex>
                    <div>
                        <img alt={`Product Section Image`}  style={{height: "150px", objectFit: "scale-down"}} src={prod.section.image} />
                    </div>
                    <div>
                        <br/>
                        <br/>
                        <Markdown>
                            {prod.section.description}
                        </Markdown>
                    </div>
                    </Flex>
                </TitledCard>
            </Box>
        )}
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