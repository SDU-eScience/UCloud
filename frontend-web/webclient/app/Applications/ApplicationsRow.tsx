import {injectStyle} from "@/Unstyled";
import {ApplicationGroup} from "./api";
import {AppCard, AppCardType, AppCardStyle} from "./Card";
import React, {useEffect, useState} from "react";
import {Absolute, Icon, Link, Relative} from "@/ui-components";
import * as Pages from "./Pages";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;

export const ApplicationRowContainerClass = injectStyle("application-row-container", k => `
    ${k} {
        display: flex;
        justify-content: left;
        gap: 20px;
        padding: 12px 10px;
        margin: 0 -10px;
        overflow-x: auto;
    }

    ${k}[data-space-between=true] {
        justify-content: space-between;
    }
`);

export interface ApplicationRowItem {
    id: string,
    type: AppCardType,
    title: string,
    description?: string,
    defaultApplication?: string,
    tags?: string[],
    isFavorite?: boolean,
    application?: ApplicationSummaryWithFavorite
}

export function ApplicationGroupToRowItem(group: ApplicationGroup): ApplicationRowItem {
    return {
        id: group.id.toString(),
        type: AppCardType.GROUP,
        title: group.title,
        description: group.description,
        defaultApplication: group.defaultApplication,
        tags: group.tags
    }
}

export function ApplicationSummaryToRowItem(app: ApplicationSummaryWithFavorite): ApplicationRowItem {
    return {
        id: app.metadata.name,
        type: AppCardType.APPLICATION,
        title: app.metadata.title,
        description: app.metadata.description,
        isFavorite: app.favorite,
        application: app
    }
}

interface ApplicationRowProps {
    items: ApplicationRowItem[];
    onFavorite: (app: ApplicationSummaryWithFavorite) => void;
    cardStyle: AppCardStyle;
    refreshId: number;
}

const SCROLL_SPEED = 156 * 4;



const ScrollButtonClass = injectStyle("scroll-button", k => `
    ${k} {
        background-color: var(--blue);
        color: var(--white);
        width: 32px;
        height: 32px;
        border-radius: 16px;
        cursor: pointer;
        user-select: none;
        font-weight: 800;
        font-size: 18px;
        padding-left: 10px;
        padding-top: 1px;
    }

    ${k}[data-is-left="true"] {
        padding-left: 8px;
    }

    ${k}:hover {
        filter: brightness(115%);
    }
`);

function ScrollButton({disabled, left, onClick}: {disabled: boolean; left: boolean; onClick(): void}): JSX.Element {
    return <div onClick={onClick} data-is-left={left} className={ScrollButtonClass} data-disabled={disabled}>
     <Icon name={left ? "backward" : "forward"} size="14" />
    </div>
}

function linkFromApplicationRowItem(item: ApplicationRowItem): string {
    if (item.type === AppCardType.APPLICATION) {
        return Pages.run(item.id)
    }
    if (item.defaultApplication) {
        return Pages.run(item.defaultApplication)
    }
    return Pages.browseGroup(item.id)
}

const ApplicationRow: React.FunctionComponent<ApplicationRowProps> = ({
    items, onFavorite, cardStyle 
}: ApplicationRowProps) => {
    const [hasScroll, setHasScroll] = useState<boolean>(false);
    const scrollRef = React.useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!scrollRef.current) return;
        setHasScroll(scrollRef.current.scrollWidth > scrollRef.current.clientWidth);
    }, [items]);

    return (
        <>
            {!hasScroll ? null : <>
                <Relative>
                    <Absolute height={0} width={0} top="110px">
                        <ScrollButton disabled={false} left onClick={() => {
                            if (scrollRef.current) {
                                scrollRef.current.scrollBy({left: -SCROLL_SPEED, behavior: "smooth"});
                            }
                        }} />
                    </Absolute>
                </Relative>
                <Relative>
                    <Absolute height={0} width={0} right="0" top="110px">
                        <ScrollButton disabled={false} left={false} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: SCROLL_SPEED, behavior: "smooth"});
                        }} />
                    </Absolute>
                </Relative>
            </>}

            <div
                ref={scrollRef}
                className={ApplicationRowContainerClass}
                data-space-between={cardStyle === AppCardStyle.WIDE ? (items.length > 3) : (items.length > 6)}
            >
                {items.map(item =>
                    <AppCard
                        key={item.id}
                        cardStyle={cardStyle}
                        title={item.title}
                        description={item.description}
                        logo={item.id}
                        type={item.type}
                        isFavorite={item.isFavorite}
                        onFavorite={onFavorite}
                        application={item.application}
                        link={linkFromApplicationRowItem(item)}
                    />
                )}
            </div>
        </>
    )

};

export default ApplicationRow;