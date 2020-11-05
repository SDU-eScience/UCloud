import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {PRODUCT_NAME} from "../../../site.config.json";
import {useHistory, useParams} from "react-router";
import {MainContainer} from "MainContainer/MainContainer";
import {useCloudAPI} from "Authentication/DataHook";
import * as Jobs from "./index";
import {isJobStateTerminal, JobState} from "./index";
import * as Heading from "ui-components/Heading";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useTitle} from "Navigation/Redux/StatusActions";
import {shortUUID} from "UtilityFunctions";
import {AppToolLogo} from "Applications/AppToolLogo";
import styled, {keyframes} from "styled-components";
import {Box, Button, Flex, Icon, Link} from "ui-components";
import {DashboardCard} from "Dashboard/Dashboard";
import {IconName} from "ui-components/Icon";
import * as anims from "react-animations";
import {buildQueryString, getQueryParamOrElse} from "Utilities/URIUtilities";
import {device, deviceBreakpoint} from "ui-components/Hide";
import {CSSTransition} from "react-transition-group";
import {appendToXterm, useXTerm} from "Applications/Jobs/xterm";
import {VirtualFileTable} from "Files/VirtualFileTable";
import {arrayToPage} from "Types";
import {fileTablePage, mockFile} from "Utilities/FileUtilities";
import {Client, WSFactory} from "Authentication/HttpClientInstance";
import {FollowStdStreamResponse} from "Applications";

const enterAnimation = keyframes`${anims.pulse}`;
const busyAnim = keyframes`${anims.fadeIn}`;

const Container = styled.div`
    --logoScale: 1;
    --logoBaseSize: 200px;
    --logoSize: calc(var(--logoBaseSize) * var(--logoScale));
    
    --logoPX: 50px;
    --logoPY: 50px;
    
    /* NOTE(Dan): 14px are added by MainContainer and sidebar */
    --logoIndentX: calc(var(--sidebarWidth) + var(--logoPX) + 14px);
    --logoIndentY: calc(var(--headerHeight) + var(--logoPY) + 14px);
    
    /* center while accounting for the frame */
    --logoCenterX: calc((100vw + var(--sidebarWidth) - var(--logoSize)) / 2);
    --logoCenterY: calc((100vh + var(--headerHeight) - var(--logoSize)) / 2);
    
    margin: 50px; /* when header is not wrapped this should be equal to logoPX and logoPY */   
    max-width: 2200px;
    
    ${device("xs")} {
        margin-left: 0;
        margin-right: 0;
    }
    
    & {
        display: flex;
        flex-direction: column;
    }
    
    .logo-wrapper {
        position: absolute;
        
        left: var(--logoCenterX);
        top: var(--logoCenterY);
    }
    
    .logo-wrapper.active {
        transition: all 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
        transform: translate3d(
            calc(-1 * var(--logoCenterX) + var(--logoIndentX)),
            calc(-1 * var(--logoCenterY) + var(--logoIndentY)),
            0
        );
    }
    
    .logo-wrapper.active .logo-scale {
        transition: transform 300ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
        transform: 
            scale3d(
                var(--logoScale),
                var(--logoScale),
                var(--logoScale)
            )
            translate3d(
                calc(var(--logoBaseSize) / (1 / var(--logoScale)) - var(--logoBaseSize)),
                calc(var(--logoBaseSize) / (1 / var(--logoScale)) - var(--logoBaseSize)),
                0
            );
    }
    
    .fake-logo {
        /* NOTE(Dan): the fake logo takes the same amount of space as the actual logo, 
           this basically fixes our document flow */
        display: block;
        width: var(--logoSize);
        height: var(--logoSize);
        content: '';
    }
    
    .data.data-enter-done {
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }
    
    .data.data-enter-active {
        opacity: 1;
        transform: translate3d(0, 0, 0);
        transition: transform 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
    }
    
    .data.data-exit {
        opacity: 1;
    }
    
    .data-exit-active {
        display: none;
    }
 
    .data {
        width: 100%; /* fix info card width */
        opacity: 0;
        transform: translate3d(0, 50vh, 0);
    }
    
    .header-text {
        margin-left: 32px;
        margin-top: calc(var(--logoScale) * 16px);
    }
    
    ${deviceBreakpoint({maxWidth: "1000px"})} {
        .fake-logo {
            width: 100%; /* force the header to wrap */
        }
        
        & {
            --logoIndentX: var(--logoCenterX);
        }
        
        .header {
            text-align: center;
        }
        
        .header-text {
            margin-left: 0;
            margin-top: 0;
            width: 100%;
        }
    }
    
    &.IN_QUEUE .logo {
        animation: 3s ${enterAnimation} infinite;
    }
    
    &.RUNNING {
        --logoScale: 0.5;
    }
`;

function useJobUpdates(jobId: string, callback: (entry: FollowStdStreamResponse) => void): void {
    useEffect(() => {
        const conn = WSFactory.open(
            "/hpc/jobs", {
                init: conn => {
                    conn.subscribe({
                        call: "hpc.jobs.followWS",
                        payload: {jobId, stdoutLineStart: 0, stderrLineStart: 0},
                        handler: message => {
                            const streamEntry = message.payload as FollowStdStreamResponse;
                            callback(streamEntry);
                        }
                    });
                }
            }
        );

        return () => {
            conn.close();
        };
    }, [jobId, callback]);
}

interface JobUpdateListener {
    handler: (e: FollowStdStreamResponse) => void;
}

export const View: React.FunctionComponent = () => {
    const {id} = useParams<{ id: string }>();
    const history = useHistory();

    // Note: This might not match the real app name
    const appNameHint = getQueryParamOrElse(history.location.search, "app", "");
    const action = getQueryParamOrElse(history.location.search, "action", "view");
    const delayInitialAnim = action === "start";

    const [jobFetcher, fetchJob] = useCloudAPI<Jobs.Job | undefined>({noop: true}, undefined);
    const job = jobFetcher.data;

    const useFakeState = useMemo(() => localStorage.getItem("useFakeState") !== null, []);

    useSidebarPage(SidebarPages.Runs);
    useTitle(`Job ${shortUUID(id)}`);
    useEffect(() => {
        fetchJob(Jobs.findById({id}));
    }, [id]);

    const [dataAnimationAllowed, setDataAnimationAllowed] = useState<boolean>(false);
    const [logoAnimationAllowed, setLogoAnimationAllowed] = useState<boolean>(false);
    const [state, setState] = useState<Jobs.JobState | null>(null);

    useEffect(() => {
        if (useFakeState) {
            const t = setInterval(() => {
                setState(
                    (window["fakeState"] as JobState | undefined) ??
                    (localStorage.getItem("fakeState") as JobState | null) ??
                    state
                );
            }, 100);

            return () => {
                clearInterval(t);
            };
        } else {
            return () => {
                // Do nothing
            };
        }
    }, [state]);

    useEffect(() => {
        let t1;
        let t2;
        if (job) {
            t1 = setTimeout(() => {
                setDataAnimationAllowed(true);

                // NOTE(Dan): Remove action to avoid getting delay if the user refreshes their browser
                history.replace(buildQueryString(history.location.pathname, {app: appNameHint}));
            }, delayInitialAnim ? 3000 : 400);

            t2 = setTimeout(() => {
                setLogoAnimationAllowed(true);
            }, delayInitialAnim ? 2200 : 0);
        }

        return () => {
            if (t1) clearTimeout(t1);
            if (t2) clearTimeout(t2);
        };
    }, [job]);

    const jobUpdateCallbackHandlers = useRef<JobUpdateListener[]>([]);
    useEffect(() => {
        let lastState = state;
        jobUpdateCallbackHandlers.current = [{
            handler: (e) => {
                if (!e) return;
                if (!useFakeState) {
                    if (e.state != null) {
                        if (e.state !== lastState) {
                            setState(e.state);
                        }
                        lastState = e.state;
                    }
                }
            }
        }];
    }, [id]);
    const jobUpdateListener = useCallback((e: FollowStdStreamResponse) => {
        jobUpdateCallbackHandlers.current.forEach(({handler}) => {
            handler(e);
        });
    }, [id]);
    useJobUpdates(id, jobUpdateListener);

    if (jobFetcher.error !== undefined) {
        return <MainContainer main={<Heading.h2>An error occurred</Heading.h2>}/>;
    }

    return <MainContainer
        main={
            <Container className={state ?? "state-loading"}>
                <div className={`logo-wrapper ${logoAnimationAllowed && state ? "active" : ""}`}>
                    <div className="logo-scale">
                        <div className={"logo"}>
                            <AppToolLogo name={job?.metadata?.name ?? appNameHint} type={"APPLICATION"} size={"200px"}/>
                        </div>
                    </div>
                </div>

                {!job ? null : (
                    <CSSTransition
                        in={state === JobState.IN_QUEUE && dataAnimationAllowed}
                        timeout={{
                            enter: 1000,
                            exit: 0,
                        }}
                        classNames={"data"}
                        unmountOnExit
                    >
                        <div className={"data"}>
                            <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                                <div className={"fake-logo"}/>
                                <div className={"header-text"}>
                                    <InQueueText job={job!}/>
                                </div>
                            </Flex>

                            <Content>
                                <Busy/>
                                <InfoCards job={job!}/>
                            </Content>
                        </div>
                    </CSSTransition>
                )}

                {!job ? null : (
                    <CSSTransition
                        in={state === JobState.RUNNING && dataAnimationAllowed}
                        timeout={{enter: 1000, exit: 0}}
                        classNames={"data"}
                        unmountOnExit
                    >
                        <div className={"data"}>
                            <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                                <div className={"fake-logo"}/>
                                <div className={"header-text"}>
                                    <RunningText job={job!}/>
                                </div>
                            </Flex>

                            <RunningContent job={job} updateListeners={jobUpdateCallbackHandlers}/>
                        </div>
                    </CSSTransition>
                )}

                {!job ? null : (
                    <CSSTransition
                        in={state != null && isJobStateTerminal(state) && dataAnimationAllowed}
                        timeout={{enter: 1000, exit: 0}}
                        classNames={"data"}
                        unmountOnExit
                    >
                        <div className={"data"}>
                            <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                                <div className={"fake-logo"}/>
                                <div className={"header-text"}>
                                    <CompletedText job={job!}/>
                                </div>
                            </Flex>

                            <Content>
                                <OutputFiles job={job}/>
                                <InfoCards job={job!}/>
                            </Content>
                        </div>
                    </CSSTransition>
                )}
            </Container>
        }
    />;
};

const Content = styled.div`
    display: flex;
    align-items: center; 
    flex-direction: column;
`;

const InQueueText: React.FunctionComponent<{ job: Jobs.Job }> = ({job}) => {
    return <>
        <Heading.h2>{PRODUCT_NAME} is preparing your job</Heading.h2>
        <Heading.h3>
            {job.name ?
                (<>
                    We are about to launch <i>{job.metadata.title} v{job.metadata.version}</i>
                    {" "}for <i>{job.name}</i> (ID: {shortUUID(job.jobId)})
                </>) :
                (<>
                    We are about to launch <i>{job.metadata.title} v{job.metadata.version}</i>
                    {" "}(ID: {shortUUID(job.jobId)})
                </>)
            }
        </Heading.h3>
    </>;
};


const BusyWrapper = styled(Box)`
    display: none;
    
    &.active {
        animation: 1s ${busyAnim};
        display: block;
    }
`;

const Busy: React.FunctionComponent = () => {
    const clusterUtilization = 90;
    const numberOfJobs = 50;
    const numberOfJobsInQueue = 10;
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const t = setTimeout(() => {
            ref.current?.classList.add("active");
        }, 6000);
        return () => {
            clearTimeout(t);
        };
    }, []);

    return <BusyWrapper ref={ref}>
        <Flex my={"16px"} flexDirection={"column"}>
            <Box textAlign={"center"} mb={"16px"}>
                Your reserved machine is currently quite popular. <br/>
                Cluster utilization is currently at {clusterUtilization}% with {numberOfJobs} jobs running
                and {numberOfJobsInQueue} in the queue.
            </Box>

            <Button color={"red"} type={"button"}>Cancel reservation</Button>
        </Flex>
    </BusyWrapper>;
};

const InfoCardsContainer = styled.div`
    margin-top: 32px;
    display: grid;
    width: 100%;
    grid-template-columns: repeat(auto-fit, minmax(200px, 380px));
    grid-gap: 16px;
    justify-content: center;
`;

const InfoCards: React.FunctionComponent<{ job: Jobs.Job }> = ({job}) => {
    return <InfoCardsContainer>
        <InfoCard stat={"10"} statTitle={"Parallel jobs"} icon={"cpu"}>
            <b>u1-standard-64</b><br/>
            64x vCPU &mdash; 123GB RAM &mdash; 2x GPU
        </InfoCard>
        <InfoCard stat={"24 hours"} statTitle={"Allocated"} icon={"hourglass"}>
            <b>Estimated price:</b> 240 DKK <br/>
            <b>Price per hour:</b> 10 DKK
        </InfoCard>
        <InfoCard stat={"10"} statTitle={"Input files"} icon={"ftFolder"}>
            Code/ucloud, Config/myconfig.json, Data/Dog Pictures <i>(and more)</i>
        </InfoCard>
        <InfoCard stat={"My Workspace"} statTitle={"Project"} icon={"projects"}>
            <b>Launched by:</b> DanSebastianThrane#1234
        </InfoCard>
    </InfoCardsContainer>;
};

const InfoCardContainer = styled.div`
    margin: 15px 10px;
    text-align: center;
    
    .stat {
        font-size: 250%;
        line-height: 150%;
    }
    
    .stat-title {
        font-size: 150%;
    }
    
    .content {
        margin-top: 30px;
        text-align: left;
    }
`;

const InfoCard: React.FunctionComponent<{
    stat: string,
    statTitle: string,
    icon: IconName,
}> = props => {
    return <DashboardCard color={"purple"} isLoading={false}>
        <InfoCardContainer>
            <Icon name={props.icon} size={"60px"} color={"iconColor"} color2={"iconColor2"}/>
            <div className={"stat"}>{props.stat}</div>
            <div className={"stat-title"}>{props.statTitle}</div>
            <div className={"content"}>
                {props.children}
            </div>
        </InfoCardContainer>
    </DashboardCard>;
};

const RunningText: React.FunctionComponent<{ job: Jobs.Job }> = ({job}) => {
    return <>
        <Heading.h2>
            <i>{job.metadata.title} v{job.metadata.version}</i> is now running
        </Heading.h2>
        <Heading.h3>You can follow the progress below{!job.name ? null : (<> of <i>{job.name}</i></>)}</Heading.h3>
    </>;
};

const RunningInfoWrapper = styled.div`
    margin-top: 32px;
    display: grid;
    width: 100%;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    grid-gap: 16px;
    justify-content: center;   
`;

const AltButtonGroup = styled.div<{ minButtonWidth: string }>`
    display: grid;
    width: 100%;
    grid-template-columns: repeat(auto-fit, minmax(${props => props.minButtonWidth}, max-content));
    grid-gap: 8px;
    margin-top: 8px;
    margin-bottom: 8px;
`;

const RunningContent: React.FunctionComponent<{
    job: Jobs.Job,
    updateListeners: React.RefObject<JobUpdateListener[]>
}> = ({job, updateListeners}) => {
    return <>
        <RunningInfoWrapper>
            <DashboardCard color={"purple"} isLoading={false} title={"Job info"} icon={"hourglass"}>
                {!job.name ? null : <><b>Name:</b> {job.name}<br/></>}
                <b>ID:</b> {shortUUID(job.jobId)}<br/>
                <b>Reservation:</b> u1-standard-64 (x10)<br/>
                <b>Input:</b> Code/ucloud, Config/myconfig.json, Data/Dog pictures<br/>
                <b>Project:</b> My workspace<br/>
            </DashboardCard>
            <DashboardCard color={"purple"} isLoading={false} title={"Time allocation"} icon={"hourglass"}>
                <b>Job start: </b> 12:34 1/1/2020 <br/>
                <b>Job expiry: </b> 12:34 2/1/2020 <br/> <br/>

                Extend allocation (hours):
                <AltButtonGroup minButtonWidth={"50px"}>
                    <Button>+1</Button>
                    <Button>+6</Button>
                    <Button>+12</Button>
                    <Button>+24</Button>
                    <Button>+48</Button>
                </AltButtonGroup>
            </DashboardCard>
        </RunningInfoWrapper>

        <RunningJobsWrapper>
            {Array(10).fill(0).map((_, i) => {
                return <RunningJobRank key={i} job={job} rank={i} updateListeners={updateListeners}/>;
            })}
        </RunningJobsWrapper>
    </>;
};

const RunningJobsWrapper = styled.div`
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    margin-top: 32px;
    
    margin-bottom: 32px;
    grid-gap: 32px;
`;

const RunningJobRankWrapper = styled.div`
    margin-top: 16px;
    margin-bottom: 16px;
    
    display: grid;
    grid-template-columns: 80px 1fr 200px;
    grid-template-rows: 1fr auto;
    grid-gap: 16px;

    .rank {
        text-align: center;
        width: 100%;
        flex-direction: column;
    }
    
    .term {
        height: 100%;
    }
    
    .term .terminal {
        /* NOTE(Dan): This fixes a feedback loop in the xtermjs fit function. Without this, the fit function is
           unable to correctly determine the size of the terminal */
        position: absolute;
    }
    
    .buttons {
        display: flex;
        flex-direction: column;
    }
    
    .buttons ${Button} {
        margin-top: 8px;
        width: 100%;
    }
    
    ${deviceBreakpoint({minWidth: "1001px"})} {
        &.expanded {
            height: 80vh;
        }
        
        &.expanded .term {
            grid-row: 1;
            grid-column: 2 / 4;
        }
        
        &.expanded .buttons {
            grid-row: 2;
            grid-column: 2 / 4;
        }
    }
    
    ${deviceBreakpoint({maxWidth: "1000px"})} {
        grid-template-columns: 1fr !important;
        
        .term {
            height: 400px;
        }
        
        .expand-btn {
            display: none;
        }
    }
`;

const RunningJobRank: React.FunctionComponent<{
    job: Jobs.Job,
    rank: number,
    updateListeners: React.RefObject<JobUpdateListener[]>,
}> = ({job, rank, updateListeners}) => {
    const {termRef, terminal, fitAddon} = useXTerm({autofit: true});
    const [expanded, setExpanded] = useState(false);
    const toggleExpand = useCallback(() => {
        setExpanded(!expanded);
        const targetView = termRef.current?.parentElement;
        if (targetView != null) {
            setTimeout(() => {
                fitAddon.fit();
                window.scrollTo({
                    top: targetView.getBoundingClientRect().top - 100 + window.pageYOffset,
                    behavior: "smooth"
                });
            }, 0);
        }
    }, [expanded, termRef]);

    useEffect(() => {
        updateListeners.current?.push({
            handler: e => {
                if (e.rank === rank && e.stderr !== null) {
                    appendToXterm(terminal, e.stderr);
                }

                if (e.rank === rank && e.stdout !== null) {
                    appendToXterm(terminal, e.stdout);
                }
            }
        });

        // NOTE(Dan): Clean up is performed by the parent object
    }, [job.jobId, rank]);

    return <>
        <DashboardCard color={"purple"} isLoading={false}>
            <RunningJobRankWrapper className={expanded ? "expanded" : undefined}>
                <div className="rank">
                    <Heading.h2>{rank + 1}</Heading.h2>
                    <Heading.h3>Rank</Heading.h3>
                </div>

                <div className={"term"} ref={termRef}/>

                <div className="buttons">
                    <Link to={`/applications/shell/${job.jobId}/${rank}?hide-frame`} onClick={e => {
                        e.preventDefault();

                        window.open(
                            ((e.target as HTMLDivElement).parentElement as HTMLAnchorElement).href,
                            `shell-${job.jobId}-${rank}`,
                            "width=800,height=600,status=no"
                        );
                    }}>
                        <Button type={"button"}>
                            <b>&gt;_{"  "}</b> Open terminal
                        </Button>
                    </Link>
                    <Button>Open interface</Button>
                    <Button className={"expand-btn"} onClick={toggleExpand}>
                        {expanded ? "Shrink" : "Expand"} output
                    </Button>
                </div>
            </RunningJobRankWrapper>
        </DashboardCard>
    </>;
};

const CompletedTextWrapper = styled.div`
    ${deviceBreakpoint({maxWidth: "1000px"})} {
        ${AltButtonGroup} {
            justify-content: center;
        }
    }
`;

const CompletedText: React.FunctionComponent<{ job: Jobs.Job }> = ({job}) => {
    const success = job.state === JobState.SUCCESS;
    return <CompletedTextWrapper>
        <Heading.h2>{PRODUCT_NAME} has processed your job</Heading.h2>
        <Heading.h3>
            <i>{job.metadata.title} v{job.metadata.version}</i>
            {" "}{success ? "succeeded" : "failed"}{" "}
            {job.name ? <>for <i>{job.name}</i></> : null} (ID: {shortUUID(job.jobId)})
        </Heading.h3>
        <AltButtonGroup minButtonWidth={"200px"}>
            <Button>Restart application</Button>
            <Button>Do something else 2</Button>
        </AltButtonGroup>
    </CompletedTextWrapper>;
};

const OutputFilesWrapper = styled.div`
    display: flex;
    flex-direction: column;
    width: 100%;
    
    h1, h2, h3, h4 {
        margin-top: 15px;
        margin-bottom: 15px;
    }
`;

const OutputFiles: React.FunctionComponent<{ job: Jobs.Job }> = ({job}) => {
    const history = useHistory();
    return <OutputFilesWrapper>
        <Heading.h3>Files</Heading.h3>
        <VirtualFileTable
            embedded
            disableNavigationButtons
            previewEnabled
            permissionAlertEnabled={false}
            onFileNavigation={f => history.push(fileTablePage(f))}
            page={arrayToPage([
                mockFile({path: Client.homeFolder + "/Code", type: "DIRECTORY"}),
                mockFile({path: Client.homeFolder + "/Config", type: "DIRECTORY"}),
                mockFile({path: Client.homeFolder + "/Dog pictures", type: "DIRECTORY"}),
                mockFile({path: Client.homeFolder + "/Output", type: "DIRECTORY"}),
            ])}
        />
    </OutputFilesWrapper>;
};
