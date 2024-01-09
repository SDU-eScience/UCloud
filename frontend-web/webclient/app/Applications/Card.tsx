import {AppToolLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Flex, Icon, Relative} from "@/ui-components";
import Link from "@/ui-components/Link";
import Markdown from "@/ui-components/Markdown";
import {ThemeColor} from "@/ui-components/theme";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import { CardClass } from "@/ui-components/Card";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;

interface ApplicationCardProps {
    onFavorite?: (app: ApplicationSummaryWithFavorite) => void;
    title: string;
    description?: string;
    logo: string;
    type: AppCardType;
    isFavorite?: boolean;
    link?: string;
    colorBySpecificTag?: string;
    application?: ApplicationSummaryWithFavorite
}

export const Tag = ({label, bg = "black"}: {label: string; bg?: ThemeColor}): JSX.Element => (
    <div style={{
        marginRight: "3px",
        background: `var(--${bg})`,
        display: "inline-block",
        lineHeight: 1.5,
        color: "white",
        textTransform: "uppercase",
        fontSize: "10px",
        fontWeight: 700,
        borderRadius: "6px",
        padding: "3px"
    }}>
        {label}
    </div>
);



const MultiLineTruncateClass = injectStyleSimple("multiline-truncate", `
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-align: left;
    font-size: 13px;
`);

const TallApplicationCard = injectStyle("tall-application-card", k => `
    ${k} {
        width: 166px;
        height: 225px;
        cursor: pointer;
    }

    ${k} > div.image {
        width: 75px;
        height: 75px;
        min-width: 75px;
        min-height: 75px;
        margin-bottom: 10px;
    }
    
    ${k} > div.image > * {
        width: 52px;
        height: 52px;
    }

    ${k}[data-xl="true"] {
        height: 290px;
    }
`);

const TitleAndDescriptionClass = injectStyleSimple("title-and-description", "");

const WideApplicationCard = injectStyle("wide-application-card", k => `
    ${k} {
        display: flex;
        width: 312px;
        height: 142px;
    }

    ${k} > div.image {
        width: 75px;
        height: 75px;
        margin-top: auto;
        margin-bottom: auto;
        text-align: center;
    }

    ${k} > div.${TitleAndDescriptionClass} {
        text-align: left;
        width: calc(322px - 100px);
        padding-left: 15px;
        margin-top: 5px;
    }

    ${k}[data-xl="true"] {
        width: 432px;
        height: 202px;
    }

    ${k}[data-xl="true"] > div.image {
        width: 100px;
        height: 100px;
    }

    ${k}[data-xl="true"] > div.${TitleAndDescriptionClass} {
        width: calc(432px - 140px);
    }
`);

export const ApplicationCardClass = injectStyle("application-card", k => `
    ${k} {
        user-select: none;
    }

    ${k} > div.${TitleAndDescriptionClass} {
        font-size: var(--buttonText);
    }

    ${k} > div > span {
        text-align: left;
        font-size: var(--secondaryText);
        overflow-y: auto;
        padding-left: 8px;
        padding-right: 8px;
        height: 90px;
    }
    
    ${k} > div.image > * {
        margin-left: auto;
        margin-right: auto;
        margin-top: auto;
        margin-bottom: auto;
    }

    ${k} > div > .${MultiLineTruncateClass} {
        color: var(--text);
        padding-top: 8px;
    }

    ${k} > .${TitleAndDescriptionClass} > div:first-child {
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
    }

    ${k} > div.image {
        background-color: var(--lightGray);
        border-radius: 12px;
        margin-left: auto;
        margin-right: auto;
        display: flex;
    }

    html.dark ${k} > div.image {
        background-color: white;
    }

    ${k} > div {
        text-align: center;
    }
`);

function MultiLineTruncate(props: React.PropsWithChildren<{lines: number}>): JSX.Element {
    const {lines, ...p} = props;
    return <div className={MultiLineTruncateClass} style={{
        WebkitLineClamp: props.lines,
    }} {...p} />;
}

export enum AppCardStyle {
    WIDE,
    EXTRA_TALL,
    TALL,
    EXTRA_WIDE,
}

export enum AppCardType {
    GROUP,
    APPLICATION
}

export interface AppCardProps extends React.PropsWithChildren<ApplicationCardProps> {
    cardStyle: AppCardStyle;
}

const typeLineCount = {
    [AppCardStyle.TALL]: 4,
    [AppCardStyle.EXTRA_TALL]: 4,
    [AppCardStyle.WIDE]: 4,
    [AppCardStyle.EXTRA_WIDE]: 9
}

export function AppCard(props: AppCardProps): JSX.Element {
    return React.useMemo(() => {
        let lineCount = typeLineCount[props.cardStyle];
        let card: React.JSX.Element;
        const titleAndDescription =
            <div title={props.title} className={TitleAndDescriptionClass}>
                <div><b>{props.title}</b></div>
                <MultiLineTruncate lines={lineCount}>
                    <Markdown
                        disallowedElements={['br', 'a', 'p', 'strong', 'b', 'i']}
                        unwrapDisallowed={true}
                    >
                        {props.description ?? ""}
                    </Markdown>
                </MultiLineTruncate>
            </div>;
        switch (props.cardStyle) {
            case AppCardStyle.EXTRA_TALL:
            case AppCardStyle.TALL:
                const isExtraTall = props.cardStyle === AppCardStyle.EXTRA_TALL;
                card = <Flex 
                        flexDirection="column" 
                        className={classConcat(CardClass, ApplicationCardClass, TallApplicationCard)} 
                        data-xl={isExtraTall}
                    >
                        <div className="image">
                            <AppToolLogo
                                size={"52px"}
                                name={props.logo}
                                type={props.type === AppCardType.APPLICATION ?
                                    "APPLICATION" : "GROUP"
                                }
                            />
                        </div>
                        {titleAndDescription}
                    </Flex>;
                break;

            case AppCardStyle.WIDE:
            case AppCardStyle.EXTRA_WIDE:
                const isExtraWide = props.cardStyle === AppCardStyle.EXTRA_WIDE;
                card = <div 
                    className={classConcat(CardClass, ApplicationCardClass, WideApplicationCard)} 
                    data-xl={isExtraWide}
                >
                    <div className="image">
                        <AppToolLogo
                            size={isExtraWide ? "85px" : "65px"}
                            name={props.logo}
                            type={props.type === AppCardType.APPLICATION ?
                                "APPLICATION" : "GROUP"
                            }
                        />
                    </div>
                    {titleAndDescription}
                </div>
        }

        return props.link ? 
            props.type === AppCardType.APPLICATION ?
                <Flex>
                    <Link to={props.link}>
                        {card}
                    </Link>
                    <Relative top="6px" right="28px" width="0px" height="0px">
                        <Icon
                            cursor="pointer"
                            name={props.isFavorite ? "starFilled" : "starEmpty"}
                            color="primary"
                            hoverColor="primary"
                            size="20px"
                            onClick={() => props.onFavorite && props.application ?
                                props.onFavorite(props.application) : {}
                            }
                        />
                    </Relative>
                </Flex>
            :
                <Link to={props.link}>
                    {card}
                </Link>
        : <>{card}</>
    }, [props]);
}
