import {injectStyle} from "@/Unstyled";
import {ApplicationGroup} from "./api";
import {AppCard, ApplicationCardType} from "./Card";
import React, {useEffect, useState} from "react";
import {Absolute, Flex, Grid, Icon, Link, Relative} from "@/ui-components";
import * as Pages from "./Pages";

const ApplicationRowContainerClass = injectStyle("tag-grid-bottom-box", k => `
    ${k} {
        padding: 15px 10px 15px 10px;
        margin: 0 -10px;
        overflow-x: auto;
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
        {left ? (
            <Icon name="backward" size="14" />
        ) : (
            <Icon name="forward" size="14" />
        )}
    </div>
}

const ApplicationRow: React.FunctionComponent<ApplicationRowProps> = ({
    items, type, scrolling
}: ApplicationRowProps) => {
    const filteredItems = React.useMemo(() =>
        items,
        [items]
    );

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
                                scrollRef.current.scrollBy({left: -SCROLL_SPEED});
                            }
                        }} />
                    </Absolute>
                </Relative>
                <Relative>
                    <Absolute height={0} width={0} right="0" top="110px">
                        <ScrollButton disabled={false} left={false} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: SCROLL_SPEED});
                        }} />
                    </Absolute>
                </Relative>
            </>}

            {type === ApplicationCardType.WIDE ?
                <div ref={scrollRef} className={ApplicationRowContainerClass}>
                    <Flex
                        justifyContent={filteredItems.length < 3 ? "space-evenly" : "space-between"}
                        gap="10px"
                        py="10px"
                    >
                        {filteredItems.map(app =>
                            <Link key={app.id} to={groupCardLink(app)}>
                                <AppCard
                                    type={ApplicationCardType.WIDE}
                                    title={app.title}
                                    description={app.description}
                                    logo={app.id.toString()}
                                    logoType="GROUP"
                                    isFavorite={false}
                                />
                            </Link>
                        )}
                    </Flex>
                </div>
            :
                scrolling ?
                    <div ref={scrollRef} className={ApplicationRowContainerClass}>
                        <Grid
                            gridGap="25px"
                            gridTemplateRows={"repeat(1, 1fr)"}
                            gridTemplateColumns={"repeat(auto-fill, 166px)"}
                            style={{gridAutoFlow: "column"}}
                        >
                            {filteredItems.map(app =>
                                <>
                                    <Link key={app.id} to={groupCardLink(app)}>
                                        <AppCard
                                            type={ApplicationCardType.EXTRA_TALL}
                                            title={app.title}
                                            description={app.description}
                                            logo={app.id.toString()}
                                            logoType="GROUP"
                                            isFavorite={false}
                                        />
                                    </Link>
                                </>
                            )}
                        </Grid>
                    </div>
                :
                    <div ref={scrollRef} className={ApplicationRowContainerClass}>
                        <Flex
                            justifyContent="space-evenly"
                            gap="10px"
                            py="10px"
                        >
                            {filteredItems.map(app =>
                                <Link key={app.id} to={groupCardLink(app)}>
                                    <AppCard
                                        type={ApplicationCardType.EXTRA_TALL}
                                        title={app.title}
                                        description={app.description}
                                        logo={app.id.toString()}
                                        logoType="GROUP"
                                        isFavorite={false}
                                    />
                                </Link>
                            )}
                        </Flex>
                    </div>
            }
        </>
    )

};

export default ApplicationRow;