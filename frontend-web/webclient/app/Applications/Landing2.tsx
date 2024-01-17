import * as React from "react";
import {useTitle} from "@/Navigation/Redux";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {classConcat, injectStyle} from "@/Unstyled";
import {Absolute, Box, Button, Card, Flex, Grid, Icon, Relative} from "@/ui-components";
import TitledCard from "@/ui-components/HighlightedCard";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import heroExample from "@/Assets/Images/hero-example.jpeg";
import heroExample2 from "@/Assets/Images/hero-example-2.jpeg";
import heroExample3 from "@/Assets/Images/hero-example-3.jpeg";
import heroExample4 from "@/Assets/Images/hero-example-4.jpeg";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useCallback, useEffect, useRef, useState} from "react";
import {ThemeColor} from "@/ui-components/theme";

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

    return <div>
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <article className={landingStyle}>
                    <Flex alignItems={"center"}><h3>Applications</h3><Box ml="auto"/><UtilityBar searchEnabled={true}/></Flex>
                    <Hero/>

                    <TitledCard title={"Top picks"} icon={"heroChartBar"}>
                        <AppCardGrid>
                            <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}/>
                            <AppCard1 name={"4"} title={"JupyterLab"}
                                      description={"JupyterLab ecosystem for Data Science."}/>
                            <AppCard1 name={"15"} title={"RStudio"}
                                      description={"IDE for R and statistical computing."}/>
                            <AppCard1 name={"11"} title={"Terminal"}
                                      description={"Terminal in the browser and via SSH."}/>
                            <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}/>
                            <AppCard1 name={"1"} title={"MinIO"}
                                      description={"High performance object storage server."}/>
                        </AppCardGrid>
                    </TitledCard>

                    <TitledCard title={"Starred applications"} icon={"heroStar"}>
                        <AppCardGrid>
                            <AppCard1 name={"4"} title={"JupyterLab"}
                                      description={"JupyterLab ecosystem for Data Science."}/>
                            <AppCard1 name={"11"} title={"Terminal"}
                                      description={"Terminal in the browser and via SSH."}/>
                            <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}/>
                        </AppCardGrid>
                    </TitledCard>

                    <TitledCard title={"Browse by category"} icon={"heroMagnifyingGlass"}>
                        <Grid gap={"16px"} gridTemplateColumns={"repeat(auto-fit, minmax(200px, 1fr)"}>
                            <CategoryCard categoryTitle={"Social sciences"}/>
                            <CategoryCard categoryTitle={"Applied sciences"}/>
                            <CategoryCard categoryTitle={"Natural sciences"}/>
                            <CategoryCard categoryTitle={"Health sciences"}/>
                            <CategoryCard categoryTitle={"Software development"}/>
                            <CategoryCard categoryTitle={"Bioinformatics"}/>
                            <CategoryCard categoryTitle={"Engineering"}/>
                            <CategoryCard categoryTitle={"Data analytics"}/>
                            <CategoryCard categoryTitle={"Artificial intelligence"}/>
                            <CategoryCard categoryTitle={"Quantum computing"}/>
                            <CategoryCard categoryTitle={"Virtual machines"}/>
                            <CategoryCard categoryTitle={"Remote desktop environments"}/>
                            <CategoryCard categoryTitle={"Type 3 - Large memory"}/>
                        </Grid>
                    </TitledCard>

                    <TitledCard title={"Spotlight: Artificial intelligence"} icon={"heroBeaker"}>
                        <Flex flexDirection={"row"} gap={"32px"}>
                            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"}>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                            </Flex>
                            <Box width={"400px"} flexShrink={1} flexGrow={0}>
                                <p style={{marginTop: "0", fontStyle: "italic"}}>
                                    Artificial intelligence (AI) tools are becoming more and more important for
                                    businesses
                                    and
                                    individuals. AI can help optimize business processes, solve problems, and make our
                                    lives
                                    easier.
                                </p>
                                <p style={{fontStyle: "italic"}}>
                                    According to Gartner, around 37% of companies use AI to run their businesses.
                                    There are many different types of AI tools available, ranging from productivity and
                                    cybersecurity to coding and data analysis. Some of the top AI tools include Dropbox,
                                    Symantec Endpoint Protection, Outmatch, and Tableau.
                                </p>

                                <p style={{marginBottom: "0", fontStyle: "italic"}}>
                                    Thanks for telling me that Dropbox is an AI tool.
                                </p>
                            </Box>
                        </Flex>
                    </TitledCard>

                    <TabbedCard>
                        <TabbedCardTab icon={"heroCalendarDays"} name={"New applications"}>
                            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}
                                          fullWidth/>
                                <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}
                                          fullWidth/>
                                <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}
                                          fullWidth/>
                                <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}
                                          fullWidth/>
                                <AppCard1 name={"13"} title={"VS Code"} description={"Text and code editor."}
                                          fullWidth/>

                            </Flex>
                        </TabbedCardTab>

                        <TabbedCardTab icon={"heroCheckCircle"} name={"Recently updated"}>
                            <Flex flexGrow={1} flexDirection={"column"} gap={"16px"} mt={"16px"}>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                                <AppCard1 name={"12"} title={"Ubuntu"} description={"Remote desktop with Ubuntu."}
                                          fullWidth/>
                            </Flex>
                        </TabbedCardTab>
                    </TabbedCard>
                </article>
            </div>
        </div>


    </div>;
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
        max-width: 400px;
        padding-left: 20px;
    }
    
    ${k} > .carousel h1 {
        margin-top: 0;
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

interface CarouselSlide {
    imageSource: string;
    title: string;
    description: string;
    imageCredit: string;
}

const slides: CarouselSlide[] = [
    {
        title: "VS Code",
        description: `Visual Studio Code is a free source-code editor made by Microsoft. VS Code has many extensions available to add new features and 
        functionality. It has a built-in debugger, Git integration, and support for IntelliSense, which provides 
        intelligent code completion and suggestions. With its user-friendly interface and extensive documentation.`,
        imageCredit: "Generated with AI",
        imageSource: heroExample,
    },
    {
        title: "JupyterLab",
        description: `JupyterLab is a powerful tool for data scientists and programmers alike. It is an 
        open-source web-based integrated development environment that allows you to work with notebooks, 
        terminals, and text editors in a single window. JupyterLab is built on top of the 
        Jupyter Notebook and provides a modern and flexible environment for data analysis and 
        visualization.`,
        imageCredit: "Generated with AI",
        imageSource: heroExample2,
    },
    {
        title: "MinIO",
        description: `MinIO is a software-defined object store that runs on any cloud or on-premises infrastructure. 
        It is built for large scale data workloads and supports Kubernetes, encryption, replication, immutability, 
        and data lifecycle management. MinIO offers bucket-level 
        granularity and supports both synchronous and near-synchronous replication.`,
        imageCredit: "Generated with AI",
        imageSource: heroExample3,
    },
    {
        title: "RStudio",
        description: `RStudio is a software that helps people write computer programs. It is used by many people who 
        want to write code in the R language. RStudio is a very useful tool for people who want to write code in R 
        because it makes it easier to write and test code. Some of these features include a 
        console, a code editor, and a debugger.`,
        imageCredit: "Generated with AI",
        imageSource: heroExample4,
    }
];

const HeroIndicator: React.FunctionComponent<{
    active?: boolean;
    onClick: () => void;
}> = (props) => {
    return <div className={classConcat("indicator", props.active ? "active" : undefined)} onClick={props.onClick}/>;
};
const Hero: React.FunctionComponent = () => {
    const [activeIndex, setActiveIndex] = useState(0);
    const autoPage = useRef(true);
    useEffect(() => {
        const t = setInterval(() => {
            if (!autoPage.current) return;
            setActiveIndex(prev => prev + 1);
        }, 5000);

        return () => {
            clearInterval(t);
        };
    }, []);

    const moveDelta = useCallback((delta: number) => {
        setActiveIndex(prev => prev + delta);
        autoPage.current = true;
    }, []);
    const goBack = useCallback(() => moveDelta(-1), [moveDelta]);
    const goForwards = useCallback(() => moveDelta(1), [moveDelta]);

    const slide = slides[activeIndex % slides.length];

    return <Card style={{overflow: "hidden"}}>
        <div className={HeroStyle}>
            <div className={"carousel"}>
                <div>
                    <img alt={"cover image"} src={slide.imageSource}/>
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
                    <p>{slide.description}</p>
                    <Box flexGrow={1}/>
                    <Box mb={8}><b>Image credit:</b> <i>{slide.imageCredit}</i></Box>
                    <Button><Icon name={"heroPlay"}/> Open application</Button>


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
    
    ${k} p {
        font-size: 1rem;
        color: var(--textSecondary);
        margin: 0;
    }
`);

const AppCard1: React.FunctionComponent<{
    name: string;
    title: string;
    description: string;
    fullWidth?: boolean;
}> = props => {
    return <div className={classConcat(AppCard1Style, props.fullWidth ? "full-width" : undefined)}>
        <SafeLogo name={props.name} type={"GROUP"} size={"36px"}/>
        <div>
            <h2>{props.title}</h2>
            <p>{props.description}</p>
        </div>
    </div>;
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

const CategoryCardStyle = injectStyle("category-card", k => `
    ${k} {
        
    }
`);

const CategoryCard: React.FunctionComponent<{
    categoryTitle: string;
}> = props => {
    return <Button className={CategoryCardStyle}>
        {props.categoryTitle}
    </Button>
}

export default LandingPage;