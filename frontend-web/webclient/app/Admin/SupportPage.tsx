import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Button, Divider, Error, Flex, Heading, Icon, Input, Label, Link, MainContainer} from "@/ui-components";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import * as React from "react";
import {useMemo} from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {Client} from "@/Authentication/HttpClientInstance";
import AppRoutes from "@/Routes";
import {normalizeFrequency, WalletV2} from "@/Accounting";
import {Project} from "@/Project";
import {apiRetrieve, apiUpdate, callAPI} from "@/Authentication/DataHook";
import {capitalized, doNothing, errorMessageOrDefault} from "@/UtilityFunctions";
import {mail} from "@/UCloud";
import {Application} from "@/Grants";
import {Job} from "@/UCloud/JobsApi";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {dateToString} from "@/Utilities/DateUtilities";
import {ProjectTitleForNewCore} from "@/Project/InfoCache";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import RenderMermaid from "react-x-mermaid";
import {dialogStore} from "@/Dialog/DialogStore";
import Warning from "@/ui-components/Warning";
import EmailSettings = mail.EmailSettings;

const {supportAssist} = AppRoutes;

export default function () {
    usePage("Support", SidebarTabId.ADMIN);

    const navigate = useNavigate();

    const userRef = React.useRef<HTMLInputElement>(null);
    const emailRef = React.useRef<HTMLInputElement>(null);
    const projectRef = React.useRef<HTMLInputElement>(null);
    const jobRef = React.useRef<HTMLInputElement>(null);
    const allocationRef = React.useRef<HTMLInputElement>(null);

    const navigateTo = React.useCallback((url: string, id: string | undefined, additional?: Record<string, any>) => {
        if (!id) {
            snackbarStore.addFailure("Field cannot be empty", false);
            return;
        }
        navigate(buildQueryString(url, {id, ...additional}));
    }, [navigate]);

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <Heading mb="32px">Support page</Heading>
            <Box>
                <form onSubmit={e => {
                    e.preventDefault();
                    navigateTo(supportAssist.user(), userRef.current?.value, {isEmail: false})
                }}>
                    <Flex my="12px">
                        <Label>
                            Username
                            <Input placeholder="Type username..." inputRef={userRef} />
                        </Label>
                        <SearchButton />
                    </Flex>
                </form>
                <form onSubmit={e => {
                    e.preventDefault();
                    navigateTo(supportAssist.user(), emailRef.current?.value, {isEmail: true})
                }}>
                    <Flex my="12px">
                        <Label>
                            e-mail
                            <Input placeholder="Type e-mail..." inputRef={emailRef} />
                        </Label>
                        <SearchButton />
                    </Flex>
                </form>
                <Box my="12px">
                    <form onSubmit={e => {
                        e.preventDefault();
                        navigateTo(supportAssist.project(), projectRef.current?.value)
                    }}>
                        <Flex>
                            <Label>
                                Project ID
                                <Input placeholder="Type project ID..." inputRef={projectRef} />
                            </Label>
                            <SearchButton />
                        </Flex>
                    </form>
                </Box>
                <form onSubmit={e => {
                    e.preventDefault();
                    navigateTo(supportAssist.job(), jobRef.current?.value);
                }}>
                    <Flex my="12px">
                        <Label>
                            Job ID
                            <Input placeholder="Type job ID..." inputRef={jobRef} />
                        </Label>
                        <SearchButton />
                    </Flex>
                </form>
                <Box my="12px">
                    <form onSubmit={e => {
                        e.preventDefault();
                        navigateTo(supportAssist.allocation(), allocationRef.current?.value);
                    }}>
                        <Flex>
                            <Label>
                                Allocation ID
                                <Input placeholder="Type allocation ID..." inputRef={allocationRef} />
                            </Label>
                            <SearchButton />
                        </Flex>
                    </form>
                </Box>
            </Box>
        </>
        }
    />
}

function SearchButton() {
    return <Button ml="24px" mt="auto">Search</Button>;
}

// USER

interface SupportAssistRetrieveUserInfoRequest {
    username: string;
    email: string;
}

interface SupportAssistUserInfo {
    username: string;
    firstNames: string;
    lastName: string;
    email: string;
    emailSettings: EmailSettings;
    associatedProjects: Project[];
    activeGrants: Application[];
    personalProjectResources: WalletV2[];
}

interface SupportAssistRetrieveUserInfoResponse {
    info: SupportAssistUserInfo[]
}

interface Reset2FARequest {
    username: string;
}

type Reset2FAResponse = void;

function reset2fa(req: Reset2FARequest): APICallParameters<Reset2FARequest, Reset2FAResponse> {
    return apiUpdate(req, "api/supportAssistFnd", "resetMfa")
}

export function UserSupportContent() {
    const location = useLocation();
    const query = getQueryParam(location.search, "id");
    const isEmail = getQueryParam(location.search, "isEmail") === "true";
    usePage("User support", SidebarTabId.ADMIN);

    const {Error, setError} = useError();
    const [userInfo, setInfo] = React.useState<SupportAssistRetrieveUserInfoResponse>({info: []});

    React.useEffect(() => {
        if (!query) return;
        callAPI(Api.retrieveUserInfo(isEmail ? {username: "", email: query} : {username: query, email: ""}))
            .then(result => {
                setInfo(result)
            })
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch user")));
    }, [query, isEmail]);

    if (!Client.userIsAdmin || userInfo == null) return null;

    const sortedAssociatedProjects = useMemo(() => {
        return userInfo.info.map(user => {
            const sortedList = user.associatedProjects.sort((a, b) => {
                return a.specification.title.localeCompare(b.specification.title)
            })
            return {username: user.username, associatedProject: sortedList}
        });
    }, [userInfo])


    return <MainContainer
        main={<>
            {Error}
            <div>
                {userInfo.info === null ? `0 entries found` : null}
                {userInfo.info?.length > 1 ? `${userInfo.info?.length} entries found` : null}

                {userInfo.info?.map(it => <div key={it.username}>
                    <h3>Personal Info:</h3>
                    <hr/>
                    <div>Name: {it.firstNames} {it.lastName}</div>
                    <div>Email: {it.email}</div>
                    <br/>
                    <h3>Reset 2FA</h3>
                    <hr/>
                    <div>
                        <ConfirmationButton
                            actionText={"Reset 2FA"}
                            icon={"refresh"}
                            color="errorMain"
                            onAction={() => {
                                const requiredText = it.username
                                dialogStore.addDialog((
                                    <div onKeyDown={e => e.stopPropagation()}>
                                        <div>
                                            <h3>Are you absolutely sure?</h3>
                                            <Divider />
                                            <Warning>This is a dangerous operation, please read this!</Warning>
                                            <Box mb={"8px"} mt={"16px"}>
                                                This will reset the two factor authentication for {it.username}! This action <i>CANNOT BE UNDONE</i>.
                                            </Box>
                                            <Box mb={"16px"}>
                                                Please type '<b>{requiredText}</b>' to confirm.
                                            </Box>
                                            <Box mb={"16px"}>
                                                <form onSubmit={ev => {
                                                    ev.preventDefault();
                                                    ev.stopPropagation();
                                                    const written = document.querySelector<HTMLInputElement>("#collectionName")?.value ?? "";
                                                    if (written !== requiredText) {
                                                        snackbarStore.addFailure(`Please type '${requiredText}' to confirm.`, false);
                                                    } else {
                                                        callAPI(reset2fa({username: written})).then(
                                                            result => {
                                                                snackbarStore.addSuccess("2FA reset.", false)
                                                                dialogStore.success();
                                                            }
                                                        ).catch(e => snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to reset 2FA"), false))
                                                    }
                                                }}>
                                                    <Input id={"collectionName"} mb={"8px"} />
                                                    <Button color={"errorMain"} type={"submit"} fullWidth>
                                                        I understand what I am doing, reset the 2FA
                                                    </Button>
                                                </form>
                                            </Box>
                                        </div>
                                    </div>
                                ), doNothing, true);
                            }}
                        />
                    </div>
                    <br/>

                    <h3>E-mail settings:</h3>
                    <hr/>
                    <div>
                        <ul style={{ listStyle: "none" }}>
                            {Object.keys(it.emailSettings).map(key =>
                                <li key={key}>
                                    <input type={"checkbox"} checked={it.emailSettings[key]}/> {capitalized(key)}
                                </li>
                            )}
                        </ul>
                    </div>
                    <br/>

                    { it.associatedProjects != null ? <>
                        <h3>Associated projects:</h3>
                        <hr/>
                        <div>
                            <table>
                                <thead>
                                <tr>
                                    <th align={"left"}>Project Title</th>
                                    <th style={{width: "20px"}}></th>
                                    <th align={"left"}>Project ID</th>
                                    <th style={{width: "20px"}}></th>
                                    <th>Role</th>
                                </tr>
                                </thead>
                                <tbody>
                                { sortedAssociatedProjects.map((info) => <>{
                                    info.username === info.username ? info.associatedProject.map(project => <tr key={project.id}>
                                            <td>{project.specification.title}</td>
                                            <td/>
                                            <td>{project.id}
                                                <Link target={"_blank"}
                                                      to={buildQueryString(supportAssist.project(), {id: project.id})}>
                                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                                </Link>
                                            </td>
                                            <td/>
                                            {project.status.members?.map(member =>
                                                member.username === it.username ? <td>{member.role}</td> : null
                                            )}
                                        </tr>
                                    ) : null
                                }</>)}
                                </tbody>
                            </table>
                        </div>
                        <br/>
                    </> : null}

                    <h3>Grants:</h3>
                    <hr/>
                    <GrantsTable grants={it.activeGrants}/>
                    <br/>

                    <h3>Personal project resources</h3>
                    <hr/>
                    <WalletTable wallets={it.personalProjectResources}/>
                </div>)}
            </div>
        </>}
    />
}

// PROJECT
interface SupportAssistRetrieveProjectInfoRequest {
    projectId: string;
}

interface SupportAssistRetrieveProjectInfoResponse {
    project: Project;
    projectWallets?: WalletV2[];
    activeGrants: Application[];
    accountingIssues?: WalletV2[];
    jobs?: Job[];
}

function getProjectChildren(wallets: WalletV2[]): Record<string, string> {
    const children: Record<string, string> = {};
    wallets.forEach((wallet) => {
        wallet.children?.map((child) => {
            if (child.child.projectId != null) {
                children[child.child.projectId] = child.child.projectId;
            }
        })
    })
    return children;
}

export function ProjectSupportContent() {
    usePage("Project support", SidebarTabId.ADMIN);
    const location = useLocation();
    const projectId = getQueryParam(location.search, "id");

    const {Error, setError} = useError();
    const [project, setProject] = React.useState<SupportAssistRetrieveProjectInfoResponse | null>(null);

    React.useEffect(() => {
        if (!projectId) return;
        callAPI(Api.retrieveProjectInfo({
            projectId
        })).then(setProject).catch(e => setError(errorMessageOrDefault(e, "Failed to fetch project")));
    }, [projectId]);

    if (!Client.userIsAdmin || project == null) return null;
    return <MainContainer
        main={<>
            {Error}
            <div>
                <h3>Project Information</h3>
                <hr/>
                <div>Project Title: {project.project.specification.title}</div>
                <div>Created at: {dateToString(project.project.createdAt)} </div>
                <div>Is allowed to use resources: {project.project.specification.canConsumeResources ? "YES" : "NO"}</div>
                <div>{(project.project.specification.parent !== undefined && project.project.specification.parent !== null) ? <>
                    Parent project: <ProjectTitleForNewCore id={project.project.specification.parent  ?? ""}/> ({project.project.specification.parent})
                    <Link target={"_blank"}
                          to={buildQueryString(supportAssist.project(), {id: project.project.specification.parent})}>
                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                </Link>
                </> : null}
                </div>
                <br/>

                <div>
                    <h3>Project Members:</h3>
                    <table>
                        <thead>
                            <tr>
                                <th>Username</th>
                                <th style={{width: "20px"}}></th>
                                <th>Role</th>
                            </tr>
                        </thead>
                        <tbody>
                            {project.project.status.members != null ? <>
                                {project.project.status.members.map(member => <tr key={member.username}>
                                    <td>{member.username}
                                        <Link target={"_blank"}
                                              to={buildQueryString(supportAssist.user(), {id: member.username})}>
                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                    </Link></td> <td/>
                                    <td>{member.role}</td>
                                </tr>)}
                            </> : null}
                        </tbody>
                    </table>
                </div>
                <br/>

                {project.projectWallets != null ? <>
                    <div>
                        <h3>Project Children:</h3>
                        {Object.values(getProjectChildren(project.projectWallets)).length > 0 ? <>
                            <table>
                                <thead>
                                <tr>
                                    <th>ID</th>
                                    <th style={{width: "20px"}}></th>
                                    <th>Title</th>
                                </tr>
                                </thead>
                                <tbody>
                                {Object.values(getProjectChildren(project.projectWallets)).map(id =>
                                    <tr key={id}>
                                        <td><ProjectTitleForNewCore id={id}/></td> <td/>
                                        <td>{id}
                                            <Link target={"_blank"}
                                                  to={buildQueryString(supportAssist.project(), {id: id})}>
                                                <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                            </Link></td>
                                    </tr>
                                )}
                                </tbody>
                            </table>
                        </> : <>No children found</>}
                    </div>
                    <br/>
                </> : null }

                <h3>Wallets</h3>
                <hr/>
                <WalletTable wallets={project.projectWallets} />

                <h3>Grants</h3>
                <hr/>
                <GrantsTable grants={project.activeGrants}/>
                <br/>
                <h3>Possible reasons to limited resources</h3>
                <hr/>
                <div>
                    {project.accountingIssues !== null && project.accountingIssues !== undefined ? project.accountingIssues.map(issue => <>
                        {issue.allocationGroups !== null ? <>
                            <div>Product Category: {issue.paysFor.name} ({issue.paysFor.provider})</div>
                            <div>Problem in project: {issue.owner.type == "user" ? issue.owner.username : issue.owner.projectId} {issue.owner.type == "project" ? <>
                                    <Link target={"_blank"}
                                          to={buildQueryString(supportAssist.project(), {id: issue.owner.projectId})}>
                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                    </Link>
                                </> : null}
                            </div>
                            <br/>
                        </> : null}
                    </>) : null}
                </div>
                <br/>

                <h3>Jobs</h3>
                <hr/>
                <div>
                    <table>
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th style={{width: "20px"}}></th>
                                <th>Created At</th>
                                <th style={{width: "20px"}}></th>
                                <th>Created By</th>
                                <th style={{width: "20px"}}></th>
                                <th>Current State</th>
                                <th style={{width: "20px"}}></th>
                                <th>Application</th>
                                <th style={{width: "20px"}}></th>
                                <th>Version</th>
                            </tr>
                        </thead>
                        <tbody>
                            {project.jobs != null ? project.jobs.map(it => <tr key={it.id}>
                                    <td align={"center"}>{it.id}
                                        <Link target={"_blank"}
                                           to={buildQueryString(supportAssist.job(), {id: it.id})}>
                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                        </Link>
                                    </td><td/>
                                    <td align={"center"}>{dateToString(it.createdAt)}</td><td/>
                                    <td align={"center"}>{it.owner.createdBy}
                                        <Link target={"_blank"}
                                            to={buildQueryString(supportAssist.user(), {id: it.owner.createdBy})}>
                                        {" "}
                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                    </Link></td><td/>
                                    <td align={"center"}>{it.status.state}</td><td/>
                                    <td align={"right"}>{it.specification.application.name}</td><td/>
                                    <td align={"right"}>{it.specification.application.version}</td>
                                </tr>
                            ) : null }
                        </tbody>
                    </table>
                </div>
            </div>
        </>}
    />
}

// JOB

interface SupportAssistRetrieveJobInfoRequest {
    jobId: string;
}

interface SupportAssistRetrieveJobInfoResponse {
    jobInfo: Job;
}

export function JobSupportContent() {
    usePage("Job support", SidebarTabId.ADMIN);
    const location = useLocation();
    const jobId = getQueryParam(location.search, "id");
    const {Error, setError} = useError();
    const [job, setJob] = React.useState<SupportAssistRetrieveJobInfoResponse | null>(null);
    React.useEffect(() => {
        if (!jobId) return
        callAPI(Api.retrieveJobInfo({jobId}))
            .then(setJob)
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch job")));
    }, [jobId]);
    if (!Client.userIsAdmin || job == null) return null;

    return <MainContainer
        main={<>
            {Error}
            <h3>Job Info</h3>
            <hr/>
            <div> ID: {job.jobInfo.id} </div>
            <div> Created: {dateToString(job.jobInfo.createdAt)} </div>
            <div> Created By: {job.jobInfo.owner.createdBy}
                <Link target={"_blank"}
                      to={buildQueryString(supportAssist.user(), {id: job.jobInfo.owner.createdBy})}>
                <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
            </Link> </div>
            {job.jobInfo.owner.project !== undefined && job.jobInfo.owner.project !== null ? <> Project Scope: {job.jobInfo.owner.project}
                <Link target={"_blank"}
                      to={buildQueryString(supportAssist.project(), {id: job.jobInfo.owner.project})}>
                <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
            </Link> </> : null}
            <div> Current status: {job.jobInfo.status.state} </div>
            {job.jobInfo.specification.name != undefined && job.jobInfo.specification.name !== null ? <> Job Name: {job.jobInfo.specification.name} </> : null}
            <div>Application: {job.jobInfo.specification.application.name} (Version: {job.jobInfo.specification.application.version})</div>
            <div>Running on:
                <ul>
                    <li>
                        Category: {job.jobInfo.specification.product.category}({job.jobInfo.specification.product.provider})
                    </li>
                    <li>
                        Variant: {job.jobInfo.specification.product.id}
                    </li>
                </ul>
            </div>
            <div>Allocated time:
                <ul>
                    {job.jobInfo.specification.timeAllocation?.hours ? <li>{job.jobInfo.specification.timeAllocation.hours} hour(s)</li> : ""}
                    {job.jobInfo.specification.timeAllocation?.minutes ? <li>{job.jobInfo.specification.timeAllocation.minutes} minutes(s)</li> : ""}
                    {job.jobInfo.specification.timeAllocation?.seconds ? <li>{job.jobInfo.specification.timeAllocation.seconds} seconds(s)</li> : ""}
                </ul>
            </div>
            <div>
                SSH is enabled: {job.jobInfo.specification.sshEnabled ? 'YES' : 'NO'}
            </div>
            <br/>
            <div>
                Job Updates:
                <ul>
                    {job.jobInfo.updates.map(update => (
                        update.status !== null ? <li>{dateToString(update.timestamp)}: {update.status !== null ? update.status : null}</li> : null
                    ))}
                </ul>
            </div>
        </>}
    />
}

// ALLOCATION/WALLET

type SupportAssistRetrieveWalletInfoRequest = ({
    allocationId: string;
}) & {
    flags?: SupportAssistWalletInfoFlags;
}

interface SupportAssistWalletInfoFlags {
    includeAccountingGraph: boolean;
}

interface SupportAssistRetrieveWalletInfoResponse {
    wallet: WalletV2;
    accountingGraph?: string;
}

export function AllocationSupportContent() {
    usePage("Allocation support", SidebarTabId.ADMIN);
    const location = useLocation();
    const id = getQueryParam(location.search, "id");
    const flags = {
        includeAccountingGraph: getQueryParam(location.search, "includeAccountingGraph") === "true"
    }
    const {Error, setError} = useError();
    const [allocation, setAllocation] = React.useState<SupportAssistRetrieveWalletInfoResponse | null>(null);

    React.useEffect(() => {
        if (!id) return;
        // or wallet id? handle graph thing flag
        callAPI(Api.retrieveWalletInfo(
            {allocationId: id}))
            .then(result =>
                setAllocation(result)
            )
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch")));
    }, [id]);

    if (!Client.userIsAdmin || allocation == null) return null;

    return <MainContainer
        main={<>
            {Error}
            <h3>Wallet Owner</h3>
            <hr/>
            <div>
                Type: {allocation.wallet.owner.type}
            </div>
            <div>
                Title: {allocation.wallet.owner.type === "user" ? allocation.wallet.owner.username
                : <ProjectTitleForNewCore id={allocation.wallet.owner.projectId ?? ""}/>}
            </div>
            <div>
                {allocation.wallet.owner.type === "project" ? <>
                    ID: {allocation.wallet.owner.projectId}
                        <Link target={"_blank"}
                              to={buildQueryString(supportAssist.project(), {id: allocation.wallet.owner.projectId})}>
                            <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                        </Link>
                    </>
                    : ""
                }
            </div>
            <br/>

            <h3>Wallet Info</h3>
            <hr/>
            <WalletTable wallets={new Array(allocation.wallet)} />



            <h3>Accounting Graph</h3>
            <hr/>
            <br/>

            {allocation.accountingGraph != null ? <div className="mermaid">
                <RenderMermaid mermaidCode={allocation.accountingGraph} mermaidConfig={{theme: "dark"}} />
            </div> : null }
        </>}
    />
}

// Note(Jonas): I have no idea if this makes any sense. It's the same amount of lines in the components that use it.
function useError(): {setError: (e: string) => void; Error: React.ReactNode} {
    const [error, setError] = React.useState("");
    return {setError, Error: <Error error={error} />}
}


function WalletTable(props: {wallets?: WalletV2[]}): React.ReactNode {
    const wallets = props.wallets;
    return <>
        <table>
            <thead>
            <tr>
                <th>
                    Resource
                </th>
                <th style={{width: "20px"}}></th>
                <th>
                    Quota
                </th>
                <th style={{width: "20px"}}></th>
                <th>
                    Local Usage
                </th>
                <th style={{width: "20px"}}></th>
                <th>
                    Total Usage
                </th>
                <th style={{width: "20px"}}></th>
                <th>
                    Max Usage
                </th>
                <th style={{width: "20px"}}></th>
                <th>
                    Allocated to subprojects
                </th>
            </tr>
            </thead>
            {
                wallets?.map(resource =>
                    <tbody>
                    <tr key={resource.paysFor.name + "("+resource.paysFor.provider+")"}>
                        <td align={"left"}> {resource.paysFor.name} ({resource.paysFor.provider}) {resource.paysFor.accountingUnit.name} {normalizeFrequency(resource.paysFor.accountingFrequency)} </td> <td/>
                        <td align={"right"}> {resource.quota} </td> <td/>
                        <td align={"right"}> {resource.localUsage} </td> <td/>
                        <td align={"right"}> {resource.totalUsage} </td> <td/>
                        <td align={"right"}> {resource.maxUsable} </td> <td/>
                        <td align={"right"}> {resource.totalAllocated}</td> <td/>
                        <td align={"right"}> {(resource.maxUsable + resource.totalUsage - resource.quota) !== 0 ? <Icon name={"heroExclamationTriangle"} color={"warningMain"} /> : null } </td>
                    </tr>
                    </tbody>
                )
            }
        </table>
        <br/>
        <h3>Allocations</h3>
        <hr/>
        {wallets?.map(resource => resource.allocationGroups.length !== 0 ? <>
            <u>{resource.paysFor.name} ({resource.paysFor.provider})</u>
                <br/>
                    <table>
                    <thead>
                    <tr>
                        <th>
                            Id
                        </th>
                        <th style={{width: "20px"}}></th>
                        <th>
                            Resource
                        </th>
                        <th style={{width: "20px"}}></th>
                        <th>
                            Quota
                        </th>
                        <th style={{width: "20px"}}></th>
                        <th>
                            Start Date
                        </th>
                        <th style={{width: "20px"}}></th>
                        <th>
                            End Date
                        </th>
                        <th style={{width: "20px"}}></th>
                        <th>
                            Granted In
                        </th>
                    </tr>
                    </thead>
                        <tbody>
                        {
                        resource.allocationGroups.map(group =>
                            group.group?.allocations.map(alloc =>
                                <tr key={alloc.id}>
                                    <td align={"right"}> {alloc.id}
                                        <Link target={"_blank"}
                                              to={buildQueryString(supportAssist.allocation(), {id: alloc.id})}>
                                        {" "}
                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                    </Link> </td>
                                    <td/>
                                    <td align={"left"}> {resource.paysFor.name} ({resource.paysFor.provider}) {resource.paysFor.accountingUnit.name} {normalizeFrequency(resource.paysFor.accountingFrequency)} </td>
                                    <td/>
                                    <td align={"right"}> {alloc.quota} </td>
                                    <td/>
                                    <td align={"right"}> {dateToString(alloc.startDate)} </td>
                                    <td/>
                                    <td align={"right"}> {dateToString(alloc.endDate)} </td>
                                    <td/>
                                    <td align={"right"}> {alloc.grantedIn}
                                        {alloc.grantedIn && <>
                                            <Link target={"_blank"}
                                                  to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                {" "}
                                                <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                            </Link>
                                        </>}
                                    </td>
                                </tr>
                            )
                        )
                    }
                    </tbody>
                </table>
            <br/>
            </> : null
        )}
        <br/>
    </>
}

function GrantsTable(props: {grants?: Application[]}): React.ReactNode {
    const grants = props.grants;
    return <table>
            <thead>
                <tr>
                    <th align={"left"}>Application ID</th>
                    <th style={{width: "20px"}}></th>
                    <th align={"left"}>Created By</th>
                    <th style={{width: "20px"}}></th>
                    <th align={"left"}>Creation Time</th>
                    <th style={{width: "20px"}}></th>
                    <th align={"left"}>Last Update</th>
                    <th style={{width: "20px"}}></th>
                    <th align={"left"}>Resources requested</th>
                    <th style={{width: "20px"}}></th>
                    <th align={"center"}>Status</th>
                </tr>
            </thead>
            {
                (grants != null ) ? grants.map(it =>
                    <tbody>
                        <tr>
                            <td align={"right"}>
                                {it.id}
                                <Link target={"_blank"}
                                      to={AppRoutes.grants.editor(it.id)}>
                                    {" "}
                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                </Link>
                            </td>
                            <td/>
                            <td align={"center"}>
                                {it.createdBy}
                                <Link target={"_blank"}
                                      to={buildQueryString(supportAssist.user(), {id: it.createdBy})}>
                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                </Link>
                            </td>
                            <td/>
                            <td align={"center"}>
                                {dateToString(it.createdAt)}
                            </td>
                            <td/>
                            <td align={"center"}>
                                {dateToString(it.updatedAt)}
                            </td>
                            <td/>
                            <td align={"center"}>
                                <hr/>
                                {it.currentRevision.document.allocationRequests.map(request =>
                                    <>{request.category} ({request.provider})<br/></>
                                )}
                                <hr/>
                            </td>
                            <td/>
                            <td align={"center"}>
                                {it.status.overallState}
                            </td>
                        </tr>
                    </tbody>
                ) : null }
        </table>

}

const Api = {
    retrieveProjectInfo(params: SupportAssistRetrieveProjectInfoRequest): APICallParameters<SupportAssistRetrieveProjectInfoRequest, SupportAssistRetrieveProjectInfoResponse> {
        return apiRetrieve(params, "api/supportAssistOrc", "projectInfo");
    },

    retrieveJobInfo(params: SupportAssistRetrieveJobInfoRequest): APICallParameters<SupportAssistRetrieveJobInfoRequest, SupportAssistRetrieveJobInfoResponse> {
        return apiRetrieve(params, "/api/supportAssistOrc", "jobInfo");
    },

    retrieveWalletInfo(params: SupportAssistRetrieveWalletInfoRequest): APICallParameters<SupportAssistRetrieveWalletInfoRequest, SupportAssistRetrieveWalletInfoResponse> {
        return apiRetrieve(params, "api/supportAssistOrc", "walletsInfo");
    },

    retrieveUserInfo(params: SupportAssistRetrieveUserInfoRequest): APICallParameters<SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse> {
        return apiRetrieve(params, "api/supportAssistAcc", "userInfo");
    }
}