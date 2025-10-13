import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Button, Checkbox, Flex, Heading, Input, Label, MainContainer, Error} from "@/ui-components";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import AppRoutes from "@/Routes";
import {Allocation, WalletV2} from "@/Accounting";
import {Project} from "@/Project";
import {apiRetrieve, callAPI} from "@/Authentication/DataHook";
import {capitalized, errorMessageOrDefault} from "@/UtilityFunctions";
import {mail} from "@/UCloud";
import {Application} from "@/Grants";
import EmailSettings = mail.EmailSettings;
import {Job} from "@/UCloud/JobsApi";
const {supportAssist} = AppRoutes;

// Note(Jonas): Maybe a bit overengineered?
enum Index {
    MembersAndInvites = 0,
    AccountingInfo = 1,
    RecentJobs = 2,
    IncludeGraph = 3,
};

export default function () {
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
                                <Input placeholder="Type allocation/wallet ID..." inputRef={allocationRef} />
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
} | {
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
    personalProjectResources: WalletV2;
}

interface SupportAssistRetrieveUserInfoResponse {
    info: SupportAssistUserInfo[]
}

export function UserSupportContent() {
    const location = useLocation();
    const user = getQueryParam(location.search, "id");
    const isEmail = getQueryParam(location.search, "isEmail");

    const [error, setError] = React.useState("");
    const [userInfo, setInfo] = React.useState<SupportAssistRetrieveUserInfoResponse | null>({info: []});

    React.useEffect(() => {
        if (!user) return;
        callAPI(Api.retrieveUserInfo(isEmail ? {email: user} : {username: user}))
            .then(result => setInfo(result))
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch user")));
    }, [user, isEmail]);

    if (!Client.userIsAdmin || userInfo == null) return null;

    return <MainContainer
        main={<div>
            <Error error={error} />
            {user} {userInfo.info.length > 1 ? `${userInfo.info.length} entries found` : null}

            {userInfo.info.map(it => <div>
                E-mail
                <div>{it.email}</div>
                Name
                <div>{it.firstNames} {it.lastName}</div>
                E-mail settings
                <div>{Object.keys(it.emailSettings).map(key => <>{capitalized(key)}: {it.emailSettings[key]} </>)}</div>
                Associated projects
                <div>{it.associatedProjects.map(it => it.specification.title)}</div>
                Active grants
                <div>{it.activeGrants.map(it => <>
                    ID:
                    {it.id}
                    Created by:
                    {it.createdBy}
                    Created at:
                    {it.createdAt}
                    Current revision:
                    {it.currentRevision.revisionNumber}
                    Updated at:
                    {it.updatedAt}
                </>)}</div>
                Personal project resources
                <div>TODO</div>
            </div>)}
        </div>}
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
    const location = useLocation();
    const projectId = getQueryParam(location.search, "id");
    const includeMembers = getQueryParam(location.search, "includeMembers") === "true";
    const includeAccountingInfo = getQueryParam(location.search, "includeAccountingInfo") === "true";
    const includeJobsInfo = getQueryParam(location.search, "includeJobsInfo") === "true";

    const [error, setError] = React.useState("");
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
        main={<div>
            <Error error={error} />
        </div>}
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
    const [error, setError] = React.useState("");
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
            <Error error={error} />
            <div>
                {job.jobInfo.id}
            </div>
        </>}
    />
}

// ALLOCATION/WALLET

type SupportAssistRetrieveWalletInfoRequest = ({
    allocationId: string;
} | {
    walletId: string;
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
    const isWalletId = getQueryParam(location.search, "isWalletId") === "true";
    const flags = {
        includeAccountingGraph: getQueryParam(location.search, "includeAccountingGraph") === "true"
    }
    const [error, setError] = React.useState("");
    const [allocation, setAllocation] = React.useState<SupportAssistRetrieveWalletInfoResponse | null>(null);

    React.useEffect(() => {
        if (!id) return;
        // or wallet id? handle graph thing flag
        callAPI(Api.retrieveWalletInfo(isWalletId ?
            {walletId: id, flags} :
            {allocationId: id, flags}))
            .then(setAllocation)
            .catch(e => setError(errorMessageOrDefault(e, "Failed to fetch")));
    }, [id, isWalletId, flags]);

    if (!Client.userIsAdmin || allocation == null) return null;

    return <MainContainer
        main={<>
            <Error error={error} />
            Allocation group count
            {allocation.wallet.allocationGroups.length}
        </>}
    />
}


const Api = {
    retrieveProjectInfo(params: SupportAssistRetrieveProjectInfoRequest): APICallParameters<SupportAssistRetrieveProjectInfoRequest, SupportAssistRetrieveProjectInfoResponse> {
        return apiRetrieve(params, "api/support-assist-orc/retrieve_project_info");
    },

    retrieveJobInfo(params: SupportAssistRetrieveJobInfoRequest): APICallParameters<SupportAssistRetrieveJobInfoRequest, SupportAssistRetrieveJobInfoResponse> {
        return apiRetrieve(params, "/api/support-assist-orc/retrieve_job_info");
    },

    retrieveWalletInfo(params: SupportAssistRetrieveWalletInfoRequest): APICallParameters<SupportAssistRetrieveWalletInfoRequest, SupportAssistRetrieveWalletInfoResponse> {
        return apiRetrieve(params, "api/support-assist-orc/retrieve_wallets_info");
    },

    retrieveUserInfo(params: SupportAssistRetrieveUserInfoRequest): APICallParameters<SupportAssistRetrieveUserInfoRequest, SupportAssistRetrieveUserInfoResponse> {
        return apiRetrieve(params, "api/support-assist-acc/retrieve_user_info");
    }
}