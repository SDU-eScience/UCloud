import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Absolute, Box, Button, Divider, Flex, Icon, Input, Link, Relative, theme} from "@/ui-components";
import Grid from "@/ui-components/Grid";
import * as Heading from "@/ui-components/Heading";
import {Spacer} from "@/ui-components/Spacer";
import {AppCard, ApplicationCardType, FavoriteApp} from "./Card";
import * as Pages from "./Pages";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreSections = compute.AppStoreSections;
import ApplicationGroup = compute.ApplicationGroup;
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {useLocation, useNavigate, useParams} from "react-router";
import AppRoutes from "@/Routes";
import {TextSpan} from "@/ui-components/Text";
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import bgImage from "@/Assets/Images/background_polygons.png";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

export const ShowAllTagItem: React.FunctionComponent<{tag?: string; children: React.ReactNode;}> = props => (
    <Link to={props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

function favoriteStatusKey(metadata: compute.ApplicationMetadata): string {
    return `${metadata.name}/${metadata.version}`;
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
        padding-left: 12px;
        padding-top: 1px;
    }

    ${k}[data-is-left="true"] {
        padding-left: 10px;
    }
`);

function ScrollButton({disabled, text, onClick}: {disabled: boolean; text: string; onClick(): void}): JSX.Element {
    return <div onClick={onClick} data-is-left={text === "⟨"} className={ScrollButtonClass} data-disabled={disabled}>
        {text}
    </div>
}

type FavoriteStatus = Record<string, {override: boolean, app: ApplicationSummaryWithFavorite}>;

const FloatingButtonClass = injectStyle("floating-button", k => `
    ${k} {
        position: fixed;
        bottom: 30px;
        left: calc(50% - 50px);
        width: 200px;
    }

    ${k} button {
        width: 200px;
        text-align: center;
        box-shadow: ${theme.shadows.sm};
    }
`);


function FloatingButton(): JSX.Element {
    const navigate = useNavigate();


    return <div className={FloatingButtonClass}>
        <Button
            onClick={() => navigate(AppRoutes.apps.overview())}
            borderRadius="99px"
        >
            <TextSpan pr="15px">View all</TextSpan>
            <Icon name="chevronDownLight" size="18px" />
        </Button>
    </div>;
}

const LandingDivider = injectStyle("landing-divider", k => `
    ${k} {
        margin-top: 50px;
        margin-bottom: 50px;
    }

    ${k} h1 {
        text-align: center;
        margin-top: 50px;
        color: #5c89f4;
    }

    ${k} img {
        max-height: 250px;
        transform: scaleX(-1);
    }
`);

  const LargeSearchBoxClass = injectStyle("large-search-box", k => `
        ${k} {
            width: 400px;
            margin: 30px auto;
            position: relative;
        }

        ${k} input {
            width: 100%;
            border: 1px solid var(--midGray);
            background: var(--white);
            border-radius: 99px;
            padding-left: 1.2em;
            padding-right: 2.5rem;
            box-shadow: inset 0 5px 5px var(--lightGray);
        }

        ${k} input:hover {
            border-color: var(--gray);
        }

        ${k} input:focus {
            border-color: var(--blue);
            box-shadow: 0 0 3px -1px var(--blue);
        }

        ${k} button {
            border: 1px solid pink;
            background: none;
            border: 0;
            padding: 0 10px 1px 10px;
            cursor: pointer;
            position: absolute;
            right: 0;
            height: 2.5rem;
        }
    `);


function LargeSearchBox(): JSX.Element {
    const navigate = useNavigate();
  
    return <div className={LargeSearchBoxClass}>
        <Flex justifyContent="space-between">
            <Input
                placeholder="Search for applications..."
                onKeyUp={e => {
                    console.log(e);
                    if (e.key === "Enter") {
                        navigate(AppRoutes.apps.search((e as unknown as {target: {value: string}}).target.value));
                    }
                }}
                autoFocus
            />
            <button>
                <Icon name="search" size={20} color="darkGray" my="auto" />
            </button>
        </Flex>
    </div>;
}

const ApplicationsOverview: React.FunctionComponent = () => {
    const location = useLocation();

    const isLanding = !location.pathname.endsWith("full") && !location.pathname.endsWith("full/");

    const [sections, fetchSections] = useCloudAPI<AppStoreSections>(
        {noop: true},
        {sections: []}
    );

    const [refreshId, setRefreshId] = useState<number>(0);

    useEffect(() => {
        fetchSections(UCloud.compute.apps.appStoreSections({page: isLanding ? "LANDING" : "FULL"}));
    }, [refreshId]);

    useResourceSearch(ApiLike);

    const dispatch = useDispatch();

    useTitle("Applications");
    const refresh = useCallback(() => {
        setRefreshId(id => id + 1);
    }, []);
    useRefreshFunction(refresh);

    const [, invokeCommand] = useCloudCommand();
    const favoriteStatus = React.useRef<FavoriteStatus>({});

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        // Note(Jonas): This used to check commandLoading (from invokeCommand), but this gets stuck at true, so removed for now.
        const key = favoriteStatusKey(app.metadata);
        const isFavorite = favoriteStatus.current[key]?.override ?? app.favorite;
        if (favoriteStatus.current[key]) {
            delete favoriteStatus.current[key]
        } else {
            favoriteStatus.current[key] = {override: !isFavorite, app};
        }
        favoriteStatus.current = {...favoriteStatus.current};
        dispatch(toggleAppFavorite(app, !isFavorite));
        try {
            await invokeCommand(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name,
                appVersion: app.metadata.version
            }));
        } catch (e) {
            favoriteStatus.current[key].override = !favoriteStatus.current[key].override;
            favoriteStatus.current = {...favoriteStatus.current};
        }
    }, [favoriteStatus]);

    const main = (
        <Box mx="auto" maxWidth="1340px">
            <Box mt="12px" />
            <FavoriteAppRow
                favoriteStatus={favoriteStatus}
                onFavorite={onFavorite}
                refreshId={refreshId}
            />

            <LargeSearchBox />


            { isLanding ?
                <>
                    {sections.data.sections[0] ?
                        <ApplicationRow
                            key={sections.data.sections[0].name}
                            items={sections.data.sections[0].items}
                            type={ApplicationCardType.WIDE}
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                            scrolling={false}
                        />
                    : <></>}

                    {sections.data.sections[1] ?
                        <ApplicationRow
                            key={sections.data.sections[1].name}
                            items={sections.data.sections[1].items}
                            type={ApplicationCardType.TALL}
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                            scrolling={false}
                        />
                    : <></>}


                    <Flex className={LandingDivider} justifyContent="space-around">
                        <Heading.h1>Featured<br />Applications</Heading.h1>
                        <img src={ucloudImage} />
                    </Flex>

                    {sections.data.sections[0] ?
                        <ApplicationRow
                            key={sections.data.sections[0].name}
                            items={sections.data.sections[0].items}
                            type={ApplicationCardType.WIDE}
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                            scrolling={false}
                        />
                    : <></>}

                    {sections.data.sections[1] ?
                        <ApplicationRow
                            key={sections.data.sections[1].name}
                            items={sections.data.sections[1].items}
                            type={ApplicationCardType.TALL}
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                            scrolling={false}
                        />
                    : <></>}

                    <FloatingButton />
                </>
            :
                sections.data.sections.map(section =>
                    <TagGrid
                        key={section.name}
                        tag={section.name}
                        items={section.items}
                        favoriteStatus={favoriteStatus}
                        onFavorite={onFavorite}
                        tagBanList={[]}
                        refreshId={refreshId}
                    />
                )
            }
        </Box>
    );
    return (
        <div className={AppOverviewMarginPaddingHack}>
            { !location.pathname.endsWith("full") && !location.pathname.endsWith("full/") ? 
                <div className={PolygonBackgroundClass}>
                    <MainContainer main={main} />
                </div>
            :
                <MainContainer main={main} />
            }
        </div>
    );
};

const AppOverviewMarginPaddingHack = injectStyleSimple("HACK-HACK-HACK", `
/* HACK */
    margin-top: -12px;
/* HACK */
`);

const PolygonBackgroundClass = injectStyleSimple("polygon-background", `
    background-image: url(${bgImage}), linear-gradient(#b7d8fb, #fff);
    background-position: 0% 35%;
    background-repeat: repeat;
    padding-bottom: 75px;
`);

const TagGridTopBoxClass = injectStyle("tag-grid-top-box", k => `
    ${k} {
        margin-top: 25px;
    }
`);

const TagGridBottomBoxClass = injectStyle("tag-grid-bottom-box", k => `
    ${k} {
        padding: 15px 10px 15px 10px;
        margin: 0 -10px;
        overflow-x: scroll;
    }
`);


interface TagGridProps {
    tag?: string;
    items: ApplicationGroup[];
    tagBanList?: string[];
    favoriteStatus: React.MutableRefObject<FavoriteStatus>;
    onFavorite: (app: ApplicationSummaryWithFavorite) => void;
    refreshId: number;
}

interface ApplicationRowProps {
    items: ApplicationGroup[];
    type: ApplicationCardType;
    favoriteStatus: React.MutableRefObject<FavoriteStatus>;
    onFavorite: (app: ApplicationSummaryWithFavorite) => void;
    refreshId: number;
    scrolling: boolean;
}

function filterAppsByFavorite(
    items: compute.ApplicationSummaryWithFavorite[],
    showFavorites: boolean,
    tagBanList: string[] = [],
    favoriteStatus: React.MutableRefObject<FavoriteStatus>
): compute.ApplicationSummaryWithFavorite[] {
    let _filteredItems = items
        .filter(it => !it.tags.some(_tag => tagBanList.includes(_tag)))
        .filter(item => {
            const isFavorite = favoriteStatus.current[favoriteStatusKey(item.metadata)]?.override ?? item.favorite;
            return isFavorite === showFavorites;
        });

    if (showFavorites) {
        _filteredItems = _filteredItems.concat(Object.values(favoriteStatus.current).filter(it => it.override).map(it => it.app));
        _filteredItems = _filteredItems.filter(it => favoriteStatus.current[favoriteStatusKey(it.metadata)]?.override !== false);
    }

    // Remove duplicates (This can happen due to favorite cache)
    {
        const observed = new Set<string>();
        const newList: ApplicationSummaryWithFavorite[] = [];
        for (const item of _filteredItems) {
            const key = favoriteStatusKey(item.metadata);
            if (!observed.has(key)) {
                observed.add(key);
                newList.push(item);
            }
        }
        return newList;
    }
}

function FavoriteAppRow({favoriteStatus, onFavorite}: Omit<TagGridProps, "tag" | "items" | "tagBanList">): JSX.Element {
    const items = useSelector<ReduxObject, compute.ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const filteredItems = React.useMemo(() =>
        filterAppsByFavorite(items, true, [], favoriteStatus),
        [items, favoriteStatus.current]);

    return <Flex overflowX="scroll" width="100%">
        <Flex mx="auto" mb="16px">
            {filteredItems.map(app =>
                <FavoriteApp key={app.metadata.name + app.metadata.version} name={app.metadata.name} version={app.metadata.version} title={app.metadata.title} onFavorite={() => onFavorite(app)} />
            )}
        </Flex>
    </Flex>
}

const SCROLL_SPEED = 156 * 4;

const ApplicationRow: React.FunctionComponent<ApplicationRowProps> = ({
    items, type, favoriteStatus, onFavorite, scrolling
}: ApplicationRowProps) => {
    const filteredItems = React.useMemo(() =>
        items,
        [items]
    );
        // TODO(Brian)
        //filterAppsByFavorite(items, false, [], favoriteStatus),
        //[items, favoriteStatus.current]);

    const scrollRef = React.useRef<HTMLDivElement>(null);

    const hasScroll = scrollRef.current && scrollRef.current.scrollWidth > scrollRef.current.clientWidth;

    return (
        <>
            {!hasScroll ? null : <>
                <Relative>
                    <Absolute height={0} width={0} top="152px">
                        <ScrollButton disabled={false} text={"⟨"} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: -SCROLL_SPEED, behavior: "smooth"});

                        }} />
                    </Absolute>
                </Relative>
                <Relative>
                    <Absolute height={0} width={0} right="0" top="152px">
                        <ScrollButton disabled={false} text={"⟩"} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: SCROLL_SPEED, behavior: "smooth"});
                        }} />
                    </Absolute>
                </Relative>
            </>}

            {type === ApplicationCardType.WIDE ?
                <div ref={scrollRef} className={TagGridBottomBoxClass}>
                    <Flex
                        justifyContent="space-between"
                        gap="10px"
                        py="10px"
                    >
                        {filteredItems.map(app =>
                            <Link key={app.id} to={Pages.run(app.application.metadata.name, app.application.metadata.version)}>
                                <AppCard
                                    type={ApplicationCardType.WIDE}
                                    onFavorite={() => onFavorite(app.application)}
                                    app={app}
                                    isFavorite={false}
                                    tags={[]}
                                />
                            </Link>
                        )}
                    </Flex>
                </div>
            :
                scrolling ?
                    <div ref={scrollRef} className={TagGridBottomBoxClass}>
                        <Grid
                            gridGap="25px"
                            gridTemplateRows={"repeat(1, 1fr)"}
                            gridTemplateColumns={"repeat(auto-fill, 166px)"}
                            style={{gridAutoFlow: "column"}}
                        >
                            {filteredItems.map(app =>
                                <Link key={app.id} to={Pages.run(app.application.metadata.name, app.application.metadata.version)}>
                                    <AppCard
                                        type={ApplicationCardType.EXTRA_TALL}
                                        onFavorite={() => onFavorite(app.application)}
                                        app={app}
                                        isFavorite={false}
                                        tags={[]}
                                    />
                                </Link>
                            )}
                        </Grid>
                    </div>
                :
                    <div ref={scrollRef} className={TagGridBottomBoxClass}>
                        <Flex
                            justifyContent="space-between"
                            gap="10px"
                            py="10px"
                        >
                            {filteredItems.map(app =>
                                <Link key={app.id} to={Pages.run(app.application.metadata.name, app.application.metadata.version)}>
                                    <AppCard
                                        type={ApplicationCardType.EXTRA_TALL}
                                        onFavorite={() => onFavorite(app.application)}
                                        app={app}
                                        isFavorite={false}
                                        tags={[]}
                                    />
                                </Link>
                            )}
                        </Flex>
                    </div>
            }
        </>
    )

};

const TagGrid: React.FunctionComponent<TagGridProps> = ({
    tag, items, tagBanList = [], favoriteStatus, onFavorite
}: TagGridProps) => {
    const filteredItems = React.useMemo(() =>
        items, []);
        //filterAppsByFavorite(items, false, tagBanList, favoriteStatus),
        //[items, favoriteStatus.current]);

    const scrollRef = React.useRef<HTMLDivElement>(null);

    if (filteredItems.length === 0) return null;

    const firstFour = filteredItems.length > 4 ? filteredItems.slice(0, 4) : filteredItems.slice(0, 1);
    const remaining = filteredItems.length > 4 ? filteredItems.slice(4) : filteredItems.slice(1);

    const hasScroll = scrollRef.current && scrollRef.current.scrollWidth > scrollRef.current.clientWidth;

    return (
        <>
            <div className={TagGridTopBoxClass}>
                {!tag ? null :
                    <Spacer
                        mt="15px" px="10px" alignItems={"center"}
                        left={<Heading.h2>{tag}</Heading.h2>}
                        right={(
                            <ShowAllTagItem tag={tag}>
                                <Heading.h4>Show All</Heading.h4>
                            </ShowAllTagItem>
                        )}
                    />
                }
            </div>
            
            {!hasScroll ? null : <>
                <Relative>
                    <Absolute height={0} width={0} top="152px">
                        <ScrollButton disabled={false} text={"⟨"} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: -SCROLL_SPEED, behavior: "smooth"});

                        }} />
                    </Absolute>
                </Relative>
                <Relative>
                    <Absolute height={0} width={0} right="0" top="152px">
                        <ScrollButton disabled={false} text={"⟩"} onClick={() => {
                            if (scrollRef.current) scrollRef.current.scrollBy({left: SCROLL_SPEED, behavior: "smooth"});
                        }} />
                    </Absolute>
                </Relative>
            </>}

            <div ref={scrollRef} className={TagGridBottomBoxClass}>
                <Flex
                    justifyContent="space-between"
                    gap="10px"
                    py="10px"
                >
                    {firstFour.map(app =>
                        <Link
                            key={app.application.metadata.name + app.application.metadata.version}
                            to={Pages.run(app.application.metadata.name, app.application.metadata.version)}
                        >
                            <AppCard
                                type={ApplicationCardType.WIDE}
                                onFavorite={() => onFavorite(app.application)}
                                app={app}
                                isFavorite={false}
                                tags={[]}
                            />
                        </Link>
                    )}
                </Flex>
            </div>
            <div ref={scrollRef} className={TagGridBottomBoxClass}>
                <Grid
                    gridGap="25px"
                    gridTemplateRows={"repeat(1, 1fr)"}
                    gridTemplateColumns={"repeat(auto-fill, 166px)"}
                    style={{gridAutoFlow: "column"}}
                >
                    {remaining.map(app =>
                        <Link key={app.id} to={Pages.run(app.application.metadata.name, app.application.metadata.version)}>
                            <AppCard
                                type={ApplicationCardType.EXTRA_TALL}
                                onFavorite={() => onFavorite(app.application)}
                                app={app}
                                isFavorite={false}
                                tags={[]}
                            />
                        </Link>
                    )}
                </Grid>
            </div>
        </>
    );
};

export default ApplicationsOverview;
