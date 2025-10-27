import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Button, Checkbox, Flex, Heading, Input, Label, MainContainer, Error} from "@/ui-components";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import AppRoutes from "@/Routes";
import {AccountingFrequency, Allocation, WalletV2} from "@/Accounting";
import {Project} from "@/Project";
import {apiRetrieve, callAPI} from "@/Authentication/DataHook";
import {capitalized, errorMessageOrDefault} from "@/UtilityFunctions";
import {mail} from "@/UCloud";
import {Application} from "@/Grants";
import EmailSettings = mail.EmailSettings;
import {Job} from "@/UCloud/JobsApi";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {user} from "@/ui-components/icons";
import {width} from "styled-system";
import {dateToString} from "@/Utilities/DateUtilities";
import allocations from "@/Accounting/Allocations";
const {supportAssist} = AppRoutes;

// Note(Jonas): Maybe a bit overengineered?
enum Index {
    MembersAndInvites = 0,
    AccountingInfo = 1,
    RecentJobs = 2,
    IncludeGraph = 3,
};

export default function () {
    usePage("Support", SidebarTabId.ADMIN);

    const navigate = useNavigate();

    const userRef = React.useRef<HTMLInputElement>(null);
    const emailRef = React.useRef<HTMLInputElement>(null);
    const projectRef = React.useRef<HTMLInputElement>(null);
    const jobRef = React.useRef<HTMLInputElement>(null);
    const allocationRef = React.useRef<HTMLInputElement>(null);

    const [checkMarks, setChecks] = React.useState([false, false, false, false]);

    const toggleCheckbox = React.useCallback((idx: number) => {
        setChecks(checks => {
            checks[idx] = !checks[idx];
            return [...checks];
        });
    }, []);

    const membersAndInvites = checkMarks[Index.MembersAndInvites];
    const accountingInfo = checkMarks[Index.AccountingInfo];
    const recentJobs = checkMarks[Index.RecentJobs];
    const includeGraph = checkMarks[Index.IncludeGraph];

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
                        navigateTo(supportAssist.project(), projectRef.current?.value, {
                            includeMembers: membersAndInvites,
                            includeAccountingInfo: accountingInfo,
                            includeJobsInfo: recentJobs
                        })
                    }}>
                        <Flex>
                            <Label>
                                Project ID
                                <Input placeholder="Type project ID..." inputRef={projectRef} />
                            </Label>
                            <SearchButton />
                        </Flex>
                    </form>

                    <Label mr="12px">
                        <Checkbox checked={membersAndInvites} onChange={() => toggleCheckbox(Index.MembersAndInvites)} />
                        Members and invites
                    </Label>

                    <Label mr="12px">
                        <Checkbox checked={accountingInfo} onChange={() => toggleCheckbox(Index.AccountingInfo)} />
                        Project accounting information
                    </Label>

                    <Label mr="12px">
                        <Checkbox checked={recentJobs} onChange={() => toggleCheckbox(Index.RecentJobs)} />
                        Most recent jobs
                    </Label>
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
                        navigateTo(supportAssist.allocation(), allocationRef.current?.value, {includeGraph});
                    }}>
                        <Flex>
                            <Label>
                                Allocation ID
                                <Input placeholder="Type allocation ID..." inputRef={allocationRef} />
                            </Label>
                            <SearchButton />
                        </Flex>
                    </form>
                    <Label mr="12px">
                        <Checkbox checked={includeGraph} onChange={() => toggleCheckbox(Index.IncludeGraph)} />
                        Include graph
                    </Label>
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

type SupportAssistRetrieveUserInfoRequest = {
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

export function normalizeFrequency(frequency: AccountingFrequency):string {
    if (frequency === "PERIODIC_MINUTE") { return "minute(s)"}
    if (frequency === "PERIODIC_HOUR") { return "hour(s)"}
    if (frequency === "PERIODIC_DAY") { return "day(s)"}
    if (frequency === "ONCE") { return ""}
    return ""
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
    console.log(userInfo)
    return <MainContainer
        main={<>
            {Error}
            <div>
                {userInfo.info === null ? `0 entries found` : null}
                {userInfo.info?.length > 1 ? `${userInfo.info?.length} entries found` : null}

                {userInfo.info?.map(it => <div>
                    <h3>Personal Info:</h3>
                    <hr/>
                    <div>Name: {it.firstNames} {it.lastName}</div>
                    <div>Email: {it.email}</div>
                    <br/>

                    <h3>E-mail settings:</h3>
                    <hr/>
                    <div>
                        {
                            <ul style={{ listStyle: "none" }}>
                                {Object.keys(it.emailSettings).map(key =>
                                    <li>
                                        <input type={"checkbox"} checked={it.emailSettings[key]}/> {capitalized(key)}
                                    </li>
                                )}
                            </ul>
                        }
                    </div>
                    <br/>

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
                        {
                            it.associatedProjects.sort((a,b) => {
                                return a.specification.title.localeCompare(b.specification.title)
                            }).map(project =>
                                <tbody>
                                    <tr>
                                        <td>{project.specification.title}</td>
                                        <td/>
                                        <td>({project.id})</td>
                                        <td/>
                                        {project.status.members?.map(member =>
                                            member.username === it.username ? <td>{member.role}</td> : null
                                        )}
                                    </tr>
                                </tbody>
                            )
                        }
                        </table>
                    </div>
                    <br/>

                    <h3>Active grants:</h3>
                    <hr/>
                    <div>
                        <table>
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
                            </tr>
                            {
                                (it.activeGrants !== null ) ? it.activeGrants.map(it =>
                                    <tr>
                                        <td>
                                            {it.id}
                                        </td>
                                        <td/>
                                        <td>
                                            {it.createdBy}
                                        </td>
                                        <td/>
                                        <td>
                                            {dateToString(it.createdAt)}
                                        </td>
                                        <td/>
                                        <td>
                                            {dateToString(it.updatedAt)}
                                        </td>
                                        <td/>
                                        <td>
                                            {it.currentRevision.document.allocationRequests.map(request =>
                                            <> {request.category} ({request.provider}) <br/></>
                                            )}
                                        </td>
                                    </tr>
                                ) : null }
                        </table>
                    </div>
                    <br/>

                    <h3>Personal project resources</h3>
                    <hr/>
                    <div>
                        <table>
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
                            {
                                it.personalProjectResources.map(resource =>
                                    <tr>
                                        <td align={"left"}> {resource.paysFor.name} ({resource.paysFor.provider}) {resource.paysFor.accountingUnit.name} {normalizeFrequency(resource.paysFor.accountingFrequency)} </td> <td/>
                                        <td align={"right"}> {resource.quota} </td> <td/>
                                        <td align={"right"}> {resource.localUsage} </td> <td/>
                                        <td align={"right"}> {resource.totalUsage} </td> <td/>
                                        <td align={"right"}> {resource.maxUsable} </td> <td/>
                                        <td align={"right"}> {resource.totalAllocated}</td>
                                    </tr>
                                )
                            }
                        </table>
                    </div>
                    <hr/>

                </div>)}
            </div>
        </>}
    />
}

// PROJECT
interface SupportAssistRetrieveProjectInfoRequest {
    projectId: string;
    flags: SupportAssistProjectInfoFlags;
}

interface SupportAssistProjectInfoFlags {
    includeMembers: boolean;
    includeAccountingInfo: boolean;
    includeJobsInfo: boolean;
}

interface SupportAssistRetrieveProjectInfoResponse {
    project: Project;
    projectWallets?: WalletV2[];
    accountingIssues?: WalletIssue[];
    jobs?: Job[];
}

interface WalletIssue {
    AssociatedAllocation: Allocation;
    ProblematicWallet: WalletV2;
    description: string;
}

export function ProjectSupportContent() {
    usePage("Project support", SidebarTabId.ADMIN);
    const location = useLocation();
    const projectId = getQueryParam(location.search, "id");
    const includeMembers = getQueryParam(location.search, "includeMembers") === "true";
    const includeAccountingInfo = getQueryParam(location.search, "includeAccountingInfo") === "true";
    const includeJobsInfo = getQueryParam(location.search, "includeJobsInfo") === "true";

    const {Error, setError} = useError();
    const [project, setProject] = React.useState<SupportAssistRetrieveProjectInfoResponse | null>(null);

    React.useEffect(() => {
        if (!projectId) return;
        callAPI(Api.retrieveProjectInfo({
            projectId,
            flags: {
                includeMembers,
                includeAccountingInfo,
                includeJobsInfo
            }
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
                <div>Parent project ID: {projecttitleproject.project.specification.parent}</div>
                <br/>

                Wallets:
                <hr/>
                <div>
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
                            project.projectWallets !== undefined && project.projectWallets !== null ? project.projectWallets.map(resource =>
                                <tbody>
                                    <tr>
                                        <td align={"left"}> {resource.paysFor.name} ({resource.paysFor.provider}) {resource.paysFor.accountingUnit.name} {normalizeFrequency(resource.paysFor.accountingFrequency)} </td> <td/>
                                        <td align={"right"}> {resource.quota} </td> <td/>
                                        <td align={"right"}> {resource.localUsage} </td> <td/>
                                        <td align={"right"}> {resource.totalUsage} </td> <td/>
                                        <td align={"right"}> {resource.maxUsable} </td> <td/>
                                        <td align={"right"}> {resource.totalAllocated}</td>
                                    </tr>
                                </tbody>
                            ) : null
                        }
                    </table>
                </div>
                <br/>

                Accounting Issues:
                <hr/>
                <div>
                    {project.accountingIssues !== null && project.accountingIssues !== undefined ? project.accountingIssues.map(it => <>
                        Allocation {it.AssociatedAllocation.id} {it.description}
                    </>) : null}
                </div>
                <br/>

                Jobs:
                <hr/>
                <div>
                    {project.jobs !== null && project.jobs !== undefined ? project.jobs.map(it => <>
                        ID:
                        {it.id}
                    </>) : null }
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
        header={"Job"}
        main={<>
            {Error}
            <div>
                {job.jobInfo.id}
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
            {allocationId: id, flags}))
            .then(result =>
                setAllocation(result)
            )
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch")));
    }, [id, flags]);

    if (!Client.userIsAdmin || allocation == null) return null;

    return <MainContainer
        main={<>
            {Error}
            <div>Owner: {allocation.wallet.owner.type === "user" ? allocation.wallet.owner.username : allocation.wallet.owner.projectId}</div>
            <hr/>
            <br/>

            <div>
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
                        <tbody>
                        <tr>
                            <td align={"left"}> {allocation.wallet.paysFor.name} ({allocation.wallet.paysFor.provider}) {allocation.wallet.paysFor.accountingUnit.name} {normalizeFrequency(allocation.wallet.paysFor.accountingFrequency)} </td> <td/>
                            <td align={"right"}> {allocation.wallet.quota} </td> <td/>
                            <td align={"right"}> {allocation.wallet.localUsage} </td> <td/>
                            <td align={"right"}> {allocation.wallet.totalUsage} </td> <td/>
                            <td align={"right"}> {allocation.wallet.maxUsable} </td> <td/>
                            <td align={"right"}> {allocation.wallet.totalAllocated}</td>
                        </tr>
                        </tbody>
                </table>
                <hr/>
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
                    </tr>
                    </thead>
                {
                    allocation.wallet.allocationGroups !== undefined && allocation.wallet.allocationGroups !== null ? allocation.wallet.allocationGroups.map(group =>
                        group.group !== undefined && group.group !== null ? group.group.allocations.map(allocs =>
                            <tbody>
                            <tr>
                                <td align={"left"}> {allocs.id} </td> <td/>
                                <td align={"left"}> {allocation.wallet.paysFor.name} ({allocation.wallet.paysFor.provider}) {allocation.wallet.paysFor.accountingUnit.name} {normalizeFrequency(allocation.wallet.paysFor.accountingFrequency)} </td> <td/>
                                <td align={"right"}> {allocs.quota} </td> <td/>
                                <td align={"right"}> {allocs.startDate} </td> <td/>
                                <td align={"right"}> {allocs.endDate} </td> <td/>
                            </tr>
                            </tbody>
                        ) : null
                    ) : null
                }
                </table>
            </div>
            <br/>
        </>}
    />
}

// Note(Jonas): I have no idea if this makes any sense. It's the same amount of lines in the components that use it.
function useError(): {setError: (e: string) => void; Error: React.ReactNode} {
    const [error, setError] = React.useState("");
    return {setError, Error: <Error error={error} />}
}


const Api = {
    retrieveProjectInfo(params: SupportAssistRetrieveProjectInfoRequest): APICallParameters<SupportAssistRetrieveProjectInfoRequest, SupportAssistRetrieveProjectInfoResponse> {
        return apiRetrieve(params, "api/support-assist-orc", "project_info");
    },

    retrieveJobInfo(params: SupportAssistRetrieveJobInfoRequest): APICallParameters<SupportAssistRetrieveJobInfoRequest, SupportAssistRetrieveJobInfoResponse> {
        return apiRetrieve(params, "/api/support-assist-orc", "job_info");
    },

    retrieveWalletInfo(params: SupportAssistRetrieveWalletInfoRequest): APICallParameters<SupportAssistRetrieveWalletInfoRequest, SupportAssistRetrieveWalletInfoResponse> {
        return apiRetrieve(params, "api/support-assist-orc", "wallets_info");
    },

    retrieveUserInfo(params: SupportAssistRetrieveUserInfoRequest): APICallParameters<SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse> {
        return apiRetrieve(params, "api/support-assist-acc", "user_info");
    }
}