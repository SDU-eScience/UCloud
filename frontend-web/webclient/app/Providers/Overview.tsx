import {AbsoluteNoPointerEvents} from "@/Applications/Card";
import MainContainer from "@/MainContainer/MainContainer";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {Box, Link, theme} from "@/ui-components";
import {GridCardGroup} from "@/ui-components/Grid";
import * as React from "react";
import styled from "styled-components";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import Providers from "@/Assets/provider_info.json";
import * as Heading from "@/ui-components/Heading";
import {Feature, hasFeature} from "@/Features";

interface ProviderType {
    id: string;
    title: string;
    logo: string;
    shortDescription: string;
    description: string;
}

export function ProviderEntry(props: {provider: ProviderType}): React.ReactElement {
    return (
        <Link to={`/providers/detailed/${props.provider.id}`}>
            <ProviderCard>
                <AbsoluteNoPointerEvents left={0} top={0}
                    cursor="inherit"
                    height="10px"
                    width="100%"
                    background={theme.colors.headerBg} />

                <div className={"provider-logo"}>
                    <ProviderLogo providerId={props.provider.id} size={150} />
                </div>

                <Heading.h3>
                    <ProviderTitle providerId={props.provider.id} />
                </Heading.h3>

                <div className={"provider-content"}>
                    {props.provider.shortDescription}
                </div>
            </ProviderCard>
        </Link>
    );
}

export default function ProviderOverview() {
    if (!hasFeature(Feature.PROVIDER_CONNECTION)) return null;

    useTitle("Provider Overview");

    const main = <GridCardGroup minmax={250}>
        {Providers.providers.map(provider =>
            <ProviderEntry key={provider.title} provider={provider} />
        )}
    </GridCardGroup>

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main} />);
}

const ProviderCard = styled.div`
  padding: 10px 10px 10px 10px;
  width: 250px;
  height: 412px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border-radius: ${theme.radius};
  position: relative;
  overflow: hidden;
  box-shadow: ${theme.shadows.sm};
  background-color: var(--appCard, #f00);
  cursor: pointer;
  color: var(--black);

  transition: transform ${theme.timingFunctions.easeIn} ${theme.duration.fastest} ${theme.transitionDelays.xsmall};
  will-change: transform;

  &:hover {
    transition: transform ${theme.timingFunctions.easeOut} ${theme.duration.fastest} ${theme.transitionDelays.xsmall};
      // box-shadow: ${theme.shadows.md};
    box-shadow: 0px 3px  5px -1px rgba(0, 106, 255, 0.2), 0px 6px 10px 0px rgba(0, 106, 255, .14), 0px 1px 18px 0px rgba(0, 106, 255, .12);
    transform: translateY(-2px);
  }

  // Background
  &:before {
    pointer-events: none;
    content: "";
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    z-index: -1;
    background-image: url("data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxkZWZzPjxwYXR0ZXJuIHZpZXdCb3g9IjAgMCBhdXRvIGF1dG8iIHg9IjAiIHk9IjAiIGlkPSJwMSIgd2lkdGg9IjU2IiBwYXR0ZXJuVHJhbnNmb3JtPSJyb3RhdGUoMTUpIHNjYWxlKDAuNSAwLjUpIiBoZWlnaHQ9IjEwMCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTTI4IDY2TDAgNTBMMCAxNkwyOCAwTDU2IDE2TDU2IDUwTDI4IDY2TDI4IDEwMCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iMS41Ij48L3BhdGg+PHBhdGggZD0iTTI4IDBMMjggMzRMMCA1MEwwIDg0TDI4IDEwMEw1NiA4NEw1NiA1MEwyOCAzNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iNCI+PC9wYXRoPjwvcGF0dGVybj48L2RlZnM+PHJlY3QgZmlsbD0idXJsKCNwMSkiIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjwvcmVjdD48L3N2Zz4=");
  }

  &:hover:after {
    opacity: 1;
  }

  .provider-logo {
    margin-left: auto;
    margin-right: auto;
    margin-top: 10px;
    margin-bottom: 10px;
    width: auto;
    max-height: 150px;
    object-fit: scale-down;
  }
  
  .provider-content {
    height: 300px;
    width: 100%;
    margin-top: 4px;
  }
  
  h3 {
    width: 100%;
    text-align: center;
  }
`;