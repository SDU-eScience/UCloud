import * as React from "react";
import {useTitle} from "@/Navigation/Redux";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {classConcat, injectStyle} from "@/Unstyled";
import {Box, Button, Card, Flex, Grid, Icon, Markdown} from "@/ui-components";
import TitledCard from "@/ui-components/HighlightedCard";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {HTMLAttributeAnchorTarget, useCallback, useEffect, useRef, useState} from "react";
import {ThemeColor} from "@/ui-components/theme";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {doNothing} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {Link as ReactRouterLink} from "react-router-dom";
import {useAppSearch} from "@/Applications/Search";
import {Spotlight, TopPick} from "@/Applications/AppStoreApi";

const landingStyle = injectStyle("landing-page", k => `
    ${k} {
    }
    
    ${k} {
        margin: 0 auto;
        padding-top: 16px;
        padding-bottom: 16px;
        display: flex;
        flex-direction: column;
        gap: 16px;
        max-width: 1100px;
        min-width: 600px;
        min-height: 100vh;
    }
    
    ${k} > h1 {
        font-size: 1.2rem;
        padding: 0.6rem;
    }
`);

const LandingPage: React.FunctionComponent = () => {
    useTitle("Applications");
    const [landingPageState, fetchLandingPage] = useCloudAPI(
        AppStore.retrieveLandingPage({}),
        null
    );

    const [starred, fetchStarred] = useCloudAPI(
        AppStore.retrieveStars({}),
        {items: []}
    );

    const landingPage = landingPageState.data;

    const refresh = useCallback(() => {
        fetchLandingPage(AppStore.retrieveLandingPage({})).then(doNothing);
        fetchStarred(AppStore.retrieveStars({})).then(doNothing);
    }, []);
    useSetRefreshFunction(refresh);
    const appSearch = useAppSearch();

    if (!landingPage) return <div>
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
            </div>
        </div>
    </div>;

    return <div>
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <article className={landingStyle}>
                    <Flex alignItems={"center"}><h3>Applications</h3><Box ml="auto"/><UtilityBar onSearch={appSearch}/></Flex>
                    <Hero slides={landingPage.carrousel}/>
                    <TopPicksCard topPicks={landingPage.topPicks} />
                    <TitledCard title={"Starred applications"} icon={"heroStar"}>
                        <AppCardGrid>
                            {starred.data.items.map(a => {
                                return <AppCard1 name={a.metadata.name} title={a.metadata.title}
                                                 description={a.metadata.description} key={a.metadata.name}
                                                 isApplication/>;
                            })}
                        </AppCardGrid>
                    </TitledCard>

                    <TitledCard title={"Browse by category"} icon={"heroMagnifyingGlass"}>
                        <Grid gap={"16px"} gridTemplateColumns={"repeat(auto-fit, minmax(200px, 1fr)"}>
                            {landingPage.categories.map(c =>
                                <CategoryCard key={c.metadata.id} id={c.metadata.id}
                                              categoryTitle={c.specification.title}/>
                            )}
                        </Grid>
                    </TitledCard>

                    {landingPage.spotlight && <SpotlightCard spotlight={landingPage.spotlight} />}

                    <TabbedCard>
                        <TabbedCardTab icon={"heroCalendarDays"} name={"New applications"}>
                            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                {landingPage.newApplications.map(app => (
                                    <AppCard1 name={app.metadata.name} title={app.metadata.title}
                                              description={app.metadata.description} fullWidth key={app.metadata.name}
                                              isApplication/>
                                ))}
                            </Flex>
                        </TabbedCardTab>

                        <TabbedCardTab icon={"heroCheckCircle"} name={"Recently updated"}>
                            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                {landingPage.recentlyUpdated.map(app => (
                                    <AppCard1 name={app.metadata.name} title={app.metadata.title}
                                              description={app.metadata.description} fullWidth key={app.metadata.name}
                                              isApplication/>
                                ))}
                            </Flex>
                        </TabbedCardTab>
                    </TabbedCard>
                </article>
            </div>
        </div>
    </div>;
};

export const SpotlightCard: React.FunctionComponent<{
    spotlight: Spotlight;
    target?: HTMLAttributeAnchorTarget;
}> = ({spotlight, target}) => {
    return <TitledCard title={`Spotlight: ${spotlight.title}`} icon={"heroBeaker"}>
        <Flex flexDirection={"row"} gap={"32px"}>
            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"}>
                {spotlight.applications.map((pick, idx) => {
                    if (pick.groupId) {
                        return <AppCard1 key={idx} name={pick.groupId.toString()}
                                         title={pick.title} description={pick.description}
                                         applicationName={pick.defaultApplicationToRun}
                                         fullWidth target={target}/>;
                    } else {
                        return null;
                    }
                })}
            </Flex>
            <Box width={"400px"} flexShrink={1} flexGrow={0}>
                <div className={SpotlightDescription} style={{fontStyle: "italic"}}>
                    <Markdown allowedElements={["p"]}>
                        {spotlight.body}
                    </Markdown>
                </div>
            </Box>
        </Flex>
    </TitledCard>
};

const HeroStyle = injectStyle("hero", k => `
    ${k} > .carousel {
        display: flex;
        height: 335px;
        width: 100%;
    }
    
    ${k} > .carousel > div:nth-child(1) {
        flex-grow: 1;
    }
    
    ${k} > .carousel > div:nth-child(1) > img {
        object-fit: cover;
        width: calc(100% + 20px);
        height: calc(100% + 80px);
        object-position: 0 28%;
        margin-top: -20px;
        margin-left: -20px;
        border-right: 2px solid var(--borderColor);
    }
    
    ${k} > .carousel > div:nth-child(2) {
        display: flex;
        flex-direction: column;
        width: 400px;
        padding-left: 20px;
    }
    
    ${k} > .carousel h1 {
        margin-top: 0;
        margin-bottom: 1rem;
    }
    
    ${k} .indicators {
        position: relative;
        top: -98px;
        margin-left: -20px;
        width: 30%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: row;
        gap: 8px;
        padding: 20px;
        background: rgba(0, 0, 0, 0.4);
        right: calc(-70% - 18px);
        border-top-left-radius: 8px;
    }
    
    ${k} .indicator {
        background: #d3d3d4;
        width: 24px;
        height: 12px;
        border-radius: 4px;
        cursor: pointer;
    }
    
    ${k} .indicator:hover,
    ${k} .indicator.active {
        background: #a6a8a9;
    }
`);

const HeroIndicator: React.FunctionComponent<{
    active?: boolean;
    onClick: () => void;
}> = (props) => {
    return <div className={classConcat("indicator", props.active ? "active" : undefined)} onClick={props.onClick}/>;
};
export const Hero: React.FunctionComponent<{
    slides: AppStore.CarrouselItem[];
    imageLinks?: string[];
    isPreview?: boolean;
}> = ({slides, imageLinks, isPreview}) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const autoPage = useRef(true);
    useEffect(() => {
        const t = setInterval(() => {
            if (!autoPage.current) return;
            if (isPreview) return;
            setActiveIndex(prev => prev + 1);
        }, 5000);

        return () => {
            clearInterval(t);
        };
    }, [isPreview]);

    const moveDelta = useCallback((delta: number) => {
        setActiveIndex(prev => prev + delta);
        autoPage.current = true;
    }, []);
    const goBack = useCallback(() => moveDelta(-1), [moveDelta]);
    const goForwards = useCallback(() => moveDelta(1), [moveDelta]);

    if (slides.length == 0) return null;

    const index = activeIndex % slides.length;
    const slide = slides[index];

    let slideLink = slide.linkedWebPage;
    let slideLinkIsExternal = true;
    if (!slideLink) {
        slideLinkIsExternal = false;
        if (slide.resolvedLinkedApp) {
            slideLink = AppRoutes.jobs.create(slide.resolvedLinkedApp);
        } else {
            slideLink = AppRoutes.apps.group((slide.linkedGroup ?? 1).toString());
        }
    }

    const imageLink = imageLinks?.[index] ?? AppStore.retrieveCarrouselImage({index, slideTitle: slide.title});

    return <Card style={{overflow: "hidden"}}>
        <div className={HeroStyle}>
            <div className={"carousel"}>
                <div>
                    <img alt={"cover image"} src={imageLink}/>
                    <div className="indicators">
                        <Icon color={"#d3d3d4" as ThemeColor} hoverColor={"#a6a8a9" as ThemeColor} cursor={"pointer"}
                              name={"heroChevronLeft"} onClick={goBack}/>
                        {slides.map((s, i) =>
                            <HeroIndicator
                                key={i}
                                active={i === (activeIndex % slides.length)}
                                onClick={() => {
                                    setActiveIndex(i);
                                    autoPage.current = false;
                                }}
                            />)}
                        <Icon color={"#d3d3d4" as ThemeColor} hoverColor={"#a6a8a9" as ThemeColor} cursor={"pointer"}
                              name={"heroChevronRight"} onClick={goForwards}/>
                    </div>
                </div>
                <div>
                    <h1>{slide.title}</h1>
                    <div className={SpotlightDescription}>
                        <Markdown allowedElements={["p"]}>
                            {slide.body}
                        </Markdown>
                    </div>
                    <Box flexGrow={1}/>
                    <Box mb={8}><b>Image credit:</b> <i>{slide.imageCredit}</i></Box>
                    <ReactRouterLink
                        to={slideLink}
                        style={{width: "100%"}}
                        target={slideLinkIsExternal || isPreview ? "_blank" : undefined}
                        rel="noopener"
                    >
                        <Button fullWidth>
                            <Icon name={"heroPlay"}/>
                            <div>
                                {slide.linkedWebPage ? "Open web-page" : "Open application"}
                            </div>
                        </Button>
                    </ReactRouterLink>
                </div>
            </div>
        </div>
    </Card>;
};


const AppCard1Style = injectStyle("app-card-1", k => `
    ${k} {
        display: flex;
        gap: 8px;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--appCardBorderColor, var(--borderColor));
        width: 331px;
        cursor: pointer;
        align-items: center;
    }
    
    ${k}.full-width {
        width: 100%;
    }
    
    ${k} h2 {
        font-size: 1.0rem;
        font-weight: bold;
        margin: 0;
        margin-top: -0.3rem;
    }
    
    ${k} .content {
        max-width: calc(100% - 50px);
    }
    
    ${k} .description, ${k} .description p {
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        font-size: 1rem;
        color: var(--textSecondary);
        margin: 0;
    }
    
    ${k} .description p:first-child {
        margin-top: 0;
    }
    
    ${k} .description p:last-child {
        margin-bottom: 0;
    }
`);

const AppCard1: React.FunctionComponent<{
    name: string;
    title: string;
    description: string;
    fullWidth?: boolean;
    isApplication?: boolean;
    applicationName?: string | null;
    target?: HTMLAttributeAnchorTarget;
}> = props => {
    let link = props.isApplication ? AppRoutes.jobs.create(props.name) : AppRoutes.apps.group(props.name);
    if (props.applicationName) {
        link = AppRoutes.jobs.create(props.applicationName);
    }

    return <ReactRouterLink to={link} target={props.target}
                            className={classConcat(AppCard1Style, props.fullWidth ? "full-width" : undefined)}>
        <SafeLogo name={props.name} type={props.isApplication ? "APPLICATION" : "GROUP"} size={"36px"}/>
        <div className={"content"}>
            <h2>{props.title}</h2>
            <div className={"description"}>
                <Markdown allowedElements={["p", "i", "b"]}>{props.description}</Markdown>
            </div>
        </div>
    </ReactRouterLink>;
};

const AppCardGridStyle = injectStyle("app-card-grid", k => `
    ${k} {
        display: flex;
        flex-direction: row;
        column-gap: 32px;
        row-gap: 10px;
        flex-wrap: wrap;
    }
    
    ${k} *:nth-child(3n+1):nth-last-child(-n+3),
    ${k} *:nth-child(3n+1):nth-last-child(-n+3) ~ * {
        --appCardBorderColor: transparent;
    }
`);

const AppCardGrid: React.FunctionComponent<{ children: React.ReactNode }> = ({children}) => {
    return <div className={AppCardGridStyle}>{children}</div>;
}

const SafeLogoStyle = injectStyle("safe-app-logo", k => `
    ${k} {
        background: var(--appLogoBackground);
        padding: 4px;
        border-radius: 5px;
        border: var(--backgroundCardBorder);
    }
`);
const SafeLogo: React.FunctionComponent<{
    name: string,
    type: "APPLICATION" | "TOOL" | "GROUP",
    size: string
}> = props => {
    return <div className={SafeLogoStyle}>
        <AppToolLogo size={props.size} name={props.name} type={props.type}/>
    </div>;
}

const CategoryCard: React.FunctionComponent<{
    id: number;
    categoryTitle: string;
}> = props => {
    return <ReactRouterLink to={AppRoutes.apps.category(props.id)}>
        <Button fullWidth>{props.categoryTitle}</Button>
    </ReactRouterLink>;
}

const SpotlightDescription = injectStyle("spotlight-description", k => `
    ${k} p:first-child {
        margin-top: 0;
    }
    
    ${k} p:last-child {
        margin-bottom: 0;
    }
`)

export const TopPicksCard: React.FunctionComponent<{topPicks: TopPick[]}> = ({topPicks}) => {
    return <TitledCard title={"Top picks"} icon={"heroChartBar"}>
        <AppCardGrid>
            {topPicks.map(pick => {
                if (pick.groupId) {
                    return <AppCard1 key={pick.groupId} name={pick.groupId.toString()}
                                     title={pick.title} description={pick.description}
                                     applicationName={pick.defaultApplicationToRun}/>;
                } else {
                    return null;
                }
            })}
        </AppCardGrid>
    </TitledCard>
};

export default LandingPage;