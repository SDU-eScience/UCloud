import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import LoadingIcon from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import {connect} from "react-redux";
import {errorMessageOrDefault, removeTrailingSlash} from "UtilityFunctions";
import {updatePageTitle, setLoading} from "Navigation/Redux/StatusActions";
import {
    RunAppProps,
    RunAppState,
    ApplicationParameter,
    ParameterTypes,
    JobSchedulingOptionsForInput,
    WithAppInvocation,
    WithAppMetadata,
    RunOperations,
    RefReadPair,
    FullAppInfo
} from ".";
import {Dispatch} from "redux";
import {Box, Flex, Label, Error, OutlineButton, ContainerForText, VerticalButtonGroup, Button} from "ui-components";
import Input, {HiddenInputField} from "ui-components/Input";
import {MainContainer} from "MainContainer/MainContainer";
import {Parameter, OptionalParameters, InputDirectoryParameter} from "./ParameterWidgets";
import {
    extractParameters,
    hpcFavoriteApp,
    hpcJobQueryPost,
    extractParametersFromMap,
    ParameterValues,
    isFileOrDirectoryParam
} from "Utilities/ApplicationUtilities";
import {AppHeader} from "./View";
import * as Heading from "ui-components/Heading";
import {checkIfFileExists, expandHomeFolder} from "Utilities/FileUtilities";
import {SnackType} from "Snackbar/Snackbars";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {removeEntry} from "Utilities/CollectionUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";

class Run extends React.Component<RunAppProps, RunAppState> {
    private siteVersion = 1;

    constructor(props: Readonly<RunAppProps>) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            jobSubmitted: false,
            initialSubmit: false,

            error: undefined,

            parameterValues: new Map(),
            mountedFolders: [],
            schedulingOptions: {
                maxTime: {
                    hours: 0,
                    minutes: 0,
                    seconds: 0
                },
                numberOfNodes: 1,
                tasksPerNode: 1,
            },
            favorite: false,
            favoriteLoading: false
        };
    };

    public componentDidMount() {
        this.props.updatePageTitle();
        const name = this.props.match.params.appName;
        const version = this.props.match.params.appVersion;
        this.retrieveApplication(name, version);
    }

    public componentWillUnmount = () => this.state.promises.cancelPromises();

    private onJobSchedulingParamsChange = (field: string | number, value: number, timeField: string) => {
        const {schedulingOptions} = this.state;
        if (timeField) {
            schedulingOptions[field][timeField] = !isNaN(value) ? value : null;
        } else {
            schedulingOptions[field] = value;
        }
        this.setState(() => ({schedulingOptions}));
    };

    private onSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!this.state.application) return;
        if (this.state.jobSubmitted) return;
        const {invocation} = this.state.application;
        this.setState(() => ({initialSubmit: true}));

        const parameters = extractParametersFromMap({
            map: this.state.parameterValues,
            appParameters: this.state.application!.invocation.parameters,
            cloud: Cloud
        });
        const requiredParams = invocation.parameters.filter(it => !it.optional);
        const missingParameters: string[] = [];
        requiredParams.forEach(rParam => {
            const parameterValue = parameters[rParam.name];
            // Number, string, boolean 
            if (!parameterValue) missingParameters.push(rParam.title);
            // { source, destination }, might need refactoring in the event that other types become objects
            else if (typeof parameterValue === "object") {
                if (!parameterValue.source) {
                    missingParameters.push(rParam.title);
                }
            }
        });

        // FIXME: Not DRY 

        // Check missing values for required input fields.
        if (missingParameters.length > 0) {
            snackbarStore.addSnack({
                message: `Missing values for ${missingParameters.slice(0, 3).join(", ")} 
                 ${missingParameters.length > 3 ? `and ${missingParameters.length - 3} others.` : ``}`,
                type: SnackType.Failure,
                lifetime: 5000
            });
            return;
        }

        // Check optional input fields with errors
        const optionalErrors = [] as string[];
        const optionalParams = invocation.parameters.filter(it => it.optional && it.visible).map(it =>
            ({name: it.name, title: it.title})
        );
        optionalParams.forEach(it => {
            const param = this.state.parameterValues.get(it.name)!;
            if (!param.current!.checkValidity()) optionalErrors.push(it.title);
        });

        if (optionalErrors.length > 0) {
            snackbarStore.addSnack({
                message: `Invalid values for ${optionalErrors.slice(0, 3).join(", ")}
                    ${optionalErrors.length > 3 ? `and ${optionalErrors.length - 3} others` : ""}`,
                type: SnackType.Failure,
                lifetime: 5000
            });
            return;
        }

        // FIXME END

        const maxTime = extractJobInfo(this.state.schedulingOptions).maxTime;
        if (maxTime.hours === 0 && maxTime.minutes === 0 && maxTime.seconds === 0) {
            snackbarStore.addSnack({
                message: "Scheduling times must be more than 0 seconds.",
                type: SnackType.Failure,
                lifetime: 5000
            });
            return;
        }

        // FIXME: Unify with extractParametersFromMap
        const mounts = this.state.mountedFolders.filter(it => it.ref.current && it.ref.current.value).map(it => {
            const expandedValue = expandHomeFolder(it.ref.current!.value, Cloud.homeFolder)
            return {
                source: expandedValue,
                destination: removeTrailingSlash(expandedValue).split("/").pop()!,
                readOnly: it.readOnly
            };
        });
        // FIXME end

        const job = {
            application: {
                name: this.state.application!.metadata.name,
                version: this.state.application!.metadata.version
            },
            parameters,
            numberOfNodes: this.state.schedulingOptions.numberOfNodes,
            tasksPerNode: this.state.schedulingOptions.tasksPerNode,
            maxTime: maxTime,
            mounts,
            type: "start"
        };

        this.setState(() => ({jobSubmitted: true}));
        this.props.setLoading(true);
        try {
            const req = await Cloud.post(hpcJobQueryPost, job);
            this.props.history.push(`/applications/results/${req.response.jobId}`);
        } catch (err) {
            this.setState(() => ({
                error: errorMessageOrDefault(err, "An error ocurred submitting the job."),
                jobSubmitted: false
            }));
        } finally {
            this.props.setLoading(false);
        }
    };

    private async toggleFavorite() {
        if (!this.state.application) return;
        const {name, version} = this.state.application.metadata;
        this.setState(() => ({favoriteLoading: true}));
        try {
            await this.state.promises.makeCancelable(Cloud.post(hpcFavoriteApp(name, version))).promise;
            this.setState(() => ({favorite: !this.state.favorite}));
        } catch (e) {
            this.setState(() => ({error: errorMessageOrDefault(e, "An error occurred")}));
        } finally {
            this.setState(() => ({favoriteLoading: false}));
        }
    }

    private async retrieveApplication(name: string, version: string) {
        try {
            this.props.setLoading(true);
            const {response} = await this.state.promises.makeCancelable(
                Cloud.get<FullAppInfo>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            const app = response;
            const toolDescription = app.invocation.tool.tool.description;
            const parameterValues = new Map();

            app.invocation.parameters.forEach(it => {
                if (Object.values(ParameterTypes).includes(it.type)) {
                    parameterValues.set(it.name, React.createRef<HTMLInputElement>());
                } else if (it.type === "boolean") {
                    parameterValues.set(it.name, React.createRef<HTMLSelectElement>());
                }
            });
            this.setState(() => ({
                application: app,
                favorite: app.favorite,
                parameterValues,
                schedulingOptions: {
                    maxTime: toolDescription.defaultMaxTime,
                    numberOfNodes: toolDescription.defaultNumberOfNodes,
                    tasksPerNode: toolDescription.defaultTasksPerNode
                }
            }));
        } catch (e) {
            this.setState(() => ({error: errorMessageOrDefault(e, `An error occurred fetching ${name}`)}));
        } finally {
            this.props.setLoading(false);
        }
    }

    /* FIXME: Refactor into smaller functions */
    private importParameters(file: File) {
        const thisApp = this.state.application;
        if (!thisApp) return;

        const fileReader = new FileReader();
        fileReader.onload = async () => {
            const result = fileReader.result as string;
            try {
                const {
                    application,
                    parameters,
                    numberOfNodes,
                    mountedFolders,
                    tasksPerNode,
                    maxTime,
                    siteVersion
                } = JSON.parse(result);
                if (application.name !== thisApp.metadata.name) {
                    snackbarStore.addSnack({
                        message: "Application name does not match",
                        type: SnackType.Failure
                    });
                    return;
                } else if (application.version !== thisApp.metadata.version) {
                    snackbarStore.addSnack({
                        message: "Application version does not match. Some parameters may not be filled out correctly.",
                        type: SnackType.Information
                    });
                }

                const extractedParameters = extractParameters({
                    parameters,
                    allowedParameterKeys: thisApp.invocation.parameters.map(it => ({
                        name: it.name, type: it.type
                    })),
                    siteVersion
                });

                const fileParams = thisApp.invocation.parameters.filter(p => isFileOrDirectoryParam(p));

                const invalidFiles: string[] = [];

                for (const paramKey in fileParams) {
                    const param = fileParams[paramKey];
                    if (!!extractedParameters[param.name])
                        if (!await checkIfFileExists(expandHomeFolder(extractedParameters[param.name], Cloud.homeFolder), Cloud)) {
                            invalidFiles.push(extractedParameters[param.name]);
                            delete extractedParameters[param.name];
                        }
                }

                const invalidMountIndices = [] as number[];
                const validMountFolders = [] as RefReadPair[];

                for (let i = 0; i < mountedFolders.length; i++) {
                    if (!await checkIfFileExists(expandHomeFolder(mountedFolders[i].ref, Cloud.homeFolder), Cloud)) {
                        invalidMountIndices.push(i);
                    } else {
                        const ref = React.createRef<HTMLInputElement>();
                        validMountFolders.push({ref, readOnly: mountedFolders[i].readOnly});
                    }
                }

                // FIXME: Could be done using defaultValue
                // Add mountedFolders and fill out ref values
                this.setState(() => ({mountedFolders: this.state.mountedFolders.concat(validMountFolders)}));
                const emptyMountedFolders = this.state.mountedFolders.slice(this.state.mountedFolders.length - mountedFolders.length);
                emptyMountedFolders.forEach((it, index) => it.ref.current!.value = mountedFolders[index].ref);



                if (invalidFiles.length) {
                    snackbarStore.addSnack({
                        message: `Extracted files don't exists: ${invalidFiles.join(", ")}`,
                        type: SnackType.Failure
                    });
                }
                const {parameterValues} = this.state;

                const extractedParameterKeys = Object.keys(extractedParameters);

                // Show hidden fields.
                extractedParameterKeys.forEach(key =>
                    thisApp.invocation.parameters.find(it => it.name === key)!.visible = true
                );
                this.setState(() => ({application: thisApp}));

                extractedParameterKeys.forEach(key => {
                    thisApp.invocation.parameters.find(it => it.name === key)!.visible = true;
                    const ref = parameterValues.get(key);
                    if (ref && ref.current) {
                        ref.current.value = extractedParameters[key];
                        parameterValues.set(key, ref);
                    }
                });

                this.setState(() => ({
                    parameterValues,
                    schedulingOptions: extractJobInfo({maxTime, numberOfNodes, tasksPerNode})
                }));
            } catch (e) {
                console.warn(e);
                snackbarStore.addSnack({message: "An error ocurred", type: SnackType.Failure});
            }
        };
        fileReader.readAsText(file);
    }

    render() {
        const {application, error, jobSubmitted, schedulingOptions, parameterValues} = this.state;
        if (!application) return (
            <MainContainer
                main={<>
                    <LoadingIcon size={18} />
                    <Error
                        clearError={() => this.setState(() => ({error: undefined}))}
                        error={error} />
                </>}
            />
        );

        const onAccessChange = (index: number, readOnly: boolean) => {
            const {mountedFolders} = this.state;
            mountedFolders[index].readOnly = readOnly;
            this.setState(() => ({mountedFolders}));
        }

        const header = (
            <Flex ml="12%">
                <AppHeader slim application={application} />
            </Flex>
        );

        const main = (
            <ContainerForText>
                <Error clearError={() => this.setState(() => ({error: undefined}))} error={error} />

                <Parameters
                    initialSubmit={this.state.initialSubmit}
                    addFolder={() => this.setState(s => ({mountedFolders: s.mountedFolders.concat([{ref: React.createRef<HTMLInputElement>(), readOnly: true}])}))}
                    removeDirectory={index => this.setState(s => ({mountedFolders: removeEntry(s.mountedFolders, index)}))}
                    additionalDirectories={this.state.mountedFolders}
                    values={parameterValues}
                    onAccessChange={onAccessChange}
                    parameters={application.invocation.parameters}
                    onSubmit={this.onSubmit}
                    schedulingOptions={schedulingOptions}
                    app={application}
                    onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                    onParameterChange={(p, visible) => {
                        p.visible = visible;
                        if (!visible) parameterValues.set(p.name, React.createRef<HTMLSelectElement | HTMLInputElement>())
                        this.setState(() => ({application: this.state.application}));
                    }}
                />
            </ContainerForText>
        );

        const sidebar = (
            <VerticalButtonGroup>
                <OutlineButton
                    fullWidth
                    color="darkGreen"
                    onClick={() => exportParameters({
                        application: this.state.application,
                        schedulingOptions: this.state.schedulingOptions,
                        parameterValues: this.state.parameterValues,
                        siteVersion: this.siteVersion,
                        mountedFolders: this.state.mountedFolders
                    })}>
                    Export parameters
                </OutlineButton>
                <OutlineButton fullWidth color="darkGreen" as="label">
                    Import parameters
                    <HiddenInputField
                        type="file"
                        onChange={e => {
                            if (e.target.files) this.importParameters(e.target.files[0])
                        }} />
                </OutlineButton>
                <Button fullWidth disabled={this.state.favoriteLoading} onClick={() => this.toggleFavorite()}>
                    {this.state.favorite ? "Remove from favorites" : "Add to favorites"}
                </Button>
                <SubmitButton
                    parameters={application.invocation.parameters}
                    jobSubmitted={jobSubmitted}
                    onSubmit={this.onSubmit}
                />
            </VerticalButtonGroup>
        );

        return (
            <MainContainer
                header={header}
                headerSize={64}
                main={main}
                sidebar={sidebar}
            />
        )
    }
}

interface SubmitButton {
    parameters: ApplicationParameter[]
    onSubmit: (e: React.FormEvent) => void
    jobSubmitted: boolean
}

const SubmitButton = ({onSubmit, jobSubmitted}: SubmitButton) =>
    (<Button onClick={onSubmit} disabled={jobSubmitted} color="blue">Submit</Button>);

interface ParameterProps {
    initialSubmit: boolean
    values: ParameterValues
    parameters: ApplicationParameter[]
    schedulingOptions: JobSchedulingOptionsForInput
    additionalDirectories: RefReadPair[]
    app: WithAppMetadata & WithAppInvocation
    onSubmit: (e: React.FormEvent) => void
    onJobSchedulingParamsChange: (field: string, value: number | string, subField: number | string) => void
    onParameterChange: (parameter: ApplicationParameter, visible: boolean) => void
    addFolder: () => void
    removeDirectory: (index: number) => void
    onAccessChange: (index: number, readOnly: boolean) => void
}

const Parameters = (props: ParameterProps) => {
    if (!props.parameters) return null;

    const mandatory = props.parameters.filter(parameter => !parameter.optional);
    const visible = props.parameters.filter(parameter => parameter.optional && (parameter.visible === true || props.values.get(parameter.name)!.current != null));
    const optional = props.parameters.filter(parameter => parameter.optional && parameter.visible !== true && props.values.get(parameter.name)!.current == null);
    
    const mapParamToComponent = (parameter: ApplicationParameter) => {
        let ref = props.values.get(parameter.name)!;

        return (
            <Parameter
                key={parameter.name}
                initialSubmit={props.initialSubmit}
                parameterRef={ref}
                parameter={parameter}
                onParamRemove={() => props.onParameterChange(parameter, false)}
            />
        );
    };

    let mandatoryParams = mandatory.map(mapParamToComponent);
    let visibleParams = visible.map(mapParamToComponent);

    return (
        <form onSubmit={props.onSubmit}>
            <Heading.h4>Mandatory Parameters ({mandatoryParams.length})</Heading.h4>
            {mandatoryParams}

            {visibleParams.length > 0 ?
                <>
                    <Heading.h4>Additional Parameters Used</Heading.h4>
                    {visibleParams}
                </>
                : null
            }
            <Heading.h4 mb="4px"><Flex>Mount additional folders <Button type="button" ml="5px" onClick={props.addFolder}>+</Button></Flex></Heading.h4>
            {props.additionalDirectories.some(it => !it.readOnly) ? "Note: Giving folders read/write access will make the startup and shutdown of the application longer." : ""}
            {props.additionalDirectories.map((entry, i) => (
                <Box key={i} mb="7px">
                    <InputDirectoryParameter
                        onParamRemove={() => undefined}
                        defaultValue={entry.defaultValue}
                        initialSubmit={false}
                        parameterRef={entry.ref}
                        onRemove={() => props.removeDirectory(i)}
                        parameter={{
                            type: ParameterTypes.InputDirectory,
                            name: "",
                            optional: true,
                            title: "",
                            description: "",
                            defaultValue: "",
                            visible: true,
                            unitName: (<Box width="105px"><ClickableDropdown
                                chevron
                                minWidth="150px"
                                onChange={key => props.onAccessChange(i, key === "READ")}
                                trigger={entry.readOnly ? "Read only" : "Read/Write"}
                                options={[{text: "Read only", value: "READ"}, {text: "Read/Write", value: "READ/WRITE"}]}
                            ><Box>Read only</Box><Box>Read/Write</Box></ClickableDropdown></Box>),
                        }}
                    />
                </Box>))}
            <Heading.h4>Scheduling</Heading.h4>
            <JobSchedulingOptions
                onChange={props.onJobSchedulingParamsChange}
                options={props.schedulingOptions}
                app={props.app}
            />

            {optional.length > 0 ?
                <OptionalParameters parameters={optional} onUse={p => props.onParameterChange(p, true)} />
                : null
            }
        </form>
    )
};

interface SchedulingFieldProps {
    text: string
    field: string
    subField?: string
    onChange: (field: string, value: number, subField?: string) => void

    value?: number
    defaultValue?: number
    min?: number
    max?: number
}

const SchedulingField: React.StatelessComponent<SchedulingFieldProps> = props => (
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
                const parsed = parseInt(value);
                props.onChange(props.field, parsed, props.subField);
            }}
        />
    </Label>
);


interface JobSchedulingOptionsProps {onChange: (a, b, c) => void, options: any, app: WithAppMetadata & WithAppInvocation}
const JobSchedulingOptions = (props: JobSchedulingOptionsProps) => {
    if (!props.app) return null;
    const {maxTime, numberOfNodes, tasksPerNode} = props.options;
    return (
        <>
            <Flex mb="1em">
                <SchedulingField min={0} field="maxTime" subField="hours" text="Hours" value={maxTime.hours} onChange={props.onChange} />
                <Box ml="4px" />
                <SchedulingField min={0} field="maxTime" subField="minutes" text="Minutes" value={maxTime.minutes} onChange={props.onChange} />
                <Box ml="4px" />
                <SchedulingField min={0} field="maxTime" subField="seconds" text="Seconds" value={maxTime.seconds} onChange={props.onChange} />
            </Flex>

            {!props.app.invocation.resources.multiNodeSupport ? null :
                <Flex mb="1em">
                    <SchedulingField min={1} field="numberOfNodes" text="Number of Nodes" value={numberOfNodes} onChange={props.onChange} />
                    <Box ml="5px" />
                    <SchedulingField min={1} field="tasksPerNode" text="Tasks per Node" value={tasksPerNode} onChange={props.onChange} />
                </Flex>
            }
        </>)
};

function extractJobInfo(jobInfo: JobSchedulingOptionsForInput): JobSchedulingOptionsForInput {
    let extractedJobInfo = {maxTime: {hours: 0, minutes: 0, seconds: 0}, numberOfNodes: 1, tasksPerNode: 1};
    const {maxTime, numberOfNodes, tasksPerNode} = jobInfo;
    extractedJobInfo.maxTime.hours = Math.abs(maxTime.hours);
    extractedJobInfo.maxTime.minutes = Math.abs(maxTime.minutes);
    extractedJobInfo.maxTime.seconds = Math.abs(maxTime.seconds);
    extractedJobInfo.numberOfNodes = numberOfNodes;
    extractedJobInfo.tasksPerNode = tasksPerNode;
    return extractedJobInfo;
}

function exportParameters({application, schedulingOptions, parameterValues, mountedFolders, siteVersion}: {
    application?: FullAppInfo
    schedulingOptions: JobSchedulingOptionsForInput,
    parameterValues: ParameterValues,
    mountedFolders: RefReadPair[]
    siteVersion: number
}) {
    if (!application) return;
    const appInfo = application.metadata;

    const jobInfo = extractJobInfo(schedulingOptions);
    const element = document.createElement("a");

    const values: {[key: string]: string} = {};

    for (const [key, ref] of parameterValues[Symbol.iterator]()) {
        if (ref && ref.current) values[key] = ref.current.value;
    }



    element.setAttribute("href", "data:application/json;charset=utf-8," + encodeURIComponent(JSON.stringify({
        siteVersion: siteVersion,
        application: {
            name: appInfo.name,
            version: appInfo.version
        },
        parameters: values,
        mountedFolders: mountedFolders.map(it => ({ref: it.ref.current && it.ref.current.value, readOnly: it.readOnly})).filter(it => it.ref),
        numberOfNodes: jobInfo.numberOfNodes,
        tasksPerNode: jobInfo.tasksPerNode,
        maxTime: jobInfo.maxTime,
    })));

    element.setAttribute("download", `${application.metadata.name}-${application.metadata.version}-params.json`);
    element.style.display = "none";
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
}

const mapDispatchToProps = (dispatch: Dispatch): RunOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application")),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(Run);
