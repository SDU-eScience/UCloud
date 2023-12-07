import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {useLocation, useNavigate, useParams} from "react-router";
import {MainContainer} from "@/MainContainer/MainContainer";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {isJobStateTerminal, JobState, stateToTitle} from "./index";
import * as Heading from "@/ui-components/Heading";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {displayErrorMessageOrDefault, shortUUID, timestampUnixMs, useEffectSkipMount} from "@/UtilityFunctions";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {Box, Button, ExternalLink, Flex, Icon, Link, Text, Truncate} from "@/ui-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {IconName} from "@/ui-components/Icon";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {device, deviceBreakpoint} from "@/ui-components/Hide";
import {CSSTransition} from "react-transition-group";
import {appendToXterm, useXTerm} from "@/Applications/Jobs/xterm";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {dateToString, dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import {MarginProps} from "styled-system";
import {useProject} from "@/Project/cache";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {bulkRequestOf, pageV2Of} from "@/DefaultObjects";
import {
    api as JobsApi,
    Job,
    JobUpdate,
    JobStatus,
    ComputeSupport,
    JobSpecification,
    DockerSupport,
    NativeSupport,
    VirtualMachineSupport
} from "@/UCloud/JobsApi";
import {compute} from "@/UCloud";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import AppParameterValueNS = compute.AppParameterValueNS;
import {costOfDuration, ProductCompute, usageExplainer} from "@/Accounting";
import {prettyFilePath} from "@/Files/FilePath";
import PublicLinkApi, {PublicLink} from "@/UCloud/PublicLinkApi";
import {SillyParser} from "@/Utilities/SillyParser";
import Warning from "@/ui-components/Warning";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {classConcat, injectStyle, injectStyleSimple, makeKeyframe, unbox} from "@/Unstyled";
import {ButtonClass} from "@/ui-components/Button";
import FileBrowse from "@/Files/FileBrowse";
import {projectTitleFromCache} from "@/Project/ContextSwitcher";
import {sidebarJobCache} from "@/ui-components/Sidebar";

const enterAnimation = makeKeyframe("enter-animation", `
  from {
    transform: scale3d(1, 1, 1);
  }
  50% {
    transform: scale3d(1.05, 1.05, 1.05);
  }
  to {
    transform: scale3d(1, 1, 1);
  }
`);

const busyAnim = makeKeyframe("busy-anim", `
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
`);

const zoomInAnim = makeKeyframe("zoom-in-anim", `
  from {
    opacity: 0;
    transform: scale3d(0.3, 0.3, 0.3);
  }
  50% {
    opacity: 1;
  }
`);

const Container = injectStyle("container", k => `
    ${k} {
        --logoScale: 1;
        --logoBaseSize: 200px;
        --logoSize: calc(var(--logoBaseSize) * var(--logoScale));
        margin: 50px; /* when header is not wrapped this should be equal to logoPX and logoPY */
        max-width: 2200px;
    }


    ${device("xs")} {
        ${k} {
            margin-left: 0;
            margin-right: 0;
        }
    }

    ${k} {
        display: flex;
        flex-direction: column;
        position: relative;
    }

  ${k} > .logo-wrapper {
    position: absolute;
    left: 0;
    top: 0;
    animation: 800ms ${zoomInAnim};
  }

  ${k} > .logo-wrapper.active {
    transition: scale 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
  }

  ${k} > .logo-wrapper.active > .logo-scale {
    transition: transform 300ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
    transform: scale(var(--logoScale));
    transform-origin: top left;
  }

  ${k} .fake-logo {
    /* NOTE(Dan): the fake logo takes the same amount of space as the actual logo, 
    this basically fixes our document flow */
    display: block;
    width: var(--logoSize);
    height: var(--logoSize);
    content: '';
  }

  ${k} .data.data-enter-done {
  opacity: 1;
    transform: translate3d(0, 0, 0);
  }

  ${k} .data.data-enter-active {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    transition: transform 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
  }

  ${k} .data.data-exit {
    opacity: 1;
  }

  ${k} .data-exit-active {
    display: none;
  }

  ${k} .data {
    width: 100%; /* fix info card width */
    opacity: 0;
    transform: translate3d(0, 50vh, 0);
  }

  ${k} .header-text {
    margin-left: 32px;
    margin-top: calc(var(--logoScale) * 16px);
    width: calc(100% - var(--logoBaseSize) * var(--logoScale) - 32px);
  }

  ${deviceBreakpoint({maxWidth: "1000px"})} {
    ${k} .fake-logo {
      width: 100%; /* force the header to wrap */
    }

    ${k} .logo-wrapper {
      left: calc(50% - var(--logoSize) / 2);
    }

    ${k} .header {
      text-align: center;
    }

    ${k} .header-text {
      margin-left: 0;
      margin-top: 0;
      width: 100%;
    }
  }

  ${k}.IN_QUEUE .logo {
    animation: 2s ${enterAnimation} infinite;
  }

  ${k}.RUNNING {
    --logoScale: 0.5;
  }

  ${k} .top-buttons {
    display: flex;
    gap: 8px;
  }
`);

// NOTE(Dan): WS calls don't currently have their types generated
interface JobsFollowResponse {
    updates: compute.JobUpdate[];
    log: LogMessage[];
    newStatus?: JobStatus;
}

interface LogMessage {
    rank: number;
    stdout?: string;
    stderr?: string;
}

function useJobUpdates(job: Job | undefined, callback: (entry: JobsFollowResponse) => void): void {
    useEffect(() => {
        if (!job) return;

        const conn = WSFactory.open(
            "/jobs", {
            init: async conn => {
                await conn.subscribe({
                    call: "jobs.follow",
                    payload: {id: job.id},
                    handler: message => {
                        const streamEntry = message.payload as JobsFollowResponse;
                        callback(streamEntry);
                    }
                });
                conn.close();
            },
        });

        return () => {
            conn.close();
        };
    }, [job, callback]);
}

interface JobUpdates {
    updateQueue: compute.JobUpdate[];
    logQueue: LogMessage[];
    statusQueue: JobStatus[];

    subscriptions: (() => void)[];
}

function getBackend(job?: Job): string {
    return job?.status.resolvedApplication?.invocation.tool.tool?.description.backend ?? "";
}

export function View(props: {id?: string; embedded?: boolean;}): JSX.Element {
    const id = props.id ?? useParams<{id: string}>().id!;

    // Note: This might not match the real app name
    const location = useLocation();
    const navigate = useNavigate();
    const appNameHint = getQueryParamOrElse(location.search, "app", "");
    const action = getQueryParamOrElse(location.search, "action", "view");
    const delayInitialAnim = action === "start";
    const [jobFetcher, fetchJob] = useCloudAPI<Job | undefined>({noop: true}, undefined);
    const job = jobFetcher.data;

    const useFakeState = useMemo(() => localStorage.getItem("useFakeState") !== null, []);

    if (!props.embedded) {
        useTitle(`Job ${shortUUID(id)}`);
    }

    useEffect(() => {
        fetchJob(JobsApi.retrieve({
            id,
            includeApplication: true,
            includeUpdates: true,
            includeSupport: true,
        }));
    }, [id]);

    const [dataAnimationAllowed, setDataAnimationAllowed] = useState<boolean>(false);
    const [logoAnimationAllowed, setLogoAnimationAllowed] = useState<boolean>(false);
    const [status, setStatus] = useState<JobStatus | null>(null);

    useEffect(() => {
        if (useFakeState) {
            const t = setInterval(() => {
                const jobState = (window["fakeState"] as JobState | undefined) ??
                    (localStorage.getItem("fakeState") as JobState | null) ??
                    status?.state;

                if (jobState) {
                    setStatus({
                        state: jobState,
                        startedAt: timestampUnixMs(),
                        jobParametersJson: {}
                    });
                }
            }, 100);

            return () => {
                clearInterval(t);
            };
        } else {
            return () => {
                // Do nothing
            };
        }
    }, [status]);

    useEffect(() => {

        let t1: number | undefined;
        let t2: number | undefined;
        if (job) {
            t1 = window.setTimeout(() => {
                setDataAnimationAllowed(true);

                // NOTE(Dan): Remove action to avoid getting delay if the user refreshes their browser
                if (!props.embedded) {
                    navigate(buildQueryString(location.pathname, {app: appNameHint}), {replace: true});
                }
            }, delayInitialAnim ? 3000 : 400);

            if (!props.embedded) {
                t2 = window.setTimeout(() => {
                    setLogoAnimationAllowed(true);
                }, delayInitialAnim ? 2200 : 0);
            }
        }

        return () => {
            if (t1 != undefined) clearTimeout(t1);
            if (t2 != undefined) clearTimeout(t2);
        };
    }, [job, props.embedded]);

    useEffect(() => {
        /* NOTE(jonas): Attempt to fix not transitioning to the initial state */
        if (job?.status != null) setStatus(s => s ?? job.status);
    }, [job]);

    useEffect(() => {
        // Used to fetch creditsCharged when job finishes.
        if (isJobStateTerminal(status?.state ?? "RUNNING") && job?.status.state !== status?.state) {
            fetchJob(JobsApi.retrieve({
                id,
                includeApplication: true,
                includeUpdates: true
            }));
        }
    }, [status?.state])

    const jobUpdateState = useRef<JobUpdates>({updateQueue: [], logQueue: [], statusQueue: [], subscriptions: []});
    useEffect(() => {
        jobUpdateState.current.subscriptions.push(() => {
            const s = jobUpdateState.current;
            while (true) {
                const e = s.statusQueue.pop();
                if (e === undefined) break;

                if (!useFakeState) {
                    setStatus(e);
                } else {
                    console.log("Wanted to switch status, but didn't. " +
                        "Remove localStorage useFakeState if you wish to use real status.");
                }
            }
        });
    }, [id]);

    const jobUpdateListener = useCallback((e: JobsFollowResponse) => {
        if (!e) return;
        const s = jobUpdateState.current;

        if (e.log) {
            for (const msg of e.log) {
                s.logQueue.push(msg);
            }
        }

        if (e.updates) {
            for (const update of e.updates) {
                s.updateQueue.push(update);
                job?.updates?.push(update);

                if (job && update.state && (update.state === "RUNNING" || isJobStateTerminal(update.state))) {
                    const j = {...job};
                    j.status.state = update.state;
                    sidebarJobCache.updateCache(pageV2Of(j));
                }
            }
        }

        if (e.newStatus) {
            s.statusQueue.push(e.newStatus);
        }

        s.subscriptions.forEach(it => it());
    }, [job]);

    useJobUpdates(job, jobUpdateListener);

    if (jobFetcher.error !== undefined) {
        return <MainContainer main={<Heading.h2>An error occurred</Heading.h2>} />;
    }

    const main = (
        <div className={classConcat(Container, status?.state ?? "state-loading")}>
            <div className={`logo-wrapper ${logoAnimationAllowed && status ? "active" : ""}`}>
                <div className="logo-scale">
                    <div className="logo">
                        <AppToolLogo name={job?.specification?.application?.name ?? appNameHint}
                            type={"APPLICATION"}
                            size={"200px"} />
                    </div>
                </div>
            </div>
            {!job || !status ? null : (
                <CSSTransition
                    in={(status?.state === "IN_QUEUE" || status?.state === "SUSPENDED") && dataAnimationAllowed}
                    timeout={{
                        enter: 1000,
                        exit: 0,
                    }}
                    classNames={"data"}
                    unmountOnExit
                >
                    <div className={"data"}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                            <div className={"fake-logo"} />
                            <div className={"header-text"}>
                                {status?.state === "IN_QUEUE" ? <InQueueText job={job} /> : null}
                            </div>
                        </Flex>

                        <div className={Content}>
                            <Box width={"100%"} maxWidth={"1572px"} margin={"32px auto"}>
                                <HighlightedCard>
                                    <Box py={"16px"}>
                                        <ProviderUpdates job={job} state={jobUpdateState} />
                                    </Box>
                                </HighlightedCard>
                            </Box>
                            <InfoCards job={job} status={status} />
                        </div>
                    </div>
                </CSSTransition>
            )}

            {!job || !status ? null : (
                <CSSTransition
                    in={status?.state === "RUNNING" && dataAnimationAllowed}
                    timeout={{enter: 1000, exit: 0}}
                    classNames={"data"}
                    unmountOnExit
                >
                    <div className={"data"}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                            <div className={"fake-logo"} />
                            <div className={"header-text"}>
                                <RunningText job={job} />
                            </div>
                        </Flex>

                        <RunningContent
                            job={job}
                            state={jobUpdateState}
                            status={status}
                        />
                    </div>
                </CSSTransition>
            )}


            {!job || !status ? null : (
                <CSSTransition
                    in={isJobStateTerminal(status.state) && dataAnimationAllowed}
                    timeout={{enter: 1000, exit: 0}}
                    classNames={"data"}
                    unmountOnExit
                >
                    <div className={"data"}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                            <div className={"fake-logo"} />
                            <div className={"header-text"}>
                                <CompletedText job={job} state={status.state} />
                            </div>
                        </Flex>

                        <CompletedContent job={job} state={jobUpdateState} />
                    </div>
                </CSSTransition>
            )}
            {status && isJobStateTerminal(status.state) && job ? <OutputFiles job={job} /> : null}
        </div>
    );

    if (props.embedded) {
        return main;
    }
    return <MainContainer
        main={main}
    />;
}

const CompletedContent: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
}> = ({job, state}) => {
    const project = useProject();
    const workspaceTitle = Client.hasActiveProject ? project.fetch().id === job?.owner?.project ? project.fetch().specification.title :
        "My Workspace" : "My Workspace";

    const fileInfo = useJobFiles(job.specification);

    return <div className={Content}>
        <div className={RunningInfoWrapper}>
            <HighlightedCard isLoading={false} title={"Job info"} icon={"properties"}>
                <Flex flexDirection={"column"} height={"calc(100% - 57px)"}>
                    {!job.specification.name ? null : <Box><b>Name:</b> {job.specification.name}</Box>}
                    <Box><b>ID:</b> {shortUUID(job.id)}</Box>
                    <Box>
                        <b>Reservation:</b>{" "}
                        <ProviderTitle providerId={job.specification.product.provider} />
                        {" "}/{" "}
                        {job.specification.product.id}{" "}
                        (x{job.specification.replicas})
                    </Box>
                    <Box><b>Input:</b> {fileInfo}</Box>
                    <Box><b>Launched by:</b> {job.owner.createdBy} in {workspaceTitle}</Box>
                </Flex>
            </HighlightedCard>

            <HighlightedCard isLoading={false} title="Messages" icon="chat">
                <ProviderUpdates job={job} state={state} />
            </HighlightedCard>
        </div>
    </div>
};

const Content = injectStyleSimple("content", `
  display: flex;
  align-items: center;
  flex-direction: column;
`);

function PublicLinkEntry({id}: {id: string}): JSX.Element {
    const [publicLink] = useCloudAPI<PublicLink | null>(PublicLinkApi.retrieve({id}), null);
    if (publicLink.data == null) return <div />
    const {domain} = publicLink.data.specification;

    const httpDomain = domain.startsWith("https://") ? domain : "https://" + domain;
    return <Truncate width={1}>
        <ExternalLink title={domain} href={httpDomain}>{domain}</ExternalLink>
    </Truncate>
}

const InQueueText: React.FunctionComponent<{job: Job}> = ({job}) => {
    const [utilization, setUtilization] = useCloudAPI<compute.JobsRetrieveUtilizationResponse | null>(
        {noop: true},
        null
    );

    useEffect(() => {
        const support = job.status.resolvedSupport?.support as ComputeSupport;
        const appType = getAppType(job);
        if (
            (appType === "DOCKER" && support.docker.utilization === true) ||
            (appType === "VIRTUAL_MACHINE" && support.virtualMachine.utilization === true)
        ) {
            setUtilization(JobsApi.retrieveUtilization({jobId: job.id}))
        }
    }, [job]);

    return <>
        <Heading.h2>Your job is being prepared</Heading.h2>
        <Heading.h3>
            {job.specification.name ?
                (<>
                    Starting {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                    {" "}for <i>{job.specification.name}</i> (ID: {shortUUID(job.id)})
                </>) :
                (<>
                    Starting {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                    {" "}(ID: {shortUUID(job.id)})
                </>)
            }
        </Heading.h3>
        <Busy job={job} utilization={utilization.data} />
    </>;
};

const BusyWrapper = injectStyle("busy-wrapper", k => `
    ${k} {
        display: none;
    }

    ${k}.active {
        animation: 1s ${busyAnim};
        display: block;
    }
`);

const Busy: React.FunctionComponent<{
    job: Job;
    utilization: compute.JobsRetrieveUtilizationResponse | null
}> = ({job, utilization}) => {
    const ref = useRef<HTMLDivElement>(null);
    const clusterUtilization = utilization ?
        Math.floor((utilization.usedCapacity.cpu / utilization.capacity.cpu) * 100) : 0;

    useEffect(() => {
        const t = setTimeout(() => {
            ref.current?.classList.add("active");
        }, 6000);
        return () => {
            clearTimeout(t);
        };
    }, []);

    return <Box className={BusyWrapper} divRef={ref}>
        <Box mt={"16px"}>
            <Box mb={"16px"}>
                {clusterUtilization > 80 ? (
                    <>
                        Due to high resource utilization, it might take longer than normal to prepare the machine you
                        requested.<br />
                        {utilization ? (
                            <>
                                Cluster utilization is currently at {clusterUtilization}%
                                with {utilization.queueStatus.running} jobs
                                running and {utilization.queueStatus.pending} in the queue.
                            </>
                        ) : null}
                    </>
                ) : (
                    <>We are currently preparing your job. This step might take a few
                        minutes.</>
                )}
            </Box>

            <CancelButton job={job} state={"IN_QUEUE"} />
        </Box>
    </Box>;
};

const InfoCardsContainer = injectStyleSimple("info-card-container", `
  display: grid;
  width: 100%;
  grid-template-columns: repeat(auto-fit, minmax(200px, 380px));
  grid-gap: 16px;
  justify-content: center;
`);

function isSupported(jobBackend: string | undefined, support: ComputeSupport | undefined, flag: keyof DockerSupport | keyof NativeSupport | keyof VirtualMachineSupport): boolean {
    switch (jobBackend) {
        case "DOCKER":
            return support?.docker[flag] === true;
        case "VIRTUAL_MACHINE":
            return support?.virtualMachine[flag] === true;
        case "NATIVE":
            return support?.native[flag] === true;
        default: {
            console.log(`Unhandled job backend: ${jobBackend}`)
            return false;
        }
    }
}

const InfoCards: React.FunctionComponent<{job: Job, status: JobStatus}> = ({job, status}) => {
    const fileInfo = useJobFiles(job.specification);
    let time = job.specification.timeAllocation;
    if (status.expiresAt && status.startedAt) {
        const msTime = status.expiresAt - status.startedAt;
        time = {
            hours: Math.floor(msTime / (1000 * 3600)),
            minutes: Math.floor((msTime % (1000 * 3600)) / (1000 * 60)),
            seconds: Math.floor((msTime % (1000 * 60)) / (1000))
        };
    }

    let prettyTime = "No job deadline";
    if (time) {
        prettyTime = "";
        if (time.hours > 0) {
            prettyTime += time.hours;
            if (time.hours > 1) prettyTime += " hours";
            else prettyTime += " hour";
        }
        if (time.minutes > 0) {
            if (prettyTime !== "") prettyTime += " ";
            prettyTime += time.minutes;
            if (time.minutes > 1) prettyTime += " minutes";
            else prettyTime += " minute";
        }

        if (prettyTime === "") {
            prettyTime = "< 1 minute";
        }
    }

    const workspaceTitle = projectTitleFromCache(job.owner.project);

    const machine = job.status.resolvedProduct! as unknown as ProductCompute;
    const pricePerUnit = machine?.pricePerUnit ?? 0;
    const estimatedCost = time ?
        (time.hours * 60 * pricePerUnit + (time.minutes * pricePerUnit)) * job.specification.replicas :
        0;

    return <div className={InfoCardsContainer}>
        <InfoCard
            stat={job.specification.replicas.toString()}
            statTitle={job.specification.replicas === 1 ? "Node" : "Nodes"}
            icon={"cpu"}
        >
            <b><ProviderTitle providerId={job.specification.product.provider} /> / {job.specification.product.id}
            </b><br />
            {!machine?.cpu ? null : <>{machine?.cpu}x vCPU </>}

            {machine?.cpu && (machine.memoryInGigs || machine.gpu) ? <>&mdash;</> : null}
            {!machine?.memoryInGigs ? null : <>{machine?.memoryInGigs}GB RAM</>}

            {machine?.cpu && machine.gpu ? <>&mdash;</> : null}
            {!machine?.gpu ? null : <>{" "}{machine?.gpu}x GPU</>}
        </InfoCard>
        {job.status.resolvedApplication?.invocation?.tool?.tool?.description?.backend === "VIRTUAL_MACHINE" ?
            null :
            <InfoCard
                stat={prettyTime}
                statTitle={"Allocated"}
                icon={"hourglass"}
            >
                {!isJobStateTerminal(status?.state) ? (<>
                    {!time ? null : <>
                        <b>Estimated price:</b>{" "}
                        {usageExplainer(estimatedCost, machine.productType, machine.chargeType, machine.unitOfPrice)}
                        <br />
                    </>}
                    <b>Price per hour:</b>{" "}
                    {job.status.resolvedSupport?.product.freeToUse ? "Free" :
                        job.status.resolvedProduct ?
                            usageExplainer(
                                costOfDuration(60, job.specification.replicas, machine),
                                "COMPUTE",
                                machine.chargeType,
                                machine.unitOfPrice
                            )
                            : "Unknown"
                    }
                </>) : null}
            </InfoCard>
        }
        <InfoCard
            stat={Object.keys(jobFiles(job.specification)).length.toString()}
            statTitle={Object.keys(jobFiles(job.specification)).length === 1 ? "Input file" : "Input files"}
            icon={"ftFolder"}
        >
            {fileInfo}
        </InfoCard>
        <InfoCard stat={workspaceTitle} statTitle={"Project"} icon={"projects"}>
            <b>Launched by:</b> {job.owner.createdBy}
        </InfoCard>
    </div>;
};

const InfoCardContainer = injectStyle("info-card-container", k => `
    ${k} {
        margin: 15px 10px;
        text-align: center;
    }

    ${k} .stat {
        font-size: 250%;
        line-height: 150%;
    }

    ${k} .stat-title {
        font-size: 150%;
    }

    ${k} .content {
        margin-top: 30px;
        text-align: left;
    }
`);

const InfoCard: React.FunctionComponent<{
    stat: string,
    statTitle: string,
    icon: IconName,
    children: React.ReactNode;
}> = props => {
    return <HighlightedCard isLoading={false}>
        <div className={InfoCardContainer}>
            <Icon name={props.icon} size={"60px"} color={"iconColor"} color2={"iconColor2"} />
            <div className={"stat"}>{props.stat}</div>
            <div className={"stat-title"}>{props.statTitle}</div>
            <div className={"content"}>
                {props.children}
            </div>
        </div>
    </HighlightedCard>;
};

const RunningText: React.FunctionComponent<{job: Job}> = ({job}) => {
    return <>
        <Flex justifyContent={"space-between"}>
            <Box>
                <Heading.h2>
                    {!job.specification.name ? "Your job" : (<><i>{job.specification.name}</i></>)} is now running
                </Heading.h2>
                <Heading.h3>
                    {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name}
                    {" "}{job.specification.application.version}
                </Heading.h3>
            </Box>
            {job.specification.replicas > 1 ? null : (
                <RunningButtonGroup job={job} rank={0} />
            )}
        </Flex>
    </>;
};

const RunningInfoWrapper = injectStyleSimple("running-info-wrapper", `
  margin-top: 32px;
  display: grid;
  width: 100%;
  grid-template-columns: repeat(auto-fit, minmax(580px, 1fr));
  grid-gap: 16px;
  justify-content: center;
`);

function AltButtonGroup(props: React.PropsWithChildren<{minButtonWidth: string} & MarginProps>) {
    return <div
        style={{
            ...unbox(props),
            display: "flex",
            gap: "8px"
        }}
    >
        {props.children}
    </div>
}

const AltButtonGroupClass = injectStyleSimple("alt-button-group", `
  display: grid;
  width: 100%;
  grid-gap: 8px;
`);

AltButtonGroup.defaultProps = {
    marginTop: "8px",
    marginBottom: "8px"
};

function jobFiles(parameters: JobSpecification): Record<string, AppParameterValueNS.File> {
    const result: Record<string, AppParameterValueNS.File> = {};
    const userParams = parameters.parameters ?? {};

    for (const paramName of Object.keys(parameters.parameters ?? {})) {
        const value = userParams[paramName];
        if (value.type !== "file") continue;
        result[paramName] = value as AppParameterValueNS.File;
    }

    let i = 0;
    const randomString = Math.random().toString();
    for (const resource of parameters.resources ?? []) {
        if (resource.type !== "file") continue;
        result[`${randomString}_resourceParam${i++}`] = resource as AppParameterValueNS.File;
    }

    return result;
}

function useJobFiles(spec: JobSpecification): string {
    const [fileInfo, setFileInfo] = useState("No files");
    useEffect(() => {
        let cancelled = false;

        (async () => {
            const files = Object.values(jobFiles(spec));
            if (files.length === 0) {
                setFileInfo("No files");
                return;
            }

            const result = await Promise.all(files.map(it => prettyFilePath(it.path)));
            if (cancelled) return;
            setFileInfo(result.join(", "));
        })();

        return () => {
            cancelled = true;
        };
    }, [spec]);

    return fileInfo;
}

interface ParsedSshAccess {
    success: boolean;
    command: string | null;
    message: string | null;
    aauLegacy: boolean;
}

function parseUpdatesForAccess(updates: JobUpdate[]): ParsedSshAccess | null {
    for (let i = updates.length - 1; i >= 0; i--) {
        const status = updates[i].status;
        if (!status) continue;

        // NOTE(Dan): we need to split on the lines to deal with AAU OpenStack provider.
        const messages = status.split("\n");
        for (const message of messages) {
            const aauPrefix = "SSH Access: ";
            if (message.startsWith(aauPrefix)) {
                // Legacy SSH access message from AAU OpenStack provider
                return {
                    success: true,
                    command: message.substring(aauPrefix.length),
                    message: null,
                    aauLegacy: true,
                };
            } else if (message.startsWith("SSH:")) {
                // Standardized SSH access update
                const parser = new SillyParser(message);
                parser.consumeToken("SSH:");

                let peek: string;
                let success = false;
                let command: string | null = null;
                let sshMessage: string | null = null;

                const status = parser.consumeWord();
                if (status === "Connected!") {
                    success = true;
                } else if (status === "Failure!") {
                    success = false;
                } else {
                    continue;
                }

                if (success) {
                    peek = parser.peekWord();
                    if (peek === "Available") {
                        parser.consumeToken("Available");
                        parser.consumeToken("at:");
                        parser.consumeWhitespace();
                        command = parser.remaining();
                    }
                }

                return {success, command, message: sshMessage, aauLegacy: false};
            }
        }
    }
    return null;
}

const RunningContent: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
    status: JobStatus;
}> = ({job, state, status}) => {
    const fileInfo = useJobFiles(job.specification);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [expiresAt, setExpiresAt] = useState(status.expiresAt);
    const backendType = getBackend(job);
    const [suspended, setSuspended] = useState(job.status.state === "SUSPENDED");
    const workspaceTitle = projectTitleFromCache(job.owner.project);
    const extendJob = useCallback(async (duration: number) => {
        if (!commandLoading && expiresAt) {
            setExpiresAt(expiresAt + (3600 * 1000 * duration));
            try {
                await invokeCommand(JobsApi.extend(bulkRequestOf({
                    jobId: job.id,
                    requestedTime: {hours: duration, minutes: 0, seconds: 0}
                })));
            } catch (e) {
                setExpiresAt(expiresAt);
            }
        }
    }, [job.id, commandLoading, expiresAt]);

    useEffectSkipMount(() => {
        setExpiresAt(status.expiresAt);
    }, [status.expiresAt]);

    const calculateTimeLeft = useCallback((expiresAt: number | undefined) => {
        if (!expiresAt) return {hours: 0, minutes: 0, seconds: 0};

        const now = new Date().getTime();
        const difference = expiresAt - now;

        if (difference < 0) return {hours: 0, minutes: 0, seconds: 0};

        return {
            hours: Math.floor(difference / 1000 / 60 / 60),
            minutes: Math.floor((difference / 1000 / 60) % 60),
            seconds: Math.floor((difference / 1000) % 60)
        }
    }, []);

    const suspendJob = React.useCallback(() => {
        try {
            setSuspended(true);
            invokeCommand(JobsApi.suspend(bulkRequestOf({id: job.id})));
        } catch (e) {
            setSuspended(false);
            displayErrorMessageOrDefault(e, "Failed to suspend virtual machine.");
        }
    }, []);

    const unsuspendJob = React.useCallback(() => {
        try {
            setSuspended(false);
            invokeCommand(JobsApi.unsuspend(bulkRequestOf({id: job.id})));
        } catch (e) {
            setSuspended(true);
            displayErrorMessageOrDefault(e, "Failed to resume virtual machine.");
        }
    }, []);

    const [timeLeft, setTimeLeft] = useState(calculateTimeLeft(expiresAt));


    const support = job.status.resolvedSupport ?
        (job.status.resolvedSupport! as ResolvedSupport<never, ComputeSupport>).support : undefined;
    const supportsExtension = isSupported(backendType, support, "timeExtension");
    const supportsLogs = isSupported(backendType, support, "logs");
    const supportsSuspend = isSupported(backendType, support, "suspension");
    const supportsPeers = isSupported(backendType, support, "peers");

    const sshAccess = useMemo(() => {
        return parseUpdatesForAccess(job.updates);
    }, [job.updates.length]);

    useEffect(() => {
        setTimeout(() => {
            setTimeLeft(calculateTimeLeft(expiresAt));
        }, 1000);
    });

    const resolvedProduct = job.status.resolvedProduct as unknown as ProductCompute;


    const ingresses = job.specification.resources.filter(it => it.type === "ingress") as AppParameterValueNS.Ingress[];
    const peers = job.specification.resources.filter(it => it.type === "peer") as AppParameterValueNS.Peer[];

    return <>
        <div className={RunningInfoWrapper}>
            <HighlightedCard isLoading={false} title={"Job info"} icon={"properties"}>
                <Flex flexDirection={"column"} height={"calc(100% - 57px)"}>
                    {!job.specification.name ? null : <Box><b>Name:</b> {job.specification.name}</Box>}
                    <Box><b>ID:</b> {shortUUID(job.id)}</Box>
                    <Box>
                        <b>Reservation:</b>{" "}
                        <ProviderTitle providerId={job.specification.product.provider} />
                        {" "}/{" "}
                        {job.specification.product.id}{" "}
                        (x{job.specification.replicas})
                    </Box>
                    <Box><b>Input:</b> {fileInfo}</Box>
                    <Box><b>Launched by:</b> {job.owner.createdBy} in {workspaceTitle}</Box>
                    <Box flexGrow={1} />
                    <Box mt={"16px"}>
                        <CancelButton job={job} state={"RUNNING"} fullWidth />
                    </Box>
                </Flex>
            </HighlightedCard>
            <HighlightedCard isLoading={false} title={"Time allocation"} icon={"hourglass"}>
                <Flex flexDirection={"column"} height={"calc(100% - 57px)"}>
                    <Box>
                        <b>Job start: </b> {status.startedAt ? dateToString(status.startedAt) : "Not started yet"}
                    </Box>
                    {!expiresAt ? null :
                        <>
                            <Box>
                                <b>Job expiry: </b> {dateToString(expiresAt)}
                            </Box>
                            <Box>
                                <b>Time remaining: </b>{timeLeft.hours < 10 ? "0" + timeLeft.hours : timeLeft.hours}
                                :{timeLeft.minutes < 10 ? "0" + timeLeft.minutes : timeLeft.minutes}
                                :{timeLeft.seconds < 10 ? "0" + timeLeft.seconds : timeLeft.seconds}
                            </Box>
                        </>
                    }
                    <Box>
                        <b>Estimated price per hour: </b>{" "}{job.status.resolvedSupport?.product.freeToUse ? "Free" :
                            job.status.resolvedProduct ?
                                usageExplainer(
                                    costOfDuration(60, job.specification.replicas, resolvedProduct),
                                    "COMPUTE",
                                    resolvedProduct.chargeType,
                                    resolvedProduct.unitOfPrice
                                )
                                : "Unknown"
                        }
                    </Box>
                    <Box flexGrow={1} />
                    <Box mb="12px">
                        {!expiresAt || !supportsExtension ? null : <>
                            Extend allocation (hours):
                            <AltButtonGroup minButtonWidth={"50px"} marginBottom={0}>
                                <Button onClick={() => extendJob(1)}>+1</Button>
                                <Button onClick={() => extendJob(6)}>+6</Button>
                                <Button onClick={() => extendJob(12)}>+12</Button>
                                <Button onClick={() => extendJob(24)}>+24</Button>
                                <Button onClick={() => extendJob(48)}>+48</Button>
                            </AltButtonGroup>
                        </>}
                        {!supportsSuspend ? null :
                            suspended ?
                                <ConfirmationButton actionText="Unsuspend" fullWidth mt="8px" mb="4px"
                                    onAction={unsuspendJob} /> :
                                <ConfirmationButton actionText="Suspend" fullWidth mt="8px" mb="4px"
                                    onAction={suspendJob} />

                        }
                    </Box>
                </Flex>
            </HighlightedCard>
            <HighlightedCard isLoading={false} title="Messages" icon="chat">
                <ProviderUpdates job={job} state={state} />
            </HighlightedCard>

            {ingresses.length === 0 ? null :
                <HighlightedCard isLoading={false} title="Public links" icon="globeEuropeSolid">
                    <Text style={{overflowY: "scroll"}} mt="6px" fontSize={"18px"}>
                        {ingresses.map(ingress => <PublicLinkEntry id={ingress.id} />)}
                    </Text>
                </HighlightedCard>
            }

            {!supportsPeers || peers.length === 0 ? null :
                <HighlightedCard isLoading={false} title="Connected jobs">
                    <Text style={{overflowY: "scroll"}} mt="6px" fontSize={"18px"}>
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHeaderCell textAlign="left">
                                        Hostname
                                    </TableHeaderCell>
                                    <TableHeaderCell textAlign="left">
                                        Job ID
                                    </TableHeaderCell>
                                </TableRow>
                            </TableHeader>
                            <tbody>
                                {peers.map(it =>
                                    <TableRow key={it.jobId}>
                                        <TableCell textAlign="left">
                                            <Truncate width={1}>{it.hostname}</Truncate>
                                        </TableCell>
                                        <TableCell textAlign="left">
                                            <Link to={`/jobs/properties/${it.jobId}?app=`}>
                                                <Truncate width={1}>{it.jobId}</Truncate>
                                            </Link>
                                        </TableCell>
                                    </TableRow>
                                )}
                            </tbody>
                        </Table>
                    </Text>
                </HighlightedCard>
            }

            {!sshAccess ? null :
                <HighlightedCard isLoading={false} title="SSH access" icon="key">
                    <Text style={{overflowY: "scroll"}} mt="6px" fontSize={"18px"}>
                        {sshAccess.success ? null : <Warning>
                            SSH was not configured successfully!
                        </Warning>}
                        {!sshAccess.command ? null : <>
                            Access the SSH server associated with this job using:
                            <pre><code>{sshAccess.command}</code></pre>
                        </>}
                        {!sshAccess.message ? null : <>{sshAccess.message}</>}
                        {sshAccess.aauLegacy ? null : <p>
                            This requires an SSH key to be uploaded. You can upload one{" "}
                            <Link to={"/ssh-keys"} target="_blank">here</Link>.
                        </p>}
                    </Text>
                </HighlightedCard>
            }
        </div>

        {!supportsLogs ? null :
            <div className={RunningJobsWrapper}>
                {Array(job.specification.replicas).fill(0).map((_, i) => {
                    return <RunningJobRank key={i} job={job} rank={i} state={state} />;
                })}
            </div>
        }
    </>;
};

const RunningJobsWrapper = injectStyleSimple("running-jobs-wrapper", `
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  margin-top: 32px;

  margin-bottom: 32px;
  grid-gap: 32px;
`);

const RunningJobRankWrapper = injectStyle("running-job-rank-wrapper", k => `
    ${k} {
        margin-top: 16px;
        margin-bottom: 16px;

        display: grid;
        grid-template-columns: 80px 1fr 200px;
        grid-template-rows: 1fr auto;
        grid-gap: 16px;
    }

    ${k} .rank {
        text-align: center;
        width: 100%;
        flex-direction: column;
    }

    ${k} .term {
        height: 100%;
    }

    ${k} .term .terminal {
        /* NOTE(Dan): This fixes a feedback loop in the xtermjs fit function. Without this, the fit function is
        unable to correctly determine the size of the terminal */
        position: absolute;
    }

    ${k} .buttons {
        display: flex;
        flex-direction: column;
    }

    ${k} .buttons .${ButtonClass} {
        margin-top: 8px;
        width: 100%;
    }

    ${deviceBreakpoint({minWidth: "1001px"})} {
        ${k}.expanded {
            height: 80vh;
        }

        ${k}.expanded .term {
            grid-row: 1;
            grid-column: 2 / 4;
        }

        ${k}.expanded .buttons {
            grid-row: 2;
            grid-column: 2 / 4;
        }
    }

    ${deviceBreakpoint({maxWidth: "1000px"})} {
        ${k}{
            grid-template-columns: 1fr !important;
        }

        ${k} .term {
            height: 400px;
        }

        ${k} .expand-btn {
            display: none;
        }
    }
`);

const RunningJobRank: React.FunctionComponent<{
    job: Job,
    rank: number,
    state: React.RefObject<JobUpdates>,
}> = ({job, rank, state}) => {
    const {termRef, terminal, fitAddon} = useXTerm({autofit: true});
    const [expanded, setExpanded] = useState(false);
    const toggleExpand = useCallback((autoScroll = true) => {
        setExpanded(!expanded);
        const targetView = termRef.current?.parentElement;
        if (targetView != null) {
            setTimeout(() => {
                // FIXME(Dan): Work around a weird resizing bug in xterm.js
                fitAddon.fit();
                fitAddon.fit();
                fitAddon.fit();
                if (autoScroll) {
                    window.scrollTo({
                        top: targetView.getBoundingClientRect().top - 100 + window.scrollY,
                    });
                }
            }, 0);
        }
    }, [expanded, termRef]);

    useEffect(() => {
        const listener = () => {
            const s = state.current;
            if (!s) return;

            const newLogQueue: LogMessage[] = [];
            for (const l of s.logQueue) {
                if (l.rank === rank) {
                    if (l.stderr != null) appendToXterm(terminal, l.stderr);
                    if (l.stdout != null) appendToXterm(terminal, l.stdout);
                } else {
                    newLogQueue.push(l);
                }
            }

            s.logQueue = newLogQueue;
        };
        state.current?.subscriptions?.push(listener);
        listener();

        if (job.specification.replicas === 1) {
            toggleExpand(false)
        }
    }, [job.id, rank]);

    return <>
        <HighlightedCard isLoading={false}>
            <div className={classConcat(RunningJobRankWrapper, expanded ? "expanded" : undefined)}>
                <div className="rank">
                    <Heading.h2>{rank + 1}</Heading.h2>
                    <Heading.h3>Node</Heading.h3>
                </div>

                <div className={"term"} ref={termRef} />

                {job.specification.replicas === 1 ? null : (
                    <RunningButtonGroup job={job} rank={rank} expanded={expanded}
                        toggleExpand={toggleExpand} />
                )}
            </div>
        </HighlightedCard>
    </>;
};

const CompletedTextWrapper = injectStyle("completed-text", k => `
  ${deviceBreakpoint({maxWidth: "1000px"})} {
    ${k} > ${AltButtonGroupClass} {
      justify-content: center;
    }
  }
`);

function jobStateToText(state: JobState) {
    switch (state) {
        case "EXPIRED":
            return "reached its time limit";
        case "FAILURE":
            return "failed"
        case "SUCCESS":
            return "completed";
        case "SUSPENDED":
            return "been suspended";
        default:
            return "";
    }
}

const CompletedText: React.FunctionComponent<{job: Job, state: JobState}> = ({job, state}) => {
    const app = job.specification.application;
    return <div className={CompletedTextWrapper}>
        <Heading.h2>Your job has {jobStateToText(state)}</Heading.h2>
        {state === "FAILURE" ?
            <Heading.h3>
                UCloud might be operating at full capacity at the moment.
                See <ExternalLink href={"https://status.cloud.sdu.dk"}>status.cloud.sdu.dk</ExternalLink> for more
                information.
            </Heading.h3> :
            null
        }
        <Heading.h3>
            {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name}
            {" "}{job.specification.application.version}{" "}
            {job.specification.name ? <>for <i>{job.specification.name}</i></> : null}
            {" "}(ID: {shortUUID(job.id)})
        </Heading.h3>
        {app.name === "unknown" ? null :
            <AltButtonGroup minButtonWidth={"200px"}>
                <Link to={buildQueryString(`/jobs/create`, {app: app.name, version: app.version, import: job.id})}>
                    <Button>Run application again</Button>
                </Link>
            </AltButtonGroup>
        }
    </div>;
};

function OutputFiles({job}: React.PropsWithChildren<{job: Job}>): JSX.Element | null {
    const pathRef = React.useRef(job.output?.outputFolder ?? "");
    if (!pathRef.current) {
        console.warn("No output folder found. Showing nothing.");
        return null;
    }
    return <div className={FadeInDiv} style={{width: "100%", marginTop: "18px"}}>
        <FileBrowse
            opts={{initialPath: pathRef.current, embedded: true}}
        />
    </div>;
};


const fadeInAnimation = makeKeyframe("fade-in-animation", `
  from {
    opacity: 0%;
  }
  75% {
    opacity: 0%;
  }
  80% {
    opacity: 0%;
  }
  to {
    opacity: 100%;
  }
`);

const FadeInDiv = injectStyle("fade-in-div", k => `
    ${k} {
        width: 100%;
        margin-top: 18px;
        animation: 1.6s ${fadeInAnimation};
    }
`);

function getAppType(job: Job): string {
    return job.status.resolvedApplication!.invocation.applicationType;
}

const RunningButtonGroup: React.FunctionComponent<{
    job: Job,
    rank: number,
    expanded?: boolean | false,
    toggleExpand?: () => void | undefined
}> = ({job, rank, expanded, toggleExpand}) => {
    const backendType = getBackend(job);
    const support = job.status.resolvedSupport ?
        (job.status.resolvedSupport! as ResolvedSupport<never, ComputeSupport>).support : undefined;
    const supportTerminal = isSupported(backendType, support, "terminal");
    const appType = getAppType(job);
    const supportsInterface =
        (appType === "WEB" && isSupported(backendType, support, "web")) ||
        (appType === "VNC" && isSupported(backendType, support, "vnc"));


    return <div className={job.specification.replicas > 1 ? "buttons" : "top-buttons"}>
        {!supportTerminal ? null : (
            <Link to={`/applications/shell/${job.id}/${rank}?hide-frame`} onClick={e => {
                e.preventDefault();

                window.open(
                    ((e.target as HTMLDivElement).parentElement as HTMLAnchorElement).href,
                    undefined,
                    "width=800,height=600,status=no"
                );
            }}>
                <Button type={"button"}>
                    Open terminal
                </Button>
            </Link>
        )}
        {appType !== "WEB" || !supportsInterface ? null : (
            <Link to={`/applications/web/${job.id}/${rank}?hide-frame`} target={"_blank"}>
                <Button>Open interface</Button>
            </Link>
        )}
        {appType !== "VNC" || !supportsInterface ? null : (
            <Link to={`/applications/vnc/${job.id}/${rank}?hide-frame`} target={"_blank"} onClick={e => {
                e.preventDefault();

                window.open(
                    ((e.target as HTMLDivElement).parentElement as HTMLAnchorElement).href,
                    `vnc-${job.id}-${rank}`,
                    "width=800,height=450,status=no"
                );
            }}>
                <Button>Open interface</Button>
            </Link>
        )}
        {job.specification.replicas === 1 ? null :
            <Button className={"expand-btn"} onClick={toggleExpand}>
                {expanded ? "Shrink" : "Expand"} output
            </Button>
        }
    </div>
};


const CancelButton: React.FunctionComponent<{
    job: Job,
    state: JobState,
    fullWidth?: boolean
}> = ({job, state, fullWidth}) => {
    const [loading, invokeCommand] = useCloudCommand();
    const onCancel = useCallback(async () => {
        if (!loading) {
            await invokeCommand(JobsApi.terminate(bulkRequestOf({id: job.id})));
        }
    }, [loading]);

    return <ConfirmationButton
        color={"red"} icon={"trash"} mb="12px" onAction={onCancel} fullWidth={fullWidth}
        actionText={state !== "IN_QUEUE" ? "Stop application" : "Cancel reservation"}
    />;
};

const ProviderUpdates: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
}> = ({job, state}) => {
    const {termRef, terminal} = useXTerm({autofit: true});

    const appendUpdate = useCallback((update: JobUpdate) => {
        if (update.status && update.status.startsWith("SSH:")) return;

        if (!update.status && !update.state) {
            appendToXterm(
                terminal,
                `[${dateToTimeOfDayString(update.timestamp)}] Job is preparing\n`
            );
        } else if (update.status) {
            appendToXterm(
                terminal,
                `[${dateToTimeOfDayString(update.timestamp)}] ${update.status}\n`
            );
        } else if (update.state) {
            let message = "Your job is now: " + stateToTitle(update.state);
            switch (update.state) {
                case "CANCELING":
                    message = "Your job is now canceling";
                    break;
                case "FAILURE":
                    message = "Your job has failed";
                    break;
                case "IN_QUEUE":
                    message = "Your job is now in the queue";
                    break;
                case "SUCCESS":
                    message = "Your job has been processed successfully";
                    break;
                case "SUSPENDED":
                    message = "Your job has been suspended";
                    break;
                case "RUNNING":
                    message = "Your job is now running";
                    break;
            }
            appendToXterm(
                terminal,
                `[${dateToTimeOfDayString(update.timestamp)}] ${message}\n`
            );
        }
    }, [terminal]);

    useLayoutEffect(() => {
        for (const update of job.updates) {
            appendUpdate(update)
        }
    }, []);

    useLayoutEffect(() => {
        let mounted = true;
        const listener = () => {
            if (!mounted) return;
            const s = state.current;
            if (!s) return;

            while (true) {
                const update = s.updateQueue.pop();
                if (!update) break;
                appendUpdate(update);
            }
        };
        listener();
        state.current?.subscriptions?.push(listener);
        return () => {
            mounted = false;
        };
    }, [state]);
    return <Box height={"200px"} divRef={termRef} />
};

export default View;
