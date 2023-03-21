import {defaultSearch, defaultSearchPlaceholder} from "@/DefaultObjects";
import * as React from "react";
import {useNavigate} from "react-router";
import styled from "styled-components";
import * as ui from "@/ui-components";
import Link from "@/ui-components/Link";
import CONF from "../../site.config.json";
import {useGlobal} from "@/Utilities/ReduxHooks";

// Note(Jonas): We need something similar NOT in the header. Or maybe keep this? Nah.
export function NonAuthenticatedHeader(): JSX.Element {
    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Logo />
            <ui.Box ml="auto" />
            <ui.Link to="/login">
                <ui.Button color="green" textColor="headerIconColor" mr="12px">Log in</ui.Button>
            </ui.Link>
        </HeaderContainer>
    );
}

export const Refresh = ({
    onClick,
    spin,
    headerLoading
}: {onClick?: () => void; spin: boolean; headerLoading?: boolean}): JSX.Element => !!onClick || headerLoading ? (
    <RefreshIcon
        data-component="refresh"
        data-loading={spin}
        name="refresh"
        spin={spin || headerLoading}
        onClick={onClick}
    />
) : <ui.Box width="24px" />;

const RefreshIcon = styled(ui.Icon)`
  cursor: pointer;
`;

const HeaderContainer = styled(ui.Flex)`
  height: 48px;
  align-items: center;
  position: fixed;
  top: 0;
  width: 100%;
  z-index: 100;
  box-shadow: ${ui.theme.shadows.sm};
`;

const LogoText = styled(ui.Text)`
  vertical-align: top;
  font-weight: 700;
`;

const Logo = (): JSX.Element => (
    <Link
        data-component={"logo"}
        to="/"
        width={[null, null, null, null, null, "190px"]}
    >
        <ui.Flex alignItems="center" ml="15px">
            <ui.Icon name="logoEsc" size="38px" />
            <ui.Text color="headerText" ml="8px">{CONF.PRODUCT_NAME}</ui.Text>
            {!CONF.VERSION_TEXT ? null : (
                <LogoText
                    ml="4px"
                    mt={-7}
                    color="red"
                    fontSize={17}
                >
                    {CONF.VERSION_TEXT}
                </LogoText>
            )}
        </ui.Flex>
    </Link>
);
