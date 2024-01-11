import {AppToolLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Box, Flex, Icon, Tooltip} from "@/ui-components";
import Text, {TextSpan} from "@/ui-components/Text";
import * as Pages from "./Pages";
import {useNavigate} from "react-router";
import * as UCloud from "@/UCloud";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

export const AppHeader: React.FunctionComponent<{
    application: UCloud.compute.ApplicationWithFavoriteAndTags;
    allVersions: UCloud.compute.ApplicationSummaryWithFavorite[];
    flavors: UCloud.compute.ApplicationSummaryWithFavorite[];
    title: string;
}> = props => {
    /* Results of `findByName` are ordered by apps `createdAt` field in descending order, so this should be correct. */
    const newest: UCloud.compute.ApplicationSummaryWithFavorite | undefined = props.allVersions[0];
    const navigate = useNavigate();
    const close = React.useRef(() => void 0);

    return (
        <Flex flexDirection={"row"}>
            <Box mr={16} mt="auto">
                <AppToolLogo type={"APPLICATION"} name={props.application.metadata.name} size={"64px"} />
            </Box>
            {/* minWidth=0 is required for the ellipsed text children to work */}
            <Flex flexDirection={"column"} minWidth={0}>
                <Box>
                    <Flex>
                        <Text verticalAlign="center" alignItems="center" fontSize={30} mr="15px">
                            {props.title}
                        </Text>
                        <Flex style={{alignSelf: "center"}}>
                            {props.flavors.length <= 1 ? null :
                                <ClickableDropdown
                                    closeFnRef={close}
                                    paddingControlledByContent
                                    noYPadding
                                    trigger={
                                        <Flex className={FlavorSelectorClass}>
                                            {props.application.metadata.flavorName ?? props.application.metadata.title}
                                            {" "}
                                            <Icon ml="8px" name="chevronDownLight" size={12} />
                                        </Flex>
                                    }>
                                    {props.flavors.map(f =>
                                        <Box
                                            cursor="pointer"
                                            key={f.metadata.name}
                                            minWidth={"300px"}
                                            p={"8px"}
                                            onClick={() => {
                                                close.current();
                                                navigate(Pages.runApplicationWithName(f.metadata.name));
                                            }}
                                        >
                                            {f.metadata.flavorName ?? f.metadata.title}
                                        </Box>
                                    )}
                                </ClickableDropdown>
                            }
                        </Flex>
                    </Flex>
                </Box>
                <Flex>
                    <FavoriteToggle application={props.application} />
                    <ClickableDropdown
                        trigger={<TextSpan ml="8px">{props.application.metadata.version}</TextSpan>}
                        chevron
                        paddingControlledByContent
                        noYPadding
                    >
                        {props.allVersions.map(it =>
                            <Box
                                minWidth={"300px"}
                                p={"8px"}
                                key={it.metadata.version}
                                onClick={() => navigate(Pages.runApplication(it.metadata))}>
                                {it.metadata.version}
                            </Box>
                        )}
                    </ClickableDropdown>
                    {newest && newest.metadata.version !== props.application.metadata.version ?
                        <Tooltip tooltipContentWidth={390} trigger={
                            <div className={TriggerDiv} onClick={e => {
                                e.preventDefault();
                                navigate(Pages.runApplication(newest.metadata));
                            }}>
                                New version available.
                            </div>
                        }>
                            <div onClick={e => e.stopPropagation()}>
                                You are not using the newest version of the app.<br />
                                Click to use the newest version.
                            </div>
                        </Tooltip> : null}
                </Flex>
            </Flex>
        </Flex>
    );
};

const TriggerDiv = injectStyleSimple("trigger-div", `
    margin-left: 4px;
    padding-left: 12px;
    padding-right: 12px;
    text-align: center;
    color: var(--fixedWhite);
    background-color: var(--primary);
    border-radius: 20px;
    cursor: pointer;
`);

const FlavorSelectorClass = injectStyle("flavor-selector", k => `
    ${k} {
        height: 35px;
        border-radius: 8px;
        padding: 0px 15px;
        font-size: var(--buttonText);
        align-items: center;
        background-color: var(--primary);
        color: white;
        margin: auto 0px;
    }

    ${k}:hover {
        filter: brightness(115%);
    }
`);
