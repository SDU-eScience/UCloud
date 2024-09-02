import {SafeLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Icon, Relative} from "@/ui-components";
import Link from "@/ui-components/Link";
import Markdown from "@/ui-components/Markdown";
import {ThemeColor} from "@/ui-components/theme";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {CardClass} from "@/ui-components/Card";
import {ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";

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

export const Tag = ({label, bg = "infoMain"}: { label: string; bg?: ThemeColor }): React.ReactNode => (
    <div style={{
        marginRight: "3px",
        background: `var(--${bg})`,
        display: "inline-block",
        lineHeight: 1.5,
        color: "var(--infoContrast)",
        textTransform: "uppercase",
        fontSize: "10px",
        fontWeight: 700,
        borderRadius: "6px",
        padding: "2.5px 6px"
    }}>
        {label}
    </div>
);

const MultiLineTruncateClass = injectStyleSimple("multiline-truncate", `
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-align: left;
`);


const TitleAndDescriptionClass = injectStyleSimple("title-and-description", "");

const ApplicationCardClass = injectStyle("application-card", k => `
    ${k} {
        display: flex;
        width: 100%;
        height: 150px;
        min-width: 430px;
        gap: 16px;
        user-select: none;
        transition: border-color 0.2s;
    }
    
    ${k}:hover {
        border-color: var(--primaryDark);
    }

    ${k} > div.image {
        width: 100px;
        height: 100px;
        
        min-width: 75px;
        min-height: 75px;
        margin-top: auto;
        margin-bottom: auto;
        text-align: center;
    }

    ${k} .${TitleAndDescriptionClass} {
        text-align: left;
        color: var(--textSecondary);
        width: calc(100% - 120px);
    }
    
    ${k} .${TitleAndDescriptionClass} h2 {
        font-size: 1.2rem;
        color: var(--textPrimary);
    }

    ${k} > div > span {
        text-align: left;
        font-size: var(--textSecondary);
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
        padding-top: 8px;
        text-overflow: ellipsis;
        overflow: hidden;
    }
`);

function MultiLineTruncate(props: React.PropsWithChildren<{ lines: number }>): React.ReactNode {
    const {lines, ...p} = props;
    return <div className={MultiLineTruncateClass} style={{
        WebkitLineClamp: props.lines,
    }} {...p} />;
}

export enum AppCardType {
    GROUP,
    APPLICATION
}

export interface AppCardProps extends React.PropsWithChildren<ApplicationCardProps> {
}

export function AppCard(props: AppCardProps): React.ReactNode {
    return React.useMemo(() => {
        const card = <div className={classConcat(CardClass, ApplicationCardClass)}>
            <SafeLogo
                size={"85px"}
                name={props.logo}
                type={props.type === AppCardType.APPLICATION ?
                    "APPLICATION" : "GROUP"
                }
            />

            <div title={props.title} className={TitleAndDescriptionClass}>
                <h2>{props.title}</h2>
                <MultiLineTruncate lines={3}>
                    <Markdown
                        disallowedElements={['br', 'a', 'p', 'strong', 'b', 'i']}
                        unwrapDisallowed={true}
                    >
                        {props.description ?? ""}
                    </Markdown>
                </MultiLineTruncate>
            </div>

        </div>;

        return props.link ?
            props.type === AppCardType.APPLICATION ?
                <Link to={props.link}>
                    {card}
                    <Relative top={"calc(-100% + 20px)"} left="calc(100% - 41px)" width="0px" height="0px">
                        <Icon
                            cursor="pointer"
                            name={props.isFavorite ? "starFilled" : "starEmpty"}
                            color="primaryMain"
                            hoverColor="primaryLight"
                            size="20px"
                            onClick={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                return props.onFavorite && props.application ?
                                    props.onFavorite(props.application) : {};
                            }}
                        />
                    </Relative>
                </Link>
                :
                <Link to={props.link}>
                    {card}
                </Link>
            : <>{card}</>
    }, [props]);
}
