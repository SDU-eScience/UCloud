import * as React from "react";
import * as ui from "@/ui-components";
import Link from "@/ui-components/Link";
import Text from "@/ui-components/Text";
import CONF from "../../site.config.json";
import {injectStyleSimple} from "@/Unstyled";

// Note(Jonas): We need something similar NOT in the header. Or maybe keep this? Nah.
export function NonAuthenticatedHeader(): React.ReactNode {
    return (
        <ui.Flex background={"var(--primaryMain)"} className={HeaderContainerClass}>
            <Logo/>
            <ui.Box ml="auto"/>
            <ui.Link to="/login">
                <ui.Button color="successMain" textColor="fixedWhite" mr="12px">Log in</ui.Button>
            </ui.Link>
        </ui.Flex>
    );
}

const HeaderContainerClass = injectStyleSimple("header-container", `
    height: 48px;
    align-items: center;
    position: fixed;
    top: 0;
    width: 100%;
    z-index: 100;
    box-shadow: var(--defaultShadow);
`);

const Logo = (): React.ReactNode => (
    <Link
        data-component={"logo"}
        to="/"
        color="fixedWhite"
        hoverColor="fixedWhite"
        width={[null, null, null, null, null, "190px"]}
    >
        <ui.Flex alignItems="center" ml="15px">
            <ui.Icon name="logoEsc" size="38px" />
            <ui.Text color="var(--textPrimary)" ml="8px">{CONF.PRODUCT_NAME}</ui.Text>
            {!CONF.VERSION_TEXT ? null : (
                <Text
                    ml="4px"
                    verticalAlign="top"
                    mt={-7}
                    color="errorMain"
                    fontSize={17}
                >
                    {CONF.VERSION_TEXT}
                </Text>
            )}
        </ui.Flex>
    </Link>
);
