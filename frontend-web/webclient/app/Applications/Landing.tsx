import * as React from "react";
import {usePage} from "@/Navigation/Redux";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {classConcat, injectStyle} from "@/Unstyled";
import {Box, Button, Card, Flex, Grid, Icon, MainContainer, Image, Markdown, Relative} from "@/ui-components";
import TitledCard from "@/ui-components/HighlightedCard";
import {AppLogoRaw, SafeLogo} from "@/Applications/AppToolLogo";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {CSSProperties, HTMLAttributeAnchorTarget, useCallback, useEffect, useRef, useState} from "react";
import {appColors} from "@/ui-components/theme";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {doNothing} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {Link as ReactRouterLink} from "react-router-dom";
import {useAppSearch} from "@/Applications/Search";
import {Spotlight, TopPick} from "@/Applications/AppStoreApi";
import {shade, tint} from "@/ui-components/GlobalStyle";
import {LogoWithText} from "@/Applications/LogoWithText";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const landingStyle = injectStyle("landing-page", k => `
    ${k} {
        display: flex;
    }
    
    ${k} {
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        gap: 24px;
        min-width: 600px;
        min-height: 100vh;
    }
    
    ${k} h3 {
        font-size: 1.2rem;
        padding: 0.6rem 0;
    }
    
    ${k} > h1 {
        font-size: 1.2rem;
        padding: 0.6rem 0;
    }
`);

const LandingPage: React.FunctionComponent = () => {
    usePage("Applications", SidebarTabId.APPLICATIONS);
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

    return <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer main={<article className={landingStyle}>
                    <Flex alignItems={"center"}><Box ml="auto" /><UtilityBar onSearch={appSearch} /></Flex>
                    <Hero slides={landingPage.carrousel} />
                    {starred.data.items.length > 0 ?
                        <StarredApplications2 apps={starred.data.items} /> : null}


                    <TopPicksCard2 topPicks={landingPage.topPicks} />

                    {landingPage.spotlight ? <SpotlightCard2 spotlight={landingPage.spotlight} /> : null}

                    <div>
                        <h3>Browse by category</h3>
                        <Grid gap={"16px"} gridTemplateColumns={"repeat(auto-fit, minmax(250px, 1fr)"}>
                            {landingPage.categories.map((c, idx) =>
                                <CategoryCard key={c.metadata.id} id={c.metadata.id} idx={idx}
                                    categoryTitle={c.specification.title} />
                            )}
                        </Grid>
                    </div>

                    <div>
                        <h3>Updated applications</h3>
                        <TabbedCard>
                            <TabbedCardTab icon={"heroCalendarDays"} name={"New applications"}>
                                <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                    {landingPage.newApplications.map(app => (
                                        <AppCard1 name={app.metadata.name} title={app.metadata.title}
                                            description={app.metadata.description} fullWidth
                                            key={app.metadata.name}
                                            isApplication />
                                    ))}
                                </Flex>
                            </TabbedCardTab>

                            <TabbedCardTab icon={"heroCheckCircle"} name={"Recently updated"}>
                                <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                    {landingPage.recentlyUpdated.map(app => (
                                        <AppCard1 name={app.metadata.name} title={app.metadata.title}
                                            description={app.metadata.description} fullWidth
                                            key={app.metadata.name}
                                            isApplication />
                                    ))}
                                </Flex>
                            </TabbedCardTab>
                        </TabbedCard>
                    </div>
                </article>} />
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
                            fullWidth target={target} />;
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

export const SpotlightCard2: React.FunctionComponent<{
    spotlight: Spotlight;
    target?: HTMLAttributeAnchorTarget;
}> = ({spotlight, target}) => {
    return <div>
        <h3>Spotlight: {spotlight.title}</h3>
        <Flex gap={"16px"}>
            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"}>
                {spotlight.applications.map((pick, idx) => {
                    if (pick.groupId) {
                        return <AppCard2 key={idx} name={pick.groupId.toString()}
                            title={pick.title} description={pick.description}
                            applicationName={pick.defaultApplicationToRun}
                            fullWidth target={target} />;
                    } else {
                        return null;
                    }
                })}
            </Flex>

            <blockquote
                className={SpotlightDescription}
                style={{
                    fontStyle: "italic",
                    flexShrink: "1",
                    flexBasis: "400px",
                    display: "flex",
                    alignItems: "center"
                }}
            >
                <Markdown allowedElements={["p"]}>
                    {spotlight.body}
                </Markdown>
            </blockquote>
        </Flex>
    </div>;
};

const HeroStyle = injectStyle("hero", k => `
    ${k} > .carousel {
        display: flex;
        height: 335px;
        width: 100%;
    }
    
    ${k} > .carousel > .carouselImages {
        flex-grow: 1;
        position: relative;
        display: flex;
        flex-direction: row;
        overflow: hidden;
    }
    
    @keyframes translateImage {
        0%   {transform: translateX(-100%);}
        100%   {transform: translateX(0);}
    }

    ${k} > .carousel > .carouselImages img {
        object-fit: cover;
        width: 100%;
        height: 100%;
        flex-grow: 0;
        flex-shrink: 0;
        flex-basis: 100%;
        object-position: 50% 50%;
        animation: translateImage 0.5s;
    }

    
    ${k} > .carousel > .carouselText  {
        display: flex;
        flex-direction: column;
        width: 400px;
        min-width: 400px;
        padding: 20px;
    }
    
    ${k} > .carousel h1 {
        margin-top: 0;
        margin-bottom: 1rem;
    }
    
    ${k} .indicators {
        position: absolute;
        bottom: 0;
        width: 300px;
        left: calc(50% - 150px); 
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: row;
        gap: 8px;
        padding: 20px;
    }
    
    ${k} .indicators > .indicator {
        background: var(--secondaryMain);
        width: 24px;
        height: 12px;
        border-radius: 4px;
        cursor: pointer;
    }
    
    ${k} .indicators > .indicator:hover,
    ${k} .indicators > .indicator.active {
        background: var(--secondaryDark);
    }
`);  

const HeroIndicator: React.FunctionComponent<{
    active?: boolean;
    onClick: () => void;
}> = (props) => {
    return <div className={classConcat("indicator", props.active ? "active" : undefined)} onClick={props.onClick} />;
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

    if (slides.length == 0) return null;

    const index = activeIndex % slides.length;
    const slide = slides[index];
    const nextSlideIndex = (index + 1) % slides.length;
    const nextSlide = slides[nextSlideIndex];
    const prevSlideIndex = (index == 0) ? slides.length - 1 : index - 1;
    const prevSlide = slides[prevSlideIndex];

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
    const nextImageLink = imageLinks?.[index] ?? AppStore.retrieveCarrouselImage({index: nextSlideIndex, slideTitle: nextSlide.title});
    const prevImageLink = imageLinks?.[index] ?? AppStore.retrieveCarrouselImage({index: prevSlideIndex, slideTitle: prevSlide.title});

    return <Card style={{overflow: "hidden", border: 0, padding: 0}}>
        {/* Note(Jonas): Pre-fetch next image, so text and image change at the same time in the carousel */}
        <link rel="prefetch" as="image" href={nextImageLink} />
        <div className={HeroStyle}>
            <div className={"carousel"}>
                <div className={"carouselImages"}>
                    <img key={2*activeIndex+1} alt={"cover image"} src={imageLink} />
                    <img key={activeIndex} alt={"cover image"} src={prevImageLink} />
                    <div className="indicators">
                        {slides.map((s, i) =>
                            <HeroIndicator
                                key={i}
                                active={i === (activeIndex % slides.length)}
                                onClick={() => {
                                    setActiveIndex(i);
                                    autoPage.current = false;
                                }}
                            />)}
                    </div>
                </div>
                <div className={"carouselText"}>
                    <h1>{slide.title}</h1>
                    <div className={SpotlightDescription}>
                        <Markdown allowedElements={["p"]}>
                            {slide.body}
                        </Markdown>
                    </div>
                    <Box flexGrow={1} />
                    {(slide.imageCredit != "Unknown") && <Box mb={8}><b>Image credit:</b> <i>{slide.imageCredit}</i></Box>}
                    <ReactRouterLink
                        to={slideLink}
                        style={{width: "100%"}}
                        target={slideLinkIsExternal || isPreview ? "_blank" : undefined}
                        rel="noopener"
                    >
                        <Button fullWidth>
                            <Icon name={"heroPlay"} />
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
        border-bottom: 0.5px solid var(--appCardBorderColor, var(--borderColor));
        width: 331px;
        cursor: pointer;
        align-items: center;
    }

    ${k}:last-of-type {
        border-bottom: unset;
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

    return <ReactRouterLink
        to={link}
        target={props.target}
        className={classConcat(AppCard1Style, props.fullWidth ? "full-width" : undefined)}
    >
        <SafeLogo name={props.name} type={props.isApplication ? "APPLICATION" : "GROUP"} size={"36px"} />
        <div className={"content"}>
            <h2>{props.title}</h2>
            <div className={"description"}>
                <Markdown allowedElements={["p", "i", "b"]}>{props.description}</Markdown>
            </div>
        </div>
    </ReactRouterLink>;
};

const AppCard2Style = injectStyle("app-card-2", k => `
    ${k} {
        display: flex;
        gap: 16px;
        width: 331px;
        cursor: pointer;
        align-items: center;
        background: var(--backgroundCard);
        border-radius: 8px;
        box-shadow: var(--defaultShadow);
    }
    
    ${k}:hover {
        background: var(--backgroundCardHover);
    }
    
    ${k} > *:first-child {
        border-top-right-radius: 0;
        border-bottom-right-radius: 0;
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
        margin: 16px 0;
        max-width: calc(100% - 102px);
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

export const AppCard2: React.FunctionComponent<{
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
        className={classConcat(AppCard2Style, props.fullWidth ? "full-width" : undefined)}>
        <SafeLogo name={props.name} type={props.isApplication ? "APPLICATION" : "GROUP"} size={"56px"} />
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

const AppCardGrid: React.FunctionComponent<{children: React.ReactNode}> = ({children}) => {
    return <div className={AppCardGridStyle}>{children}</div>;
}

const CategoryCardStyle = injectStyle("category-card", k => `
    ${k} {
        border-radius: 8px;
        height: 90px;
        font-size: 17px;
        display: flex;
        align-items: end;
        padding: 16px;
        overflow: hidden;
        color: var(--fixedWhite);
        box-shadow: var(--defaultShadow);
        background: var(--primaryLight);
    }
    
    ${k}:hover {
        background: var(--primaryMain);
    }
    
    ${k} .logo-wrapper {
        position: absolute;
        top: -140px;
        left: 160px;
        z-index: 0;
        transform: rotate(180deg);
    }
    
    ${k} span {
        z-index: 10;
    }
`);

const CategoryCard: React.FunctionComponent<{
    id: number;
    categoryTitle: string;
    idx: number;
}> = props => {
    const appCIdx = props.idx % appColors.length;
    const appC = appColors[appCIdx][1];

    const baseColor = tint(appC, 0.0);
    const gradStart = tint(baseColor, 0.1);
    const gradEndHover = shade(gradStart, 0.2);
    const gradEnd = shade(gradStart, 0.3);

    const style: CSSProperties = {};
    style["--card-start"] = gradStart;
    style["--card-end"] = gradEnd;
    style["--card-end-hover"] = gradEndHover;

    return <ReactRouterLink to={AppRoutes.apps.category(props.id)}>
        <div className={CategoryCardStyle} style={style}>
            <Relative>
                <div className={"logo-wrapper"}>
                    <AppLogoRaw rot={60} color1Offset={1} color2Offset={2} appC={appCIdx} size={"130px"} />
                </div>
            </Relative>
            <span>{props.categoryTitle}</span>
        </div>
    </ReactRouterLink>;
}

const SpotlightDescription = injectStyle("spotlight-description", k => `
    blockquote${k} {
        margin: 0;
        padding-left: 16px;
        border-left: 5px solid var(--spotlightBlockquoteColor);
    }
    
    html.light {
        --spotlightBlockquoteColor: var(--primaryMain);
    }
    
    html.dark {
        --spotlightBlockquoteColor: var(--textPrimary);
    }
    
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
                        applicationName={pick.defaultApplicationToRun} />;
                } else {
                    return null;
                }
            })}
        </AppCardGrid>
    </TitledCard>
};

const TopPickCardGridStyle = injectStyle("top-pick-grid", k => `
    ${k} {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
        grid-template-rows: repeat(auto-fit, 115px);
        gap: 16px;
    }
    
    ${k} > *:first-child {
        grid-row: span 2;
    }
    
    ${k} > *:first-child > * {
        height: calc(115px * 2 + 16px);
    }
    
    ${k}.small > *:first-child {
        grid-row: span 1;
    }
    
    ${k}.small > *:first-child > * {
        grid-row: span 1;
        height: 115px;
    }
    
    ${k} > * {
        height: 115px;
    }
`);

const TopPickCardStyle = injectStyle("top-pick", k => `
    ${k} {
        height: 115px;
        box-shadow: var(--defaultShadow);
        border-radius: 8px;
        overflow: hidden;
        display: flex;
        justify-content: center;
        align-items: center;
        background: var(--backgroundCard);
    }
    
    ${k}:hover {
        background: var(--backgroundCardHover);
    }
`);

const LogoCard: React.FunctionComponent<{
    id: string | number;
    link: string;
    title: string;
    large?: boolean;
}> = ({id, link, title, large}) => {
    return <ReactRouterLink to={link}>
        <div className={TopPickCardStyle}>
            <LogoWithText id={id} title={title} size={60} forceUnder={large} />
        </div>
    </ReactRouterLink>;
}

export const TopPicksCard2: React.FunctionComponent<{topPicks: TopPick[]}> = ({topPicks}) => {
    return <div>
        <h3>Top picks</h3>
        <div className={TopPickCardGridStyle}>
            {topPicks.map((pick, idx) => {
                if (pick.groupId) {
                    let link = AppRoutes.apps.group(pick.groupId.toString());
                    if (pick.defaultApplicationToRun) {
                        link = AppRoutes.jobs.create(pick.defaultApplicationToRun);
                    }

                    return <LogoCard key={pick.groupId} large={idx === 0 && topPicks.length > 5} id={pick.groupId}
                        title={pick.title} link={link} />;
                } else {
                    return null;
                }
            })}
        </div>
    </div>
};

export const StarredApplications2: React.FunctionComponent<{
    apps: AppStore.ApplicationSummaryWithFavorite[]
}> = ({apps}) => {
    return <div>
        <h3>Starred applications</h3>
        <div className={classConcat(TopPickCardGridStyle, apps.length <= 5 ? "small" : undefined)}>
            {apps.map((app, idx) => {
                const link = AppRoutes.jobs.create(app.metadata.name);
                const groupId = app.metadata.group?.metadata?.id ?? 0;

                return <LogoCard
                    key={app.metadata.name}
                    large={idx === 0 && apps.length > 5}
                    title={app.metadata.title}
                    id={app.metadata.name}
                    link={link}
                />;
            })}
        </div>
    </div>
};

export default LandingPage;