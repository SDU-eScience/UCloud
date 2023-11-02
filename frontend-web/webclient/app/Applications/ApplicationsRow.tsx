import {injectStyle} from "@/Unstyled";
import {ApplicationGroup} from "./api";
import {AppCard, ApplicationCardType} from "./Card";
import React, {useEffect, useState} from "react";
import {Absolute, Icon, Link, Relative} from "@/ui-components";
import * as Pages from "./Pages";

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

interface ApplicationRowProps {
    items: ApplicationGroup[];
    type: ApplicationCardType;
    refreshId: number;
    scrolling: boolean;
}

const SCROLL_SPEED = 156 * 4;

function groupCardLink(app: ApplicationGroup): string {
    return app.defaultApplication ? 
        Pages.run(app.defaultApplication.name, app.defaultApplication.version)
    :
        Pages.browseGroup(app.id.toString())

}

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

const ApplicationRow: React.FunctionComponent<ApplicationRowProps> = ({
    items, type, scrolling
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
                data-space-between={type === ApplicationCardType.WIDE ? (items.length > 3) : (items.length > 6)}
            >
                {items.map(app =>
                    <Link key={app.id} to={groupCardLink(app)}>
                        <AppCard
                            type={type}
                            title={app.title}
                            description={app.description}
                            logo={app.id.toString()}
                            logoType="GROUP"
                            isFavorite={false}
                        />
                    </Link>
                )}
            </div>
        </>
    )

};

export default ApplicationRow;