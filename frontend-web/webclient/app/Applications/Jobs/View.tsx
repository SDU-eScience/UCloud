import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {useLocation, useParams} from "react-router-dom";
import {MainContainer} from "@/ui-components/MainContainer";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {isJobStateTerminal, JobState, stateToTitle} from "./index";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {displayErrorMessageOrDefault, shortUUID, timestampUnixMs, useEffectSkipMount} from "@/UtilityFunctions";
import {SafeLogo} from "@/Applications/AppToolLogo";
import {Box, Button, Card, ExternalLink, Flex, Icon, Link, Truncate} from "@/ui-components";
import TitledCard from "@/ui-components/HighlightedCard";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {device, deviceBreakpoint} from "@/ui-components/Hide";
import {CSSTransition} from "react-transition-group";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {dateToString, dateToTimeOfDayString} from "@/Utilities/DateUtilities";
import {MarginProps} from "styled-system";
import {useProject} from "@/Project/cache";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {pageV2Of} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/UtilityFunctions";
import {
    api as JobsApi,
    Job,
    JobUpdate,
    JobStatus,
    ComputeSupport,
    DockerSupport,
    NativeSupport,
    VirtualMachineSupport,
    isSyncthingApp,
    OpenInteractiveSessionRequest,
    InteractiveSession
} from "@/UCloud/JobsApi";
import {BulkResponse, PageV2, compute} from "@/UCloud";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import AppParameterValueNS = compute.AppParameterValueNS;
import PublicLinkApi, {PublicLink} from "@/UCloud/PublicLinkApi";
import {SillyParser} from "@/Utilities/SillyParser";
import Warning from "@/ui-components/Warning";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {getProviderTitle, ProviderTitle} from "@/Providers/ProviderTitle";
import {classConcat, injectStyle, makeClassName, makeKeyframe, unbox} from "@/Unstyled";
import {ButtonClass} from "@/ui-components/Button";
import FileBrowse from "@/Files/FileBrowse";
import {LogOutput} from "@/UtilityComponents";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {useScrollToBottom} from "@/ui-components/ScrollToBottom";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {appendToXterm, useXTerm} from "./XTermLib";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {WebSession} from "./Web";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import * as JobViz from "@/Applications/Jobs/JobViz"

export const jobCache = new class extends ExternalStoreBase {
    private cache: PageV2<Job> = {items: [], itemsPerPage: 100};
    private isDirty: boolean = false;

    public updateCache(page: PageV2<Job>, doClear = false) {
        this.isDirty = true;
        if (doClear) {
            this.cache = {items: [], itemsPerPage: 100};
        }

        const runningJobs = page.items.filter(it => it.status.state === "RUNNING");
        for (const job of runningJobs) {
            const duplicate = this.cache.items.find(it => it.id === job.id);
            if (duplicate) {
                duplicate.status = job.status;
            } else {
                this.cache.items.unshift(job);
            }
        }

        const endedJobs = page.items.filter(it => isJobStateTerminal(it.status.state));
        for (const endedJob of endedJobs) {
            const job = this.cache.items.find(it => it.id === endedJob.id);
            if (job) {
                this.cache.items = this.cache.items.filter(it => it.id !== job.id);
            }
        }

        this.cache.items = this.cache.items.sort((a, b) => b.createdAt - a.createdAt);

        this.emitChange();
    }

    public getSnapshot(): Readonly<PageV2<Job>> {
        if (this.isDirty) {
            this.isDirty = false;
            return this.cache = {items: this.cache.items, itemsPerPage: this.cache.itemsPerPage};
        }
        return this.cache;
    }
}();

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

const logoWrapper = makeClassName("logo-wrapper");
const logoScale = makeClassName("logo-scale");
const fakeLogo = makeClassName("fake-logo");
const active = makeClassName("active");
const data = makeClassName("data");
const dataEnterDone = makeClassName("data-enter-done");
const dataEnterActive = makeClassName("data-enter-active");
const header = makeClassName("headers");
const headerText = makeClassName("header-text");
const dataExitActive = makeClassName("data-exit-active");
const dataExit = makeClassName("data-exit");
const logo = makeClassName("logo");
const running = makeClassName("running");
const topButtons = makeClassName("top-buttons");

const Container = injectStyle("job-container", k => `
    ${k} {
        --logoScale: 1;
        --logoSize: 128px;
        margin: 32px;
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

  ${k} > ${logoWrapper.dot} {
    position: absolute;
    left: 0;
    top: 0;
    animation: 800ms ${zoomInAnim};
  }

  ${k} > ${logoWrapper.dot}${active.dot} {
    transition: scale 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
  }

  ${k} > ${logoWrapper.dot}${active.dot} > ${logoScale.dot} {
    transition: transform 300ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
    transform: scale(var(--logoScale));
    transform-origin: top left;
  }

  ${k} ${fakeLogo.dot} {
    /* NOTE(Dan): the fake logo takes the same amount of space as the actual logo, 
    this basically fixes our document flow */
    display: block;
    width: var(--logoSize);
    height: var(--logoSize);
    content: '';
  }

  ${k} ${data.dot}${dataEnterDone.dot} {
    opacity: 1;
    transform: none;
  }

  ${k} ${data.dot}${dataEnterActive.dot} {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    transition: transform 1000ms cubic-bezier(0.57, 0.10, 0.28, 0.84);
  }

  ${k} ${data.dot}${dataExit.dot} {
    opacity: 1;
  }

  ${k} ${data.dot}${dataExitActive.dot} {
    display: none;
  }

  ${k} ${data.dot} {
    width: 100%; /* fix info card width */
    opacity: 0;
    transform: translate3d(0, 50vh, 0);
  }

  ${k} ${headerText.dot} {
    margin-left: 32px;
    width: calc(100% - var(--logoSize) - 32px);
    height: var(--logoSize);
    display: flex;
    flex-direction: column;
  }

  ${deviceBreakpoint({maxWidth: "1000px"})} {
    ${k} ${fakeLogo.dot} {
      width: 100%; /* force the header to wrap */
    }

    ${k} ${logoWrapper.dot} {
      left: calc(50% - var(--logoSize) / 2);
    }

    ${k} ${header.dot} {
      text-align: center;
    }

    ${k} ${headerText.dot} {
      margin-left: 0;
      margin-top: 0;
      width: 100%;
    }
  }

  ${k}.IN_QUEUE ${logo.dot} {
    animation: 2s ${enterAnimation} infinite;
  }
  
  ${k}.RUNNING {
    --logoSize: 96px;
  }

  ${k}${running.dot} {
    --logoScale: 0.5;
  }

  ${k} ${topButtons.dot} {
    display: flex;
    gap: 8px;
    height: 23px;
  }
`);

// NOTE(Dan): WS calls don't currently have their types generated
interface JobsFollowResponse {
    updates: compute.JobUpdate[];
    log: LogMessage[];
    newStatus?: JobStatus;
    initialJob?: Job | null;
}

interface LogMessage {
    rank: number;
    stdout?: string | null;
    stderr?: string | null;
    channel?: string | null;
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

export function View(props: {id?: string; embedded?: boolean;}): React.ReactNode {
    const id = props.id ?? useParams<{id: string}>().id!;

    // Note: This might not match the real app name
    const location = useLocation();
    const appNameHint = getQueryParamOrElse(location.search, "app", "");
    const [jobFetcher, fetchJob] = useCloudAPI<Job | undefined>({noop: true}, undefined);
    const job = jobFetcher.data;
    const [interfaceTargets, setInterfaceTargets] = useState<InterfaceTarget[]>([]);
    const useFakeState = useMemo(() => localStorage.getItem("useFakeState") !== null, []);
    const [jobUpdates, setJobUpdates] = useState(job?.updates ?? []);

    if (!props.embedded) {
        usePage(`Job ${shortUUID(id)}`, SidebarTabId.RUNS);
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
    const [status, setStatus] = useState<JobStatus | null>(null);
    const jobUpdateState = useRef<JobUpdates>({updateQueue: [], logQueue: [], statusQueue: [], subscriptions: []});
    const didUnmount = useDidUnmount();

    const targetRequests = useMemo(() => {
        if (!job) return {requestsToMake: [], fixedTargets: []};
        return findTargetRequests(job);
    }, [jobUpdates.length]);

    useEffect(() => {
        (async () => {
            const targets = await resolveInterfaceTargets(targetRequests);
            if (!didUnmount.current) {
                setInterfaceTargets(targets);
            }
        })();
    }, [targetRequests.requestsToMake.length, targetRequests.fixedTargets.length]);

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

                for (let i = 0; i < (job?.specification?.replicas ?? 1); i++) {
                    jobUpdateState.current.logQueue.push({
                        rank: i,
                        stdout: `[Node ${i + 1}] Test message: ${timestampUnixMs()}\n`
                    });
                }
                jobUpdateState.current.subscriptions.forEach(it => it());
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
        if (job) {
            t1 = window.setTimeout(() => {
                setDataAnimationAllowed(true);
            }, 400);
        }

        return () => {
            if (t1 != undefined) clearTimeout(t1);
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

        if (e.initialJob) {
            const alreadySeenUpdates = job?.updates ?? [];
            const initialJobUpdates = e.initialJob.updates;
            for (const update of initialJobUpdates) {
                const seen = alreadySeenUpdates.some(it => it.timestamp == update.timestamp &&
                    it.status == update.status && it.state == update.state && it.outputFolder == update.outputFolder);

                if (!seen) {
                    s.updateQueue.push(update);
                    job?.updates?.push(update);

                    if (job && update.outputFolder) {
                        const j = {...job};
                        j.output = {outputFolder: update.outputFolder};
                        jobCache.updateCache(pageV2Of(j));
                        jobFetcher.data = j;
                    }

                    if (job && update.state && (update.state === "RUNNING" || isJobStateTerminal(update.state))) {
                        const j = {...job};
                        j.status.state = update.state;
                        jobCache.updateCache(pageV2Of(j));
                    }
                }
            }
        }

        if (e.log) {
            for (const msg of e.log) {
                s.logQueue.push(msg);
            }
        }

        if (e.updates) {
            for (const update of e.updates) {
                s.updateQueue.push(update);
                job?.updates?.push(update);

                if (job && update.outputFolder) {
                    const j = {...job};
                    j.output = {outputFolder: update.outputFolder};
                    jobCache.updateCache(pageV2Of(j));
                    jobFetcher.data = j;
                }

                if (job && update.state && (update.state === "RUNNING" || isJobStateTerminal(update.state))) {
                    const j = {...job};
                    j.status.state = update.state;
                    jobCache.updateCache(pageV2Of(j));
                }
            }
        }

        if (e.newStatus) {
            s.statusQueue.push(e.newStatus);
        }

        setJobUpdates([...(job?.updates ?? [])]);

        s.subscriptions.forEach(it => it());
    }, [job]);

    const isVirtualMachine = status?.resolvedApplication?.invocation.tool?.tool?.description.backend === "VIRTUAL_MACHINE";

    useJobUpdates(job, jobUpdateListener);

    // Note(Jonas): CSSTransition otherwise relies on removed "React.findDomNode"
    const transitionRefOne = useRef(null);
    const transitionRefTwo = useRef(null);
    const transitionRefThree = useRef(null);

    if (jobFetcher.error !== undefined) {
        return <MainContainer main={<Heading.h2>An error occurred</Heading.h2>} />;
    }

    const main = (
        <div className={classConcat(Container, status?.state ?? "state-loading")}>
            <div className={`${logoWrapper.class} ${active.class}`}>
                <div className={logoScale.class}>
                    <div className="logo">
                        <SafeLogo name={job?.specification?.application?.name ?? appNameHint}
                            type={"APPLICATION"}
                            size={"var(--logoSize)"} />
                    </div>
                </div>
            </div>
            {!job || !status ? null : (
                <CSSTransition
                    nodeRef={transitionRefOne}
                    in={(status?.state === "IN_QUEUE" || status?.state === "SUSPENDED") && !isVirtualMachine && dataAnimationAllowed}
                    timeout={{
                        enter: 1000,
                        exit: 0,
                    }}
                    classNames={data.class}
                    unmountOnExit
                >
                    <div ref={transitionRefOne} className={data.class}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={"header"}>
                            <div className={fakeLogo.class} />
                            <div className={headerText.class}>
                                <InQueueText job={job} state={status.state ?? "IN_QUEUE"} />
                            </div>
                        </Flex>

                        <div className={Content}>
                            <Box width={"100%"} maxWidth={"1572px"} margin={"0 auto"}>
                                <TitledCard>
                                    <ProviderUpdates key={job.id} job={job} state={jobUpdateState} addOverflow={true} />
                                </TitledCard>
                            </Box>
                        </div>
                    </div>
                </CSSTransition>
            )}

            {!job || !status ? null : (
                <CSSTransition
                    nodeRef={transitionRefTwo}
                    in={(status?.state === "RUNNING" || (isVirtualMachine && !isJobStateTerminal(status.state))) && dataAnimationAllowed}
                    timeout={{enter: 1000, exit: 0}}
                    classNames={data.class}
                    unmountOnExit
                >
                    <div ref={transitionRefTwo} className={data.class}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={header.class}>
                            <div className={fakeLogo.class} />
                            <div className={headerText.class}>
                                <RunningText job={job} interfaceLinks={interfaceTargets} defaultInterfaceName={targetRequests.defaultName} />
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
                    nodeRef={transitionRefThree}
                    in={isJobStateTerminal(status.state) && dataAnimationAllowed}
                    timeout={{enter: 1000, exit: 0}}
                    classNames={data.class}
                    unmountOnExit
                >
                    <div ref={transitionRefThree} className={data.class}>
                        <Flex flexDirection={"row"} flexWrap={"wrap"} className={header.class}>
                            <div className={fakeLogo.class} />
                            <div className={headerText.class}>
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

    if (props.embedded) return main;
    return <MainContainer main={main} />;
}

const CompletedContent: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
}> = ({job, state}) => {
    const project = useProject();
    const workspaceTitle = Client.hasActiveProject ? project.fetch().id === job?.owner?.project ? project.fetch().specification.title :
        "My workspace" : "My workspace";

    return <div className={Content}>
        <TabbedCard style={{flexBasis: "300px"}}>
            <TabbedCardTab icon={"heroServerStack"} name={"Job info"}>
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
                    <Box><b>Launched by:</b> {job.owner.createdBy} in {workspaceTitle}</Box>
                </Flex>
            </TabbedCardTab>
        </TabbedCard>

        <TabbedCard style={{flexBasis: "600px"}}>
            <StandardPanelBody>
                <TabbedCardTab icon={"heroChatBubbleBottomCenter"} name={"Messages"}>
                    <ProviderUpdates key={job.id} job={job} state={state} addOverflow={false} />
                </TabbedCardTab>
            </StandardPanelBody>
        </TabbedCard>
    </div>
};

const Content = injectStyle("content", k => `
    ${k} {
        margin-top: 32px;
        display: flex;
        gap: 16px;
    }
    
    ${k} > * {
        flex-grow: 1;
        flex-shrink: 0;
    }
`);

function PublicLinkEntry({id}: {id: string}): React.ReactNode {
    const [publicLink] = useCloudAPI<PublicLink | null>(PublicLinkApi.retrieve({id}), null);
    if (!id.startsWith("fake-") && publicLink.data == null) return <div />
    let domain: string;
    if (id.startsWith("fake")) {
        domain = "https://fake-public-link.example.com";
    } else if (publicLink.data == null) {
        return <li />;
    } else {
        domain = publicLink.data.specification.domain;
    }

    const httpDomain = domain.startsWith("https://") ? domain : "https://" + domain;
    return <li><a target={"_blank"} title={domain} href={httpDomain}>{domain}</a></li>;
}

const InQueueText: React.FunctionComponent<{job: Job, state: JobState}> = ({job, state}) => {
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
        <Heading.h2>
            {state === "IN_QUEUE" ?
                <>
                    {job.specification.application.name === "unknown" ? <>
                        {job.specification.name ? <>Starting {job.specification.name}</> : <>Job is starting</>}
                        {" "}
                        (ID: {shortUUID(job.id)})
                    </> :
                        job.specification.name ?
                            (<>
                                Starting {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                                {" "}for <i>{job.specification.name}</i> (ID: {shortUUID(job.id)})
                            </>) :
                            (<>
                                Starting {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                                {" "}(ID: {shortUUID(job.id)})
                            </>)
                    }
                </> :
                "Your job is temporarily suspended"
            }
        </Heading.h2>
        {state === "SUSPENDED" &&
            <Heading.h3>
                {job.specification.application.name === "unknown" ? <>
                    {job.specification.name ? <>{job.specification.name}</> : <></>}
                    {" "}(ID: {shortUUID(job.id)})
                </> : <>
                    {job.specification.name ?
                        (<>
                            {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                            {" "}for <i>{job.specification.name}</i> (ID: {shortUUID(job.id)})
                        </>) :
                        (<>
                            {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name} {job.specification.application.version}
                            {" "}(ID: {shortUUID(job.id)})
                        </>)
                    }
                </>
                }
            </Heading.h3>
        }
        <Busy job={job} state={state} utilization={utilization.data} />
    </>;
};

const BusyWrapper = injectStyle("busy-wrapper", k => `
    ${k} {
        display: none;
        flex-direction: column;
        flex-grow: 1;
    }

    ${k}${active.dot} {
        animation: 1s ${busyAnim};
        display: flex;
    }
`);

const Busy: React.FunctionComponent<{
    job: Job;
    state: JobState;
    utilization: compute.JobsRetrieveUtilizationResponse | null
}> = ({job, state}) => {
    const [isActive, setActive] = useState(false);
    useEffect(() => {
        const t = setTimeout(() => {
            setActive(true);
        }, 6000);

        return () => {
            clearTimeout(t);
        };
    }, []);

    return <Box className={classConcat(BusyWrapper, isActive ? active.class : undefined)}>
        {state === "IN_QUEUE" &&
            <Box>We are currently preparing your job. This step might take a few minutes.</Box>
        }

        <Box flexGrow={1} />
        <Box><CancelButton job={job} state={"IN_QUEUE"} /></Box>
    </Box>;
};

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

const RunningText: React.FunctionComponent<{
    job: Job;
    interfaceLinks: InterfaceTarget[];
    defaultInterfaceName?: string;
}> = ({job, interfaceLinks, defaultInterfaceName}) => {
    return <>
        <Flex justifyContent={"space-between"} height={"var(--logoSize)"}>
            <Flex flexDirection={"column"}>
                <Heading.h2>
                    {job.specification?.name ?? job.status.resolvedApplication?.metadata?.title ?? "Your job"} is now
                    running
                    {" "}
                    <Box style={{display: "inline"}} color={"textSecondary"}>(ID: {job.id})</Box>
                </Heading.h2>
                <Box flexGrow={1} />
                <div><CancelButton job={job} state={"RUNNING"} /></div>
            </Flex>
            <RunningButtonGroup job={job} interfaceLinks={interfaceLinks} defaultInterfaceName={defaultInterfaceName} />
        </Flex>
    </>;
};


const InterfaceSelectorTrigger = injectStyle("interface-selector-trigger", k => `
    ${k} {
        position: relative;
        width: 35px;
        height: 35px;
        border-radius: 8px;
        user-select: none;
        -webkit-user-select: none;
        background: var(--primaryMain);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
        padding: 6px;
        border-top-left-radius: 0px;
        border-bottom-left-radius: 0px;
    }

    ${k}:hover {
        background: var(--primaryDark);
    }

    ${k}[data-omit-border="true"] {
        border: unset;
    }

    ${k} > svg {
        color: var(--primaryContrast);
        position: absolute;
        bottom: 9px;
        right: 10px;
        height: 16px;
    }
`);

const RunningInfoWrapper = injectStyle("running-info-wrapper", k => `
    ${k} {
        margin-top: 32px;
        margin-bottom: 16px;
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
    }
    
    ${k} > * {
        flex-basis: 300px;
        flex-grow: 1;
    }
`);

function AltButtonGroup(props: React.PropsWithChildren<{minButtonWidth: string} & MarginProps>) {
    return <div
        style={{
            ...unbox({marginTop: props.marginTop ?? "8px", marginBottom: props.marginBottom ?? "8px", ...props}),
            display: "flex",
            gap: "8px"
        }}
    >
        {props.children}
    </div>
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

interface InterfaceTarget {
    rank: number,
    type: "WEB" | "VNC";
    target?: string,
    port?: number,
    link?: string,
    defaultName?: string | null,
}

interface TerminalTarget {
    jobId: string,
    rank: number,
}

interface TargetRequests {
    requestsToMake: OpenInteractiveSessionRequest[];
    fixedTargets: InterfaceTarget[];
    defaultName?: string;
}

function findTargetRequests(job: Job): TargetRequests {
    let requestsToMake: OpenInteractiveSessionRequest[] = [];
    const fixedTargets: InterfaceTarget[] = [];
    let defaultName: string | undefined;

    const appType = getAppType(job);
    const backendType = getBackend(job);
    const support = job.status.resolvedSupport ?
        (job.status.resolvedSupport! as ResolvedSupport<never, ComputeSupport>).support : undefined;
    const isVirtualMachine =
        job.status?.resolvedApplication?.invocation.tool?.tool?.description.backend === "VIRTUAL_MACHINE";
    const canShowVnc = (appType === "VNC" || isVirtualMachine) && isSupported(backendType, support, "vnc");
    const canShowWeb = (appType === "WEB") && isSupported(backendType, support, "web");

    // Parse targets from job updates
    for (let i = 0; i < job.updates.length; i++) {
        const status = job.updates[i].status;
        if (!status) continue;

        const messages = status.split("\n");
        for (const message of messages) {
            if (message.startsWith("Target: ")) {
                const parsedTarget = JSON.parse(message.substring("Target: ".length)) as InterfaceTarget;
                if (parsedTarget.defaultName != null) {
                    defaultName = parsedTarget.defaultName;
                    continue;
                }
                const canShowVnc = (parsedTarget.type === "VNC" || isVirtualMachine) && isSupported(backendType, support, "vnc");
                const canShowWeb = (parsedTarget.type === "WEB") && isSupported(backendType, support, "web");

                if (canShowWeb && job.status.state === "RUNNING") {
                    requestsToMake.push({
                        sessionType: "WEB",
                        id: job.id,
                        rank: parsedTarget.rank,
                        target: parsedTarget.target,
                        port: parsedTarget.port
                    });
                } else if (canShowVnc && job.status.state === "RUNNING") {
                    fixedTargets.push({
                        target: parsedTarget.target,
                        rank: parsedTarget.rank,
                        type: "WEB",
                        link: `/applications/vnc/${job.id}/${parsedTarget.rank}?hide-frame`
                    });
                }
            }
        }
    }

    if (canShowWeb && job.status.state === "RUNNING") {
        requestsToMake = requestsToMake.concat(Array(job.specification.replicas)
            .fill(0).map((_, i) => ({sessionType: 'WEB', id: job.id, rank: i} as OpenInteractiveSessionRequest)));
    }

    if (canShowVnc && job.status.state === "RUNNING") {
        fixedTargets.push({rank: 0, type: "VNC", link: `/applications/vnc/${job.id}/0?hide-frame`} as InterfaceTarget);
    }

    return {requestsToMake, fixedTargets, defaultName};
}

async function resolveInterfaceTargets(request: TargetRequests): Promise<InterfaceTarget[]> {
    const result: InterfaceTarget[] = [...request.fixedTargets];

    if (request.requestsToMake.length > 0) {
        try {
            const sessionResult = await callAPI<BulkResponse<InteractiveSession>>(
                JobsApi.openInteractiveSession(bulkRequestOf(...request.requestsToMake))
            );
            let i = 0;
            for (const res of sessionResult?.responses ?? []) {
                const webSession = (res.session as WebSession);
                result.push({
                    target: request.requestsToMake[i].target ?? undefined,
                    rank: webSession.rank,
                    type: "WEB",
                    link: webSession.redirectClientTo,
                });
                i++;
            }
        } catch (e) {
            console.warn(e);
        }
    }

    return result;
}

const RunningContent: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
    status: JobStatus;
}> = ({job, state, status}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [expiresAt, setExpiresAt] = useState(status.expiresAt);
    const backendType = getBackend(job);
    const [suspended, setSuspended] = useState(job.status.state === "SUSPENDED");
    const messagesRef = useRef<HTMLDivElement>(null);
    const scrollRef = useRef<HTMLDivElement>(null);
    useScrollToBottom(scrollRef);
    useScrollToBottom(messagesRef);
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

    const stream = useMemo(() => new JobViz.StreamProcessor(), [job.id]);

    useEffect(() => {
        const logListener = () => {
            const s = state.current;
            if (!s) return;

            const newLogQueue: LogMessage[] = [];
            for (const l of s.logQueue) {
                if (l.channel === "ui" || l.channel === "data") {
                    const text = l.stdout ?? l.stderr ?? "";
                    stream.accept(text);
                } else if (l.channel != null) {
                    stream.acceptGenericData(l.stdout ?? l.stderr ?? "", l.channel);
                } else {
                    newLogQueue.push(l);
                }
            }

            s.logQueue = newLogQueue;
        };

        state.current?.subscriptions?.push(logListener);
        logListener();
    }, [job.id]);

    const support = job.status.resolvedSupport ?
        (job.status.resolvedSupport! as ResolvedSupport<never, ComputeSupport>).support : undefined;
    const supportsExtension = isSupported(backendType, support, "timeExtension");
    const supportsLogs = isSupported(backendType, support, "logs");
    const supportsSuspend = isSupported(backendType, support, "suspension");
    const supportsPeers = isSupported(backendType, support, "peers");

    useEffect(() => {
        setSuspended(job.status.state === "SUSPENDED");
    }, [job.status.state]);

    const sshAccess: ParsedSshAccess | null = useMemo(() => {
        const res = parseUpdatesForAccess(job.updates);
        if (res) return res;
        if (localStorage.getItem("fakeSsh")) {
            return {
                success: true,
                command: "ssh example@ssh.example.com -P 41231",
                message: "SSH configured successfully",
                aauLegacy: false,
            }
        } else {
            return null;
        }
    }, [job.updates.length]);

    const ingressResources = job.specification.resources.filter(it => it.type === "ingress") as AppParameterValueNS.Ingress[];
    const peers = job.specification.resources.filter(it => it.type === "peer") as AppParameterValueNS.Peer[];

    if (localStorage.getItem("fakeLinks")) {
        ingressResources.push({
            id: "fake-link",
            type: "ingress"
        });
    }

    const ingresses = Array.from(
        new Map(ingressResources.map(ingress => [ingress.id, ingress])).values()
    );

    if (localStorage.getItem("fakePeers")) {
        for (let i = 1; i <= 15; i++) {
            peers.push({
                hostname: "fake-peer-" + i,
                jobId: `5${i}00000`,
                type: "peer"
            });
        }
    }

    const sshUrl = React.useMemo(() => transformToSSHUrl(sshAccess?.command), [sshAccess?.command]);

    return <>
        <div className={RunningInfoWrapper}>
            <TabbedCard style={{flexBasis: "300px"}}>
                <StandardPanelBody>
                    <TabbedCardTab icon={"heroClock"} name={"Time allocation"}>
                        <Flex flexDirection={"column"} height={"calc(100% - 57px)"}>
                            <Box>
                                <b>Job submitted at: </b> {dateToString(job.createdAt)}
                            </Box>
                            <Box>
                                <b>Job
                                    start: </b> {status.startedAt ? dateToString(status.startedAt) : "Not started yet"}
                            </Box>
                            {!expiresAt && !localStorage.getItem("useFakeState") ? null :
                                <>
                                    <Box>
                                        <b>Job expiry: </b> {dateToString(expiresAt ?? timestampUnixMs())}
                                    </Box>
                                    <Box>
                                        <b>Time remaining: </b><TimeLeft expiresAt={expiresAt ?? -1} />
                                    </Box>
                                </>
                            }
                            <Box flexGrow={1} />
                            <Box mb="12px">
                                {(!expiresAt || !supportsExtension) && !localStorage.getItem("useFakeState") ? null : <>
                                    Extend allocation (hours):
                                    <AltButtonGroup minButtonWidth={"50px"} marginBottom={0}>
                                        <Button onClick={() => extendJob(1)}>+1</Button>
                                        <Button onClick={() => extendJob(8)}>+8</Button>
                                        <Button onClick={() => extendJob(24)}>+24</Button>
                                    </AltButtonGroup>
                                </>}
                                {!supportsSuspend ? null :
                                    suspended ?
                                        <Button color={"successMain"} fullWidth onClick={unsuspendJob}>
                                            <Icon name={"heroPower"} mr={"8px"} />
                                            Power on
                                        </Button> :
                                        <ConfirmationButton
                                            actionText="Power off"
                                            fullWidth
                                            mt="8px"
                                            mb="4px"
                                            icon={"heroPower"}
                                            onAction={suspendJob}
                                        />
                                }
                            </Box>
                        </Flex>
                    </TabbedCardTab>
                </StandardPanelBody>
            </TabbedCard>

            <TabbedCard style={{flexBasis: "600px"}}>
                <StandardPanelBody>
                    <JobViz.Renderer
                        processor={stream}
                        windows={[JobViz.WidgetWindow.WidgetWindowMain, JobViz.WidgetWindow.WidgetWindowAux1, JobViz.WidgetWindow.WidgetWindowAux2]}
                        tabsOnly={true}
                    />

                    {!sshAccess ? null :
                        <TabbedCardTab icon={"heroKey"} name={"SSH"}>
                            {sshAccess.success ? null : <Warning>
                                SSH was not configured successfully!
                            </Warning>}
                            {!sshAccess.message ? null : <>{sshAccess.message}</>}
                            {!sshAccess.command ? null : <>
                                {sshUrl != null ?
                                    <a href={sshUrl}><CodeSnippet maxHeight={"100px"}>{sshAccess.command}</CodeSnippet></a> :
                                    <CodeSnippet maxHeight={"100px"}>{sshAccess.command}</CodeSnippet>
                                }
                            </>}
                        </TabbedCardTab>
                    }

                    {!supportsPeers || peers.length === 0 ? null :
                        <TabbedCardTab icon={"heroServerStack"} name={`Connected jobs (${peers.length})`}>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHeaderCell textAlign="left" width={"120px"}>Job ID</TableHeaderCell>
                                        <TableHeaderCell textAlign="left">Hostname</TableHeaderCell>
                                    </TableRow>
                                </TableHeader>
                                <tbody>
                                    {peers.map(it =>
                                        <TableRow key={it.jobId}>
                                            <TableCell textAlign="left" width={"120px"}>
                                                <Link to={`/jobs/properties/${it.jobId}?app=`} target={"_blank"}>
                                                    {it.jobId}
                                                    {" "}
                                                    <Icon name={"heroArrowTopRightOnSquare"} mt={"-5px"} />
                                                </Link>
                                            </TableCell>

                                            <TableCell textAlign="left">
                                                <code><Truncate width={1}>{it.hostname}</Truncate></code>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </tbody>
                            </Table>
                        </TabbedCardTab>
                    }

                    {ingresses.length === 0 ? null :
                        <TabbedCardTab icon={"heroGlobeEuropeAfrica"} name={`Links (${ingresses.length})`}>
                            This job is publicly available through:
                            <ul style={{paddingLeft: "2em"}}>
                                {ingresses.map(ingress => <PublicLinkEntry key={ingress.id} id={ingress.id} />)}
                            </ul>
                        </TabbedCardTab>
                    }
                </StandardPanelBody>
            </TabbedCard>

            <TabbedCard style={{flexBasis: "600px"}}>
                <StandardPanelBody divRef={messagesRef}>
                    <TabbedCardTab icon={"heroChatBubbleBottomCenter"} name={"Messages"}>
                        <ProviderUpdates key={job.id} job={job} state={state} addOverflow={false} />
                    </TabbedCardTab>
                </StandardPanelBody>
            </TabbedCard>
        </div>

        {!supportsLogs ? null :
            <TabbedCard>
                <Box divRef={scrollRef}>
                    {Array(job.specification.replicas).fill(0).map((_, i) =>
                        <RunningJobRank key={i} job={job} rank={i} state={state} />
                    )}
                </Box>
            </TabbedCard>
        }
    </>;
};


/* 
 *  tests:
    console.log(transformToSSHUrl("foobar baz -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh baz -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh -p bar"), "invalid");
    console.log(transformToSSHUrl("ssh baz 200"), "invalid");
    console.log(transformToSSHUrl("ssh baz bar"), "invalid");
    console.log(transformToSSHUrl("ssh ssh.example.com -P 41231"), "valid");
    console.log(transformToSSHUrl("ssh example@ssh.example.com -P 41231"), "valid")
*/
function transformToSSHUrl(command?: string | null): `ssh://${string}:${number}` | null {

    if (!command) return null;
    const parser = new SillyParser(command);

    // EXAMPLE:
    // ssh ucloud@ssh.cloud.sdu.dk -p 1234

    if (parser.consumeWord().toLocaleLowerCase() !== "ssh") {
        return null;
    }

    const hostname = parser.consumeWord();

    if (!hostname || hostname.toLocaleLowerCase() === "-p") return null;

    let portNumber = 22; // Note(Jonas): Fallback value if none present.

    const portFlag = parser.consumeWord();

    if (portFlag.toLocaleLowerCase() === "-p") {
        const portNumberString = parser.consumeWord();
        const parsed = parseInt(portNumberString, 10);
        if (isNaN(parsed)) return null;
        portNumber = parsed;
    } else {
        if (portFlag !== hostname) return null; // Note(Jonas): Is the last parsed word already read, so this is repeated?
    }

    // Fallback port 22
    return `ssh://${hostname}:${portNumber}`;
}

const StandardPanelBody: React.FunctionComponent<React.PropsWithChildren<{
    divRef?: React.RefObject<HTMLDivElement | null>;
}>> = ({divRef, children}) => {
    return <div style={{height: "165px", overflowY: "auto"}} ref={divRef}>{children}</div>;
};

function TimeLeft({expiresAt}: {expiresAt: number}) {
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

    const [timeLeft, setTimeLeft] = useState(calculateTimeLeft(expiresAt));

    useEffect(() => {
        setTimeout(() => {
            setTimeLeft(calculateTimeLeft(expiresAt));
        }, 1000);
    });


    return (timeLeft.hours < 10 ? "0" + timeLeft.hours : timeLeft.hours) +
        ":" + (timeLeft.minutes < 10 ? "0" + timeLeft.minutes : timeLeft.minutes) +
        ":" + (timeLeft.seconds < 10 ? "0" + timeLeft.seconds : timeLeft.seconds)
}

const RunningJobRankWrapper = injectStyle("running-job-rank-wrapper", k => `
    ${k} {
        --termHeight: max(320px, calc(50vh - 60px));
        margin-top: 16px;
        margin-bottom: 16px;
    }

    ${k}[data-has-replicas="true"] {
        --termHeight: max(300px, calc(50vh - 113px));
    }

    ${k} .rank {
        text-align: center;
        width: 100%;
        flex-direction: column;
    }

    ${k} .term {
        height: var(--termHeight);
        width: 100%;
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
    const {termRef, terminal} = useXTerm({autofit: true});

    useEffect(() => {
        const listener = () => {
            const s = state.current;
            if (!s) return;

            const newLogQueue: LogMessage[] = [];
            for (const l of s.logQueue) {
                if (l.channel == null && l.rank === rank) {
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
    }, [job.id, rank]);

    const hasMultipleNodes = job.specification.replicas > 1;

    return <TabbedCardTab icon={"heroServer"} name={`Node ${rank + 1}`}>
        <div className={RunningJobRankWrapper} data-has-replicas={hasMultipleNodes}>
            <div ref={termRef} className="term" />
        </div>
    </TabbedCardTab>
};

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

const UNKNOWN_APP_NAME = "unknown";

const CompletedText: React.FunctionComponent<{job: Job, state: JobState}> = ({job, state}) => {
    const app = job.specification.application;
    const isUnknownApp = app.name === UNKNOWN_APP_NAME;
    return <Flex flexDirection={"column"} flexGrow={1}>
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
            {isUnknownApp ? null : <>
                {job.status.resolvedApplication?.metadata?.title ?? job.specification.application.name}
                {" "}{job.specification.application.version}{" "}
                {job.specification.name ? <>for <i>{job.specification.name}</i></> : null}
            </>}
            {" "}(ID: {shortUUID(job.id)})
        </Heading.h3>
        <Box flexGrow={1} />
        {isUnknownApp || isSyncthingApp(job) ? null :
            <Link to={buildQueryString(`/jobs/create`, {app: app.name, version: app.version, import: job.id})}>
                <Button>Run application again</Button>
            </Link>
        }
    </Flex>;
};

function OutputFiles({job}: React.PropsWithChildren<{job: Job}>): React.ReactNode {
    const pathRef = React.useRef(job.output?.outputFolder ?? "");
    if (!pathRef.current) {
        console.warn("No output folder found. Showing nothing.");
        return null;
    }

    useEffect(() => {
        const newPath = job.output?.outputFolder ?? "";
        if (newPath) {
            pathRef.current = newPath;
        }
    }, [job.output?.outputFolder]);

    return <Card key={job.id} className={FadeInDiv} p={"0px"} minHeight={"500px"} mt={"16px"}>
        <FileBrowse
            opts={{
                initialPath: pathRef.current,
                managesLocalProject: true,
                embedded: {hideFilters: true, disableKeyhandlers: false}
            }}
        />
    </Card>;
}

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
        animation: 1.6s ${fadeInAnimation};
    }
`);

function getAppType(job: Job): string {
    return job.status.resolvedApplication!.invocation.applicationType;
}


const InterfaceLinkRow: RichSelectChildComponent<SearchableInterfaceTarget> = ({element, dataProps, onSelect}) => {
    if (!element) return null;

    return <Box {...dataProps}>
        <Link
            to={element.link ?? ""}
            key={element.link ?? (element.rank + (element.port?.toString() ?? ""))}
            target={"_blank"}
        >
            <Flex
                gap="5px"
                alignItems={"center"}
                p={8}
            >
                <Icon name="heroArrowTopRightOnSquare" />
                <Truncate>{element.target ?? element.defaultName ?? "Open interface"}</Truncate>

                {!element.showNode ? null :
                    <div style={{color: "var(--textSecondary)"}}>
                        Node {element.rank + 1}
                    </div>
                }
            </Flex>
        </Link>
    </Box>;
}

const InterfaceLinkSelectedRow: RichSelectChildComponent<SearchableInterfaceTarget> = () => {
    return <div className={InterfaceSelectorTrigger}>
        <Icon name="heroChevronDown" />
    </div>;
}

const TerminalLinkRow: RichSelectChildComponent<SearchableTerminalTarget> = ({element}) => {
    if (!element) return null;

    return <Link to={`/applications/shell/${element.jobId}/${element.rank}?hide-frame`} target={"_blank"}>
        <Flex
            gap="5px"
            alignItems={"center"}
            p={8}
        >
            <Icon name="heroCommandLine" />
            <Truncate>Node {element.rank + 1}</Truncate>
        </Flex>
    </Link>;
}

const TerminalLinkSelectedRow: RichSelectChildComponent<SearchableTerminalTarget> = () => {
    return <div className={InterfaceSelectorTrigger}>
        <Icon name="heroChevronDown" />
    </div>;
}

type SearchableInterfaceTarget = (InterfaceTarget & {searchString: string; showNode: boolean;})
type SearchableTerminalTarget = (TerminalTarget & {searchString: string;})

const RunningButtonGroup: React.FunctionComponent<{
    job: Job;
    interfaceLinks: InterfaceTarget[];
    defaultInterfaceName?: string;
}> = ({job, interfaceLinks, defaultInterfaceName}) => {
    const hasMultipleNodes = job.specification.replicas > 1;
    const terminalLinks: TerminalTarget[] = Array.from(Array(job.specification.replicas).keys()).map(rank => ({
        jobId: job.id,
        rank: rank,
    }));

    const backendType = getBackend(job);
    const support = job.status.resolvedSupport ?
        (job.status.resolvedSupport! as ResolvedSupport<never, ComputeSupport>).support : undefined;
    const supportTerminal = isSupported(backendType, support, "terminal");

    let defaultInterfaceId = interfaceLinks.findIndex(link => !link.target && link.rank === 0);

    if (defaultInterfaceId < 0) {
        defaultInterfaceId = interfaceLinks.findIndex(it => !it.target);

        if (defaultInterfaceId < 0) {
            defaultInterfaceId = 0
        }
    }

    const searchableInterfaceLinks: SearchableInterfaceTarget[] = interfaceLinks
        .filter(it => it !== interfaceLinks[defaultInterfaceId])
        .map(it => {
            const isDouble = interfaceLinks.filter(existing => it.target === existing.target).length > 1;

            return {
                searchString: it.target + " " + (it.rank + 1),
                showNode: isDouble,
                defaultName: defaultInterfaceName,
                ...it
            };
        });

    const searchableTerminalLinks: SearchableTerminalTarget[] = terminalLinks
        .filter(it => it.rank > 0)
        .map(it => ({
            searchString: (it.rank + 1).toString(), ...it
        }));

    const onInterfaceSelect = useCallback((target: SearchableInterfaceTarget) => {
        window.open(target.link, "_blank");
    }, []);

    const onTerminalSelect = useCallback((target: SearchableTerminalTarget) => {
        window.open(`/applications/shell/${target.jobId}/${target.rank}?hide-frame`, "_blank");
    }, []);

    return <div className={topButtons.class}>
        {!supportTerminal ? null : (
            <Flex>
                <Link to={`/applications/shell/${job.id}/0?hide-frame`} target={"_blank"}>
                    <Button attachedLeft={hasMultipleNodes}>
                        <Icon name="heroCommandLine" />
                        <div style={{minWidth: hasMultipleNodes ? "130px" : "164px", maxWidth: "164px"}}>
                            <Truncate>
                                Open terminal{hasMultipleNodes ? ` (Node 1)` : null}
                            </Truncate>
                        </div>
                    </Button>
                </Link>
                {!hasMultipleNodes ? null :
                    <RichSelect
                        items={searchableTerminalLinks}
                        keys={["searchString"]}
                        RenderRow={TerminalLinkRow}
                        FullRenderSelected={TerminalLinkSelectedRow}
                        onSelect={onTerminalSelect}
                    />
                }
            </Flex>
        )}

        {interfaceLinks.length < 1 ? null : (
            <Flex>
                <Link to={interfaceLinks[defaultInterfaceId]?.link ?? ""}
                    aria-disabled={!interfaceLinks[defaultInterfaceId]} target={"_blank"}>
                    <Button attachedLeft={interfaceLinks.length > 1} disabled={!interfaceLinks[defaultInterfaceId]}>
                        <Icon name="heroArrowTopRightOnSquare" />
                        <div style={{minWidth: interfaceLinks.length > 1 ? "130px" : "164px", maxWidth: "164px"}}>
                            <Truncate>
                                {interfaceLinks[defaultInterfaceId]?.target ?? (defaultInterfaceName ?? "Open interface" + (hasMultipleNodes ? ` (Node 1)` : ""))}
                            </Truncate>
                        </div>
                    </Button>
                </Link>
                {interfaceLinks.length <= 1 ? null :
                    <RichSelect
                        items={searchableInterfaceLinks}
                        keys={["searchString"]}
                        RenderRow={InterfaceLinkRow}
                        FullRenderSelected={InterfaceLinkSelectedRow}
                        onSelect={onInterfaceSelect}
                    />
                }
            </Flex>
        )}
    </div>
};

const CancelButton: React.FunctionComponent<{
    job: Job,
    state: JobState,
}> = ({job, state}) => {
    const [loading, invokeCommand] = useCloudCommand();
    const onCancel = useCallback(async () => {
        if (!loading) {
            await invokeCommand(JobsApi.terminate(bulkRequestOf({id: job.id})));
        }
    }, [loading, job.id]);

    return <ConfirmationButton
        color={"errorMain"} icon={"trash"} align="left" width={"250px"} onAction={onCancel} fullWidth={false}
        actionText={state !== "IN_QUEUE" ? "Stop application" : "Cancel reservation"}
    />;
};

const ProviderUpdates: React.FunctionComponent<{
    job: Job;
    state: React.RefObject<JobUpdates>;
    addOverflow: boolean;
}> = ({job, state, addOverflow}) => {
    const [updates, setUpdates] = useState<string[]>([])

    const appendUpdate = useCallback((update: JobUpdate) => {
        if (update.status && update.status.startsWith("SSH:")) return;
        if (update.status && update.status.startsWith("Target:")) return;

        if (!update.status && !update.state) {
            setUpdates(u => {
                let text = `[${dateToTimeOfDayString(update.timestamp)}] `;
                text += job.owner.createdBy;
                text += " has requested ";
                text += job.specification.replicas;
                text += "x ";
                text += job.specification.product.id;
                text += " from ";
                text += getProviderTitle(job.specification.product.provider);
                text += "\n";

                return [...u, text];
            });
        } else if (update.status) {
            setUpdates(u => [...u, `[${dateToTimeOfDayString(update.timestamp)}] ${update.status}\n`]);
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
            setUpdates(u => [
                ...u,
                `[${dateToTimeOfDayString(update.timestamp)}] ${message}\n`
            ]);
        }
    }, []);

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

    if (addOverflow) {
        return <Box height={"200px"} overflowY="auto">
            <LogOutput updates={updates} maxHeight="200px" />
        </Box>
    } else {
        return <LogOutput updates={updates} />;
    }
};

export default View;
