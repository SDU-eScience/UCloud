import {MachineTypes} from "Applications/MachineTypes";
import {OptionalParameters} from "Applications/OptionalParameters";
import {InputDirectoryParameter} from "Applications/Widgets/FileParameter";
import {AdditionalPeerParameter} from "Applications/Widgets/PeerParameter";
import {callAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {File as CloudFile, SortBy, SortOrder} from "Files";
import FileSelector from "Files/FileSelector";
import {listDirectory} from "Files/LowLevelFileTable";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage, setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import * as ReactModal from "react-modal";
import {
    Box,
    Button,
    ContainerForText,
    Flex,
    Label,
    OutlineButton,
    VerticalButtonGroup,
    Icon
} from "ui-components";
import BaseLink from "ui-components/BaseLink";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField} from "ui-components/Input";
import Link from "ui-components/Link";
import {
    checkForMissingParameters,
    extractValuesFromWidgets,
    findKnownParameterValues,
    hpcFavoriteApp,
    hpcJobQueryPost,
    isFileOrDirectoryParam,
    validateOptionalFields
} from "Utilities/ApplicationUtilities";
import {removeEntry} from "Utilities/CollectionUtilities";
import {
    checkIfFileExists,
    expandHomeOrProjectFolder,
    fetchFileContent,
    fileTablePage, getFilenameFromPath,
    statFileQuery
} from "Utilities/FileUtilities";
import {addStandardDialog} from "UtilityComponents";
import {errorMessageOrDefault, removeTrailingSlash} from "UtilityFunctions";
import {
    AdditionalMountedFolder,
    ApplicationParameter,
    FullAppInfo,
    JobSchedulingOptionsForInput,
    ParameterTypes,
    RunAppProps,
    RunAppState,
    RunOperations,
    WithAppInvocation,
    WithAppMetadata
} from ".";
import {PRODUCT_NAME} from "../../site.config.json";
import {AppHeader} from "./View";
import {Parameter} from "./Widgets/Parameter";
import {RangeRef} from "./Widgets/RangeParameters";
import {TextSpan} from "ui-components/Text";
import Warning from "ui-components/Warning";
import {getQueryParam, RouterLocationProps} from "Utilities/URIUtilities";
import * as PublicLinks from "Applications/PublicLinks/Management";
import {creditFormatter} from "Project/ProjectUsage";
import {Product, retrieveBalance, RetrieveBalanceResponse} from "Accounting";
import {MandatoryField} from "Applications/Widgets/BaseParameter";
import {SidebarPages} from "ui-components/Sidebar";
import {Toggle} from "ui-components/Toggle";
import {IPAddressManagement} from "Applications/IPAddresses/Management";
import {defaultModalStyle} from "Utilities/ModalUtilities";

const hostnameRegex = new RegExp(
    "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
    "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$"
);
const NO_WALLET_FOUND_VALUE = 0;

class Run extends React.Component<RunAppProps & RouterLocationProps, RunAppState> {
    constructor(props: Readonly<RunAppProps & RouterLocationProps>) {
        super(props);

        this.state = {
            promises: new PromiseKeeper(),
            jobSubmitted: false,
            initialSubmit: false,
            parameterValues: new Map(),
            mountedFolders: [],
            additionalPeers: [],
            schedulingOptions: {
                maxTime: {
                    hours: 0,
                    minutes: 0,
                    seconds: 0
                },
                numberOfNodes: 1,
                name: React.createRef(),
            },

            useUrl: false,
            url: React.createRef(),

            useIp: false,
            ip: React.createRef(),

            favorite: false,
            favoriteLoading: false,
            fsShown: false,
            previousRuns: emptyPage,
            reservation: "",
            unknownParameters: [],
            balance: NO_WALLET_FOUND_VALUE,
            inlineError: undefined
        };
    }

    public componentDidMount(): void {
        this.props.onInit();
        const name = this.props.match.params.appName;
        const version = this.props.match.params.appVersion;
        this.state.promises.makeCancelable(this.retrieveApplication(name, version));

        const paramsFile = getQueryParam(location.search, "paramsFile");
        if (paramsFile !== null) {
            this.fetchAndImportParameters({path: paramsFile});
        }
    }

    public componentWillUnmount = (): void => this.state.promises.cancelPromises();

    public componentDidUpdate(
        prevProps: Readonly<RunAppProps & RouterLocationProps>,
        prevState: Readonly<RunAppState>
    ): void {
        if (prevProps.match.params.appName !== this.props.match.params.appName ||
            prevProps.match.params.appVersion !== this.props.match.params.appVersion) {
            this.state.promises.makeCancelable(
                this.retrieveApplication(this.props.match.params.appName, this.props.match.params.appVersion)
            );
        }

        if (prevState.application !== this.state.application && this.state.application !== undefined) {
            this.fetchPreviousRuns();
        }

        const paramsFile = getQueryParam(location.search, "paramsFile");
        const prevParamsFile = getQueryParam(prevProps.location.search ?? "", "paramsFile");
        if (paramsFile !== prevParamsFile && paramsFile !== null) {
            this.fetchAndImportParameters({path: paramsFile});
        }

        if (this.props.project !== prevProps.project && this.state.reservationMachine !== undefined) {
            this.getBalance(this.state.reservationMachine.category.id, this.state.reservationMachine.category.provider);
        }
    }

    private async getBalance(productCategory: string, productProvider: string): Promise<void> {
        const req = retrieveBalance({
            id: undefined,
            type: undefined,
            includeChildren: false
        });
        const {response} = await Client.get<RetrieveBalanceResponse>(req.path!);
        const balance = response.wallets.find(({wallet}) =>
            wallet.paysFor.provider === productProvider && wallet.paysFor.id === productCategory
        )?.balance ?? NO_WALLET_FOUND_VALUE;
        this.setState({balance});
    }

    public render(): JSX.Element {
        const {application, jobSubmitted, schedulingOptions, parameterValues} = this.state;
        if (!application) return <MainContainer main={<LoadingIcon size={36} />} />;

        const parameters = application.invocation.parameters;
        const mandatory = parameters.filter(parameter => !parameter.optional);
        const visible = parameters.filter(parameter =>
            parameter.optional && (parameter.visible === true || parameterValues.get(parameter.name)?.current != null)
        );
        const optional = parameters.filter(parameter =>
            parameter.optional && parameter.visible !== true && parameterValues.get(parameter.name)?.current == null);

        const onParameterChange = (parameter: ApplicationParameter, isVisible: boolean): void => {
            parameter.visible = isVisible;
            if (!isVisible) {
                parameterValues.set(parameter.name, React.createRef<HTMLSelectElement | HTMLInputElement>());
            }
            this.setState(() => ({application: this.state.application}));
        };

        const mapParamToComponent = (parameter: ApplicationParameter): JSX.Element => {
            const ref = parameterValues.get(parameter.name)!;

            function handleParamChange(): void {
                onParameterChange(parameter, false);
            }

            return (
                <Parameter
                    key={parameter.name}
                    initialSubmit={this.state.initialSubmit}
                    parameterRef={ref}
                    parameter={parameter}
                    onParamRemove={handleParamChange}
                    application={application}
                />
            );
        };

        const mandatoryParams = mandatory.map(mapParamToComponent);
        const visibleParams = visible.map(mapParamToComponent);
        const {unknownParameters} = this.state;

        const estimatedCost = (
            (this.state.reservationMachine?.pricePerUnit ?? 0) * (
                this.state.schedulingOptions.maxTime.hours * 60 +
                this.state.schedulingOptions.maxTime.minutes +
                (this.state.schedulingOptions.maxTime.seconds > 0 ? 1 : 0)
            ) * this.state.schedulingOptions.numberOfNodes);

        return (
            <MainContainer
                headerSize={48}
                header={(
                    <Flex mx={["0px", "0px", "0px", "0px", "0px", "50px"]}>
                        <AppHeader slim application={application} />
                    </Flex>
                )}

                sidebar={(
                    <VerticalButtonGroup>
                        <OutlineButton
                            onClick={() => importParameterDialog(
                                file => this.importParameters(file),
                                () => this.setState(() => ({fsShown: true}))
                            )}
                            fullWidth
                            color="darkGreen"
                            as="label"
                        >
                            Import parameters
                        </OutlineButton>
                        <Button fullWidth disabled={this.state.favoriteLoading} onClick={() => this.toggleFavorite()}>
                            {this.state.favorite ? "Remove from favorites" : "Add to favorites"}
                        </Button>
                        <Button
                            type="button"
                            onClick={this.onSubmit}
                            disabled={jobSubmitted}
                            color="blue"
                        >
                            Submit
                        </Button>
                        <Box mt={32} color={this.state.balance >= estimatedCost ? "black" : "red"} textAlign="center">
                            {!this.state.reservationMachine ? null : (
                                <>
                                    <Icon name={"grant"} />{" "}
                                    Estimated cost: <br />

                                    {creditFormatter(estimatedCost, 3)}
                                </>
                            )}
                        </Box>
                        <Box mt={32} color="black" textAlign="center">
                            {!this.state.reservationMachine ? null : (
                                <>
                                    <Icon name="grant" />{" "}
                                    Current balance: <br />

                                    {creditFormatter(this.state.balance)}
                                </>
                            )}
                        </Box>
                    </VerticalButtonGroup>
                )}

                additional={(
                    <FileSelector
                        onFileSelect={it => {
                            if (it) this.onImportFileSelected(it);
                            this.setState(() => ({fsShown: false}));
                        }}
                        trigger={null}
                        visible={this.state.fsShown}
                    />
                )}

                main={(
                    <ContainerForText>
                        <form onSubmit={this.onSubmit} style={{width: "100%"}}>
                            {
                                this.state.previousRuns.items.length <= 0 ? null : (
                                    <RunSection>
                                        <Label>Load parameters from a previous run:</Label>
                                        <Flex flexDirection="row" flexWrap="wrap">
                                            {
                                                this.state.previousRuns.items.slice(0, 5).map((file, idx) => (
                                                    <Box mr="0.8em" key={idx}>
                                                        <BaseLink
                                                            href="#"
                                                            onClick={async e => {
                                                                e.preventDefault();

                                                                try {
                                                                    this.props.setLoading(true);
                                                                    await this.fetchAndImportParameters(
                                                                        {path: `${file.path}/JobParameters.json`}
                                                                    );
                                                                } catch (ex) {
                                                                    snackbarStore.addFailure(
                                                                        "Could not find a parameters file for this " +
                                                                        "run. Try a different run.",
                                                                        false
                                                                    );
                                                                } finally {
                                                                    this.props.setLoading(false);
                                                                }
                                                            }}
                                                        >
                                                            {getFilenameFromPath(file.path, [])}
                                                        </BaseLink>
                                                    </Box>
                                                ))
                                            }
                                        </Flex>
                                    </RunSection>
                                )}
                            {!unknownParameters.length ? null : (
                                <Error
                                    error={"Could not add parameters:\n\t" + unknownParameters.join(", \n\t")}
                                    clearError={() => this.setState(() => ({unknownParameters: []}))}
                                />
                            )}

                            <RunSection>
                                <Error error={this.state.inlineError} clearError={() => this.setState({inlineError: undefined})} />
                                <JobSchedulingOptions
                                    onChange={this.onJobSchedulingParamsChange}
                                    options={schedulingOptions}
                                    reservation={this.state.reservation}
                                    setReservation={(reservation, reservationMachine) => {
                                        this.getBalance(
                                            reservationMachine.category.id,
                                            reservationMachine.category.provider
                                        );
                                        this.setState({reservation, reservationMachine});
                                    }
                                    }
                                    app={application}
                                />
                            </RunSection>

                            {mandatoryParams.length === 0 ? null : (
                                <RunSection>
                                    <Heading.h4>Mandatory Parameters ({mandatoryParams.length})</Heading.h4>
                                    {mandatoryParams}
                                </RunSection>
                            )}

                            {visibleParams.length <= 0 ? null : (
                                <RunSection>
                                    <Heading.h4>Additional Parameters Used</Heading.h4>
                                    {visibleParams}
                                </RunSection>
                            )}

                            {
                                !application.invocation.shouldAllowAdditionalMounts ? null : (
                                    <RunSection>
                                        <Flex alignItems="center">
                                            <Box flexGrow={1}>
                                                <Heading.h4>Select additional folders to use</Heading.h4>
                                            </Box>

                                            <Button
                                                type="button"
                                                ml="5px"
                                                lineHeight="16px"
                                                onClick={() => this.addFolder()}
                                            >
                                                Add folder
                                            </Button>
                                        </Flex>

                                        <Box mb={8} mt={8}>
                                            {this.state.mountedFolders.length !== 0 ? (
                                                <>
                                                    Your files will be available at <code>/work/</code>.
                                                </>
                                            ) : (
                                                    <>
                                                        If you need to use your {" "}
                                                        <Link
                                                            to={fileTablePage(Client.homeFolder)}
                                                            target="_blank"
                                                        >
                                                            files
                                                    </Link>
                                                        {" "}
                                                    in this job then click {" "}
                                                        <BaseLink
                                                            href="#"
                                                            onClick={e => {
                                                                e.preventDefault();
                                                                this.addFolder();
                                                            }}
                                                        >
                                                            &quot;Add folder&quot;
                                                    </BaseLink>
                                                        {" "}
                                                    to select the relevant
                                                    files.
                                                </>
                                                )}
                                        </Box>

                                        {this.state.mountedFolders.map((entry, i) => (
                                            <Box key={i} mb="7px">
                                                <InputDirectoryParameter
                                                    application={application}
                                                    defaultValue={entry.defaultValue}
                                                    initialSubmit={false}
                                                    parameterRef={entry.ref}
                                                    unitWidth="180px"
                                                    onRemove={() => {
                                                        this.setState(s => ({
                                                            mountedFolders: removeEntry(s.mountedFolders, i)
                                                        }));
                                                    }}
                                                    parameter={{
                                                        type: ParameterTypes.InputDirectory,
                                                        name: "",
                                                        optional: true,
                                                        title: "",
                                                        description: "",
                                                        defaultValue: "",
                                                        visible: true,
                                                    }}
                                                />
                                            </Box>
                                        ))}
                                    </RunSection>
                                )}

                            {!application.invocation.shouldAllowAdditionalPeers ? null : (
                                <RunSection>
                                    <Flex alignItems={"center"}>
                                        <Box flexGrow={1}>
                                            <Heading.h4>Connect to other jobs</Heading.h4>
                                        </Box>
                                        <Button
                                            type="button"
                                            lineHeight="16px"
                                            onClick={() => this.connectToJob()}
                                        >
                                            Connect to job
                                        </Button>
                                    </Flex>
                                    <Box mb={8} mt={8}>
                                        {this.state.additionalPeers.length !== 0 ? (
                                            <>
                                                You will be able contact the <b>job</b> using its <b>hostname</b>.
                                                File systems used by the <b>job</b> are automatically added to this job.
                                            </>
                                        ) : (
                                                <>
                                                    If you need to use the services of another job click{" "}
                                                    <BaseLink
                                                        href="#"
                                                        onClick={e => {
                                                            e.preventDefault();
                                                            this.connectToJob();
                                                        }}
                                                    >
                                                        &quot;Connect to job&quot;.
                                                </BaseLink>
                                                    {" "}
                                                This includes networking.
                                            </>
                                            )}
                                    </Box>

                                    {
                                        this.state.additionalPeers.map((entry, i) => (
                                            <AdditionalPeerParameter
                                                jobIdRef={entry.jobIdRef}
                                                nameRef={entry.nameRef}
                                                onRemove={() => this.setState(s =>
                                                    ({additionalPeers: removeEntry(s.additionalPeers, i)}))
                                                }
                                                hideLabels={i !== 0}
                                                key={i}
                                            />
                                        ))
                                    }
                                </RunSection>
                            )}

                            <RunSection>
                                <NetworkingSection
                                    app={application}
                                    urlEnabled={this.state.useUrl}
                                    setUrlEnabled={() => this.setState({useUrl: !this.state.useUrl})} // TODO ??
                                    url={this.state.url}
                                    ipEnabled={this.state.useIp}
                                    setIpEnabled={() => this.setState({useIp: !this.state.useIp})}
                                    ip={this.state.ip}
                                />
                            </RunSection>

                            <RunSection>
                                {optional.length <= 0 ? null : (
                                    <OptionalParameters
                                        parameters={optional}
                                        onUse={p => onParameterChange(p, true)}
                                    />
                                )}
                            </RunSection>
                        </form>
                    </ContainerForText>
                )}
            />
        );
    }

    private async fetchPreviousRuns(): Promise<void> {
        if (this.state.application === undefined) return;
        const title = this.state.application.metadata.title;
        const path = Client.hasActiveProject ?
            `${Client.currentProjectFolder}/Personal/${Client.username}/Jobs/${title}`
            : `${Client.homeFolder}Jobs/${title}`;
        try {
            const previousRuns = await callAPI<Page<CloudFile>>(listDirectory({
                path,
                page: 0,
                itemsPerPage: 25,
                order: SortOrder.DESCENDING,
                sortBy: SortBy.MODIFIED_AT
            }));
            this.setState(() => ({previousRuns}));
        } catch {
            // Do nothing
        }
    }

    private onJobSchedulingParamsChange = (field: string | number, value: number, timeField: string): void => {
        const {schedulingOptions} = this.state;
        if (timeField) {
            schedulingOptions[field][timeField] = !isNaN(value) ? value : null;
        } else {
            schedulingOptions[field] = value;
        }
        this.setState(() => ({schedulingOptions}));
    };

    private onSubmit = async (): Promise<void> => {
        if (!this.state.application) return;
        if (this.state.jobSubmitted) return;
        const {invocation} = this.state.application;
        this.setState(() => ({initialSubmit: true}));

        if (this.state.useUrl) {
            if (this.state.url.current == null || this.state.url.current.value === "") {
                snackbarStore.addFailure(
                    "Public link is enabled, but not set",
                    false,
                    5000
                );
                this.setState(() => ({jobSubmitted: false}));
                return;
            }
        }

        const urlName = this.state.url.current == null ? null : this.state.url.current.value;

        if (this.state.useIp) {
            if (this.state.ip.current == null || this.state.ip.current.value === "") {
                snackbarStore.addFailure(
                    "IP address is enabled, but not set",
                    false,
                    5000
                );
                this.setState(() => ({jobSubmitted: false}));
                return;
            }
        }

        const ipAddress = this.state.ip.current == null ? null : this.state.ip.current.value;

        const parameters = extractValuesFromWidgets({
            map: this.state.parameterValues,
            appParameters: this.state.application!.invocation.parameters,
            client: Client
        });

        if (!checkForMissingParameters(parameters, invocation)) return;
        if (!validateOptionalFields(invocation, this.state.parameterValues)) return;

        // Validate max time
        const maxTime = extractJobInfo(this.state.schedulingOptions).maxTime;
        if (maxTime.hours === 0 && maxTime.minutes === 0) {
            snackbarStore.addFailure("Scheduling times must be more than 0 minutes.", false, 5000);
            return;
        }

        // Validate machine type is set
        if (this.state.reservationMachine === undefined) {
            snackbarStore.addFailure("You must select a machine type", false, 5000);
            return;
        }

        const mounts = this.state.mountedFolders.filter(it => it.ref.current && it.ref.current.value).map(it => {
            const expandedValue = it.ref.current!.dataset.path as string;
            return {
                source: expandedValue,
                destination: removeTrailingSlash(expandedValue).split("/").pop()!
            };
        });

        const peers = [] as Array<{name: string; jobId: string}>;
        {
            // Validate additional mounts
            for (const peer of this.state.additionalPeers) {
                const jobIdField = peer.jobIdRef.current;
                const hostnameField = peer.nameRef.current;
                if (jobIdField === null || hostnameField === null) continue; // This should not happen

                const jobId = jobIdField.value;
                const hostname = hostnameField.value;

                if (hostname === "" && jobId === "") continue;
                if (hostname === "" || jobId === "") {
                    snackbarStore.addFailure(
                        "A connection is missing a job or hostname",
                        false,
                        5000
                    );

                    return;
                }

                if (!hostnameRegex.test(hostname)) {
                    snackbarStore.addFailure(
                        `The connection '${hostname}' has an invalid hostname.` +
                        `Hostnames cannot contain spaces or special characters.`,
                        false,
                        5000
                    );
                    return;
                }

                peers.push({name: hostname, jobId});
            }
        }

        const {name} = this.state.schedulingOptions;
        const jobName = name.current?.value;
        let reservation: string | null = this.state.reservation;
        if (reservation === "") reservation = null;

        const job = {
            application: {
                name: this.state.application!.metadata.name,
                version: this.state.application!.metadata.version
            },
            parameters,
            url: urlName,
            numberOfNodes: this.state.schedulingOptions.numberOfNodes,
            maxTime,
            mounts,
            peers,
            reservation,
            type: "start",
            name: jobName !== "" ? jobName : null,
            acceptSameDataRetry: false,
            ipAddress
        };

        try {
            this.setState({jobSubmitted: true});
            this.props.setLoading(true);
            const req = await Client.post(hpcJobQueryPost, job);
            this.props.history.push(`/applications/results/${req.response.jobId}`);
        } catch (err) {
            if (err.request.status === 409) {
                addStandardDialog({
                    title: "Job with same parameters already running",
                    message: "You might be trying to run a duplicate job. Would you like to proceed?",
                    cancelText: "No",
                    confirmText: "Yes",
                    onConfirm: async () => {
                        const rerunJob = {
                            ...job,
                            acceptSameDataRetry: true
                        };
                        try {
                            const rerunRequest = await Client.post(hpcJobQueryPost, rerunJob);
                            this.props.history.push(`/applications/results/${rerunRequest.response.jobId}`);
                        } catch (rerunErr) {
                            snackbarStore.addFailure(
                                errorMessageOrDefault(rerunErr, "An error occurred submitting the job."),
                                false
                            );
                        }
                    },
                    onCancel: async () => {
                        this.setState(() => ({jobSubmitted: false}));
                    }
                });
            } else {
                if (err.request.status === 402) {
                    this.setState(({inlineError: err.response.why}));
                } else {
                    snackbarStore.addFailure(
                        errorMessageOrDefault(err, "An error occurred submitting the job."),
                        false
                    );
                }
                this.setState(() => ({jobSubmitted: false}));
            }
        } finally {
            this.props.setLoading(false);
        }
    };

    private async toggleFavorite(): Promise<void> {
        if (!this.state.application) return;
        const {name, version} = this.state.application.metadata;
        this.setState(() => ({favoriteLoading: true}));
        try {
            await this.state.promises.makeCancelable(Client.post(hpcFavoriteApp(name, version))).promise;
            this.setState(() => ({favorite: !this.state.favorite}));
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred"), false);
        } finally {
            this.setState(() => ({favoriteLoading: false}));
        }
    }

    private async retrieveApplication(name: string, version: string): Promise<void> {
        try {
            this.props.setLoading(true);
            const {response} = await this.state.promises.makeCancelable(
                Client.get<FullAppInfo>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            const app = response;
            const toolDescription = app.invocation.tool.tool.description;
            const parameterValues = new Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement | RangeRef>>();

            app.invocation.parameters.forEach(it => {
                if (Object.values(ParameterTypes).includes(it.type)) {
                    parameterValues.set(it.name, React.createRef<HTMLInputElement>());
                } else if (["boolean", "enumeration"].includes(it.type)) {
                    parameterValues.set(it.name, React.createRef<HTMLSelectElement>());
                } else if (it.type === "range") {
                    parameterValues.set(it.name, React.createRef<RangeRef>());
                }
            });
            this.setState(() => ({
                application: app,
                favorite: app.favorite,
                parameterValues,
                schedulingOptions: {
                    maxTime: toolDescription.defaultTimeAllocation,
                    numberOfNodes: toolDescription.defaultNumberOfNodes,
                    name: this.state.schedulingOptions.name,
                },
                useUrl: this.state.useUrl,
                url: this.state.url
            }));
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, `An error occurred fetching ${name}`), false);
        } finally {
            this.props.setLoading(false);
        }
    }

    private importParameters(file: File): void {
        const thisApp = this.state.application;
        if (!thisApp) return;

        const fileReader = new FileReader();
        fileReader.onload = async (): Promise<void> => {
            const rawInputFile = fileReader.result as string;
            try {
                const {
                    application,
                    parameters,
                    numberOfNodes,
                    mountedFolders,
                    maxTime,
                    siteVersion,
                    machineType,
                    jobName,
                    ipAddress
                } = JSON.parse(rawInputFile);
                // Verify metadata
                if (application.name !== thisApp.metadata.name) {
                    snackbarStore.addFailure("Application name does not match", false);
                    return;
                } else if (application.version !== thisApp.metadata.version) {
                    snackbarStore.addInformation(
                        "Application version does not match. Some parameters may not be filled out correctly.",
                        false,
                    );
                }

                // Finds the values to parameters that are still valid in this version of the application.
                const userInputValues = findKnownParameterValues({
                    nameToValue: parameters,
                    allowedParameterKeys: thisApp.invocation.parameters.map(it => ({
                        name: it.name, type: it.type
                    })),
                    siteVersion
                });

                const parametersFromUser = Object.keys(userInputValues);

                const unknownParameters = Object.keys(parameters).filter(it => !parametersFromUser.includes(it));
                this.setState(() => ({unknownParameters: this.state.unknownParameters.concat(unknownParameters)}));

                {
                    // Remove invalid input files from userInputValues
                    const fileParams = thisApp.invocation.parameters.filter(p => isFileOrDirectoryParam(p));
                    const invalidFiles: string[] = [];
                    for (const paramKey in fileParams) {
                        const param = fileParams[paramKey];
                        if (userInputValues[param.name]) {
                            // Defensive use of expandHomeOrProjectFolder. I am not sure if any parameter files
                            // contain these paths (they shouldn't)
                            const path = expandHomeOrProjectFolder(userInputValues[param.name], Client);
                            if (!await checkIfFileExists(path, Client)) {
                                invalidFiles.push(userInputValues[param.name]);
                                userInputValues[param.name] = "";
                            }
                        }
                    }

                    if (invalidFiles.length > 0) {
                        snackbarStore.addFailure(`The following files don't exists: ${invalidFiles.join(", ")}`, false);
                    }
                }

                {
                    // Verify and load additional mounts
                    const validMountFolders = [] as AdditionalMountedFolder[];

                    for (let i = 0; i < mountedFolders.length; i++) {
                        if (await checkIfFileExists(expandHomeOrProjectFolder(mountedFolders[i].ref, Client), Client)) {
                            const ref = React.createRef<HTMLInputElement>();
                            validMountFolders.push({ref});
                        }
                    }

                    this.setState(() => ({mountedFolders: this.state.mountedFolders.concat(validMountFolders)}));
                    const emptyMountedFolders = this.state.mountedFolders.slice(
                        this.state.mountedFolders.length - mountedFolders.length
                    );
                    emptyMountedFolders.forEach((it, index) => {
                        it.ref.current!.value = mountedFolders[index].ref;
                        it.ref.current!.dataset.path = mountedFolders[index].ref;
                    });
                }

                {
                    // Make sure all the fields we are using are visible
                    parametersFromUser.forEach(key =>
                        thisApp.invocation.parameters.find(it => it.name === key)!.visible = true
                    );
                    // Trigger changes in DOM.
                    this.setState(() => ({application: thisApp}));
                }

                {
                    // Initialize widget values
                    parametersFromUser.forEach(key => {
                        const param = thisApp.invocation.parameters.find(it => it.name === key)!;
                        param.visible = true;
                        const ref = this.state.parameterValues.get(key);
                        if (ref?.current) {
                            this.state.parameterValues.set(key, ref);

                            if (param.type === "input_directory" || param.type === "input_file") {
                                const input = ref.current! as HTMLInputElement;
                                input.value = userInputValues[key];
                                input.dataset.path = userInputValues[key];
                            } else {
                                if ("value" in ref.current) ref.current.value = userInputValues[key];
                                else (ref.current.setState(() => ({bounds: userInputValues[key] as any})));
                            }
                        }
                    });
                }

                if (ipAddress != null) {
                    this.state.ip.current!.value = ipAddress;
                }

                if (jobName) {
                    this.state.schedulingOptions.name.current!.value = jobName;
                }

                this.setState(() => ({
                    application: thisApp,
                    schedulingOptions: extractJobInfo({
                        maxTime,
                        numberOfNodes,
                        name: this.state.schedulingOptions.name,
                    }),
                    useUrl: this.state.useUrl,
                    url: this.state.url,
                    reservation: machineType.id ?? this.state.reservation,
                    useIp: ipAddress != null
                }));
            } catch (e) {
                console.warn(e);
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred"), false);
            }
        };
        fileReader.readAsText(file);
    }

    private onImportFileSelected(file: {path: string}): void {
        if (!file.path.endsWith(".json")) {
            addStandardDialog({
                title: "Continue?",
                message: "The selected file's extension is not \"json\" which is the required format.",
                confirmText: "Continue",
                onConfirm: () => this.fetchAndImportParameters(file)
            });
            return;
        }
        this.fetchAndImportParameters(file);
    }

    private fetchAndImportParameters = async (file: {path: string}): Promise<void> => {
        const fileStat = await Client.get<CloudFile>(statFileQuery(file.path));
        if (fileStat.response.size! > 5_000_000) {
            snackbarStore.addFailure("File size exceeds 5 MB. This is not allowed.", false);
            return;
        }
        const response = await fetchFileContent(file.path, Client);
        if (response.ok) this.importParameters(new File([await response.blob()], "params"));
    };

    private addFolder(): void {
        this.setState(s => ({
            mountedFolders: s.mountedFolders.concat([
                {
                    ref: React.createRef<HTMLInputElement>()
                }
            ])
        }));
    }

    private connectToJob(): void {
        this.setState(s => ({
            additionalPeers: s.additionalPeers.concat([{
                jobIdRef: React.createRef(),
                nameRef: React.createRef()
            }])
        }));
    }
}

const RunSection = styled(Box)`
    margin-bottom: 32px;
    margin-top: 16px;
`;

interface SchedulingFieldProps {
    text: string;
    field: string;
    subField?: string;
    onChange: (field: string, value: number, subField?: string) => void;
    value?: number;
    defaultValue?: number;
    min?: number;
    max?: number;
}

const SchedulingField: React.FunctionComponent<SchedulingFieldProps> = props => (
    <Label>
        {props.text}

        <Input
            type="number"
            step="1"
            min={props.min}
            max={props.max}
            value={props.value == null || isNaN(props.value) ? "0" : props.value}
            placeholder={`${props.defaultValue}`}
            onChange={({target: {value}}) => {
                const parsed = parseInt(value, 10);
                props.onChange(props.field, parsed, props.subField);
            }}
        />
    </Label>
);

interface ApplicationUrlProps {
    urlEnabled: boolean;
    setUrlEnabled: React.Dispatch<React.SetStateAction<boolean>>;
    url: React.RefObject<HTMLInputElement>;
}

const ApplicationUrl: React.FunctionComponent<ApplicationUrlProps> = props => {
    const [url, setUrl] = React.useState<string>("");

    React.useEffect(() => {
        if (!props.url) return;

        const current = props.url.current;
        if (current === null) return;

        current.value = url;
    }, [props.url, url]);

    return (
        <Box mb={16}>
            <Flex mb={8}>
                <Box flexGrow={1}>Public link</Box>
                <Toggle
                    scale={1.5}
                    activeColor="green"
                    checked={props.urlEnabled}
                    onChange={() => {
                        props.setUrlEnabled(!props.urlEnabled);
                    }}
                />
            </Flex>
            <div>
                {props.urlEnabled ? (
                    <>
                        <Warning
                            warning="By enabling this setting, anyone with a link can gain access to the application."
                        />
                        <Label mt={20}>
                            <Flex alignItems={"center"}>
                                <TextSpan>https://app-</TextSpan>
                                <Input
                                    mx={"2px"}
                                    placeholder={""}
                                    ref={props.url}
                                    required
                                    onKeyDown={e => e.preventDefault()}
                                    onClick={event => {
                                        event.preventDefault();
                                        dialogStore.addDialog(
                                            <PublicLinks.PublicLinkManagement
                                                onSelect={pl => {
                                                    setUrl(pl.url);
                                                    dialogStore.success();
                                                }}
                                            />,
                                            () => 0
                                        );
                                    }}
                                />
                                <TextSpan>.cloud.sdu.dk</TextSpan>
                            </Flex>
                        </Label>
                    </>
                ) : (<></>)}
            </div>
        </Box>
    );
};

interface ApplicationIPWidgetProps {
    ip: React.RefObject<HTMLInputElement>;
    ipEnabled: boolean;
    setIpEnabled: React.Dispatch<React.SetStateAction<boolean>>;
}

const ApplicationIPWidget: React.FunctionComponent<ApplicationIPWidgetProps> = props => {
    const [ip, setIp] = React.useState<string>("");

    React.useEffect(() => {
        if (!props.ip) return;

        const current = props.ip.current;
        if (current === null) return;

        current.value = ip;
    }, [props.ip, ip]);

    const [open, setOpen] = React.useState(false);

    return <>
        <Flex mb={8}>
            <Box flexGrow={1}>IP Address</Box>
            <Toggle
                scale={1.5}
                activeColor="green"
                checked={props.ipEnabled}
                onChange={() => {
                    props.setIpEnabled(!props.ipEnabled);
                }}
            />
        </Flex>
        <Box display={!props.ipEnabled ? "none" : "block"}>
            <Warning
                warning="By enabling this setting, anyone with the IP can gain access to the application."
            />
            <Flex alignItems={"center"} mt={20}>
                <Input
                    mx={"2px"}
                    placeholder=""
                    ref={props.ip}
                    cursor="pointer"
                    onKeyDown={e => e.preventDefault()}
                    onClick={event => {
                        event.preventDefault();
                        setOpen(true);
                    }}
                />
            </Flex>
        </Box>
        <ReactModal
            isOpen={open}
            onRequestClose={() => setOpen(false)}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            ariaHideApp
            style={{content: {...defaultModalStyle.content, height: "auto", maxHeight: undefined}}}
        >
            <IPAddressManagement onSelect={pl => setIp(pl.ipAddress)} />
        </ReactModal>
    </>;
};

interface NetworkingSectionProps extends ApplicationUrlProps, ApplicationIPWidgetProps {
    app: WithAppMetadata & WithAppInvocation;
}

const NetworkingSection: React.FunctionComponent<NetworkingSectionProps> = props => {
    return props.app.invocation.applicationType === "WEB" ? (
        <>
            <Heading.h4 mb={"1em"}>Networking</Heading.h4>

            <ApplicationUrl url={props.url} urlEnabled={props.urlEnabled} setUrlEnabled={props.setUrlEnabled} />
            <ApplicationIPWidget ip={props.ip} ipEnabled={props.ipEnabled} setIpEnabled={props.setIpEnabled} />
        </>
    ) : null;
};


interface JobSchedulingOptionsProps {
    onChange: (field: string | number, value: number, timeField: string) => void;
    options: JobSchedulingOptionsForInput;
    app: WithAppMetadata & WithAppInvocation;
    reservation: string;
    setReservation: (name: string, reservationMachine: Product) => void;
}

const JobSchedulingOptions = (props: JobSchedulingOptionsProps): JSX.Element | null => {
    if (!props.app) return null;
    const {maxTime, numberOfNodes, name} = props.options;
    return (
        <>
            <Flex mb="4px" mt="4px">
                <Label>
                    Job name
                    <Input
                        ref={name}
                        placeholder="Example: Analysis with parameters XYZ"
                    />
                </Label>
            </Flex>

            <Flex mb="1em">
                <SchedulingField
                    min={0}
                    max={200}
                    field="maxTime"
                    subField="hours"
                    text="Hours"
                    value={maxTime.hours}
                    onChange={props.onChange}
                />
                <Box ml="4px" />
                <SchedulingField
                    min={0}
                    max={59}
                    field="maxTime"
                    subField="minutes"
                    text="Minutes"
                    value={maxTime.minutes}
                    onChange={props.onChange}
                />
            </Flex>

            {!props.app.invocation.allowMultiNode ? null : (
                <Flex mb="1em">
                    <SchedulingField
                        min={1}
                        field="numberOfNodes"
                        text="Number of Nodes"
                        value={numberOfNodes}
                        onChange={props.onChange}
                    />
                </Flex>
            )}

            <div>
                <Label>Machine type <MandatoryField /></Label>
                <MachineTypes
                    reservation={props.reservation}
                    setReservation={props.setReservation}
                />
            </div>


        </>
    );
};


function extractJobInfo(jobInfo: JobSchedulingOptionsForInput): JobSchedulingOptionsForInput {
    const extractedJobInfo = {
        maxTime: {hours: 0, minutes: 0, seconds: 0},
        numberOfNodes: 1,
        name: jobInfo.name
    };
    const {maxTime, numberOfNodes} = jobInfo;
    extractedJobInfo.maxTime.hours = Math.abs(maxTime.hours);
    extractedJobInfo.maxTime.minutes = Math.abs(maxTime.minutes);
    extractedJobInfo.maxTime.seconds = Math.abs(maxTime.seconds);
    extractedJobInfo.numberOfNodes = numberOfNodes;
    return extractedJobInfo;
}

const mapDispatchToProps = (dispatch: Dispatch): RunOperations => ({
    onInit: () => {
        dispatch(setActivePage(SidebarPages.AppStore));
        dispatch(updatePageTitle("Run Application"));
    },

    setLoading: loading => dispatch(setLoading(loading))
});

const mapStateToProps = (redux: ReduxObject): {project?: string} => ({
    project: redux.project.project
});

export default connect(mapStateToProps, mapDispatchToProps)(Run);

export function importParameterDialog(importParameters: (file: File) => void, showFileSelector: () => void): void {
    dialogStore.addDialog((
        <div>
            <div>
                <Button fullWidth as="label">
                    Upload file
                    <HiddenInputField
                        type="file"
                        onChange={e => {
                            if (e.target.files) {
                                const file = e.target.files[0];
                                if (file.size > 10_000_000) {
                                    snackbarStore.addFailure("File exceeds 10 MB. Not allowed.", false);
                                } else {
                                    importParameters(file);
                                }
                                dialogStore.success();
                            }
                        }}
                    />
                </Button>
                <Button mt="6px" fullWidth onClick={() => (dialogStore.success(), showFileSelector())}>
                    Select file from {PRODUCT_NAME}
                </Button>
            </div>
            <Flex mt="20px">
                <Button onClick={() => dialogStore.success()} color="red" mr="5px">Cancel</Button>
            </Flex>
        </div>
    ), () => undefined);
}
