import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import LoadingIcon from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import { connect } from "react-redux";
import { inSuccessRange, failureNotification, infoNotification, errorMessageOrDefault, inDevEnvironment } from "UtilityFunctions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { RunAppProps, RunAppState, ApplicationParameter, ParameterTypes, JobSchedulingOptionsForInput, WithAppInvocation, WithAppMetadata, WithAppFavorite } from "."
import { Dispatch } from "redux";
import { Box, Flex, Label, Error, OutlineButton, ContainerForText, VerticalButtonGroup, LoadingButton } from "ui-components";
import Input, { HiddenInputField } from "ui-components/Input";
import { MainContainer } from "MainContainer/MainContainer";
import { Parameter, OptionalParameters } from "./ParameterWidgets";
import { extractParameters, hpcFavoriteApp, hpcJobQueryPost, extractParametersFromMap, ParameterValues } from "Utilities/ApplicationUtilities";
import { AppHeader } from "./View";
import * as Heading from "ui-components/Heading";

class Run extends React.Component<RunAppProps, RunAppState> {
    private siteVersion = 1;

    constructor(props: Readonly<RunAppProps>) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            jobSubmitted: false,

            loading: false,
            error: undefined,

            parameterValues: new Map(),
            schedulingOptions: {
                maxTime: {
                    hours: null,
                    minutes: null,
                    seconds: null
                },
                numberOfNodes: null,
                tasksPerNode: null,
            },
            favorite: false,
            favoriteLoading: false
        };
        this.props.updatePageTitle();
    };

    componentDidMount() {
        const name = this.props.match.params.appName;
        const version = this.props.match.params.appVersion;
        this.retrieveApplication(name, version);
    }

    componentWillUnmount = () => this.state.promises.cancelPromises();

    private onJobSchedulingParamsChange = (field: string | number, value: number, timeField: string) => {
        const { schedulingOptions } = this.state;
        if (timeField) {
            schedulingOptions[field][timeField] = !isNaN(value) ? value : null;
        } else {
            schedulingOptions[field] = value;
        }
        this.setState(() => ({ schedulingOptions }));
    }

    private onSubmit = (event: React.FormEvent) => {
        event.preventDefault();
        if (!this.state.application) return;
        if (this.state.jobSubmitted) return;
        const { invocation } = this.state.application;

        const parameters = extractParametersFromMap(this.state.parameterValues, this.state.application!.invocation.parameters, Cloud);
        const requiredParams = invocation.parameters.filter(it => !it.optional);
        const missingParameters: string[] = [];
        requiredParams.forEach(rParam => {
            const parameterValue = parameters[rParam.name];
            // Number, string, boolean 
            if (!parameterValue) missingParameters.push(rParam.title);
            // { source, destination }
            else if (typeof parameterValue === "object") {
                if (!parameterValue.source) {
                    missingParameters.push(rParam.title);
                }
            }
        });
        
        if (missingParameters.length > 0) {
            failureNotification(`Missing values for ${missingParameters.join(", ")}`, missingParameters.length)
            return;
        }

        let maxTime = this.extractJobInfo(this.state.schedulingOptions).maxTime;
        if (maxTime && maxTime.hours === null && maxTime.minutes === null && maxTime.seconds === null) maxTime = null;

        let job = {
            application: { name: this.state.application!.metadata.name, version: this.state.application!.metadata.version },
            parameters,
            numberOfNodes: this.state.schedulingOptions.numberOfNodes,
            tasksPerNode: this.state.schedulingOptions.tasksPerNode,
            maxTime: maxTime,
            type: "start"
        };

        this.setState(() => ({ jobSubmitted: true }));

        Cloud.post(hpcJobQueryPost, job).then(req =>
            inSuccessRange(req.request.status) ?
                this.props.history.push(`/applications/results/${req.response.jobId}`) :
                this.setState(() => ({ error: "An error occured", jobSubmitted: false }))
        ).catch(err => {
            this.setState(() => ({ error: err.message, jobSubmitted: false }))
        });
    }

    private extractJobInfo(jobInfo): JobSchedulingOptionsForInput {
        let extractedJobInfo = { maxTime: { hours: null, minutes: null, seconds: null }, numberOfNodes: null, tasksPerNode: null };
        const { maxTime, numberOfNodes, tasksPerNode } = jobInfo;
        if (maxTime != null && (maxTime.hours != null || maxTime.minutes != null || maxTime.seconds != null)) {
            extractedJobInfo.maxTime.hours = maxTime.hours ? maxTime.hours : 0;
            extractedJobInfo.maxTime.minutes = maxTime.minutes ? maxTime.minutes : 0;
            extractedJobInfo.maxTime.seconds = maxTime.seconds ? maxTime.seconds : 0;
        }
        extractedJobInfo.numberOfNodes = numberOfNodes;
        extractedJobInfo.tasksPerNode = tasksPerNode;
        return extractedJobInfo;
    }

    private async toggleFavorite() {
        if (!this.state.application) return;
        const { name, version } = this.state.application.metadata;
        this.setState(() => ({ favoriteLoading: true }));
        try {
            await this.state.promises.makeCancelable(Cloud.post(hpcFavoriteApp(name, version))).promise;
            this.setState(() => ({ favorite: !this.state.favorite }));
        } catch (e) {
            this.setState(() => ({ error: errorMessageOrDefault(e, "An error occurred") }));
        } finally {
            this.setState(() => ({ favoriteLoading: false }));
        }
    }

    private async retrieveApplication(name: string, version: string) {
        try {
            this.setState(() => ({ loading: true }));
            const { response } = await this.state.promises.makeCancelable(
                Cloud.get<WithAppMetadata & WithAppInvocation & WithAppFavorite>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            const app = response;
            const toolDescription = app.invocation.tool.tool.description;
            const parameterValues = new Map();

            app.invocation.parameters.forEach(it => {
                if ((["input_file", "input_directory", "integer", "floating_point", "text"] as ParameterTypes[]).some(type => type === it.type)) {
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
            this.setState(() => ({ error: errorMessageOrDefault(e, `An error occurred fetching ${name}`) }));
        } finally {
            this.setState(() => ({ loading: false }));
        }
    }

    private importParameters(file: File) {
        const thisApp = this.state.application;
        if (!thisApp) return;

        const fileReader = new FileReader();
        fileReader.onload = () => {
            const result = fileReader.result as string;
            try {
                const { application, parameters, numberOfNodes, tasksPerNode, maxTime, siteVersion } = JSON.parse(result);
                if (application.name !== thisApp.metadata.name) {
                    failureNotification("Application name does not match");
                    return;
                } else if (application.version !== thisApp.metadata.version) {
                    infoNotification("Application version does not match. Some parameters may not be filled out correctly.")
                }

                const extractedParameters = extractParameters(
                    parameters,
                    thisApp.invocation.parameters.map(it => ({
                        name: it.name, type: it.type
                    })),
                    siteVersion
                );

                const { parameterValues } = this.state;

                const extractedParameterKeys = Object.keys(extractedParameters);
                
                // Show hidden fields.
                extractedParameterKeys.forEach(key => {
                    thisApp.invocation.parameters.find(it => it.name === key)!.visible = true;
                });
                this.setState(() => ({ application: thisApp }));
                
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
                    schedulingOptions: this.extractJobInfo({ maxTime, numberOfNodes, tasksPerNode })
                }));
            } catch (e) {
                console.warn(e);
                failureNotification("An error occured");
            }
        };
        fileReader.readAsText(file);
    }

    private exportParameters() {
        const { application, schedulingOptions } = this.state;
        if (!application) return;
        const appInfo = application.metadata;

        const jobInfo = this.extractJobInfo(schedulingOptions);
        const element = document.createElement("a");

        const values: { [key: string]: string } = {};

        for (const [key, ref] of this.state.parameterValues[Symbol.iterator]()) {
            if (ref && ref.current) values[key] = ref.current.value;
        }

        element.setAttribute("href", "data:application/json;charset=utf-8," + encodeURIComponent(JSON.stringify({
            siteVersion: this.siteVersion,
            application: {
                name: appInfo.name,
                version: appInfo.version
            },
            parameters: values,
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

    render() {
        const { application, error, jobSubmitted, schedulingOptions, parameterValues } = this.state;

        if (!application) return (
            <>
                <LoadingIcon size={18} />
                <Error
                    clearError={() => this.setState(() => ({ error: undefined }))}
                    error={error} />
            </>
        );

        const header = (
            <Flex ml="12%">
                <AppHeader slim application={application} />
            </Flex>
        );

        const main = (
            <ContainerForText>
                <Error
                    clearError={() => this.setState(() => ({ error: undefined }))}
                    error={error} />

                <Parameters
                    values={parameterValues}
                    parameters={application.invocation.parameters}
                    onSubmit={this.onSubmit}
                    schedulingOptions={schedulingOptions}
                    app={application}
                    onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                    onParameterUsed={p => {
                        p.visible = true;
                        this.setState(() => ({ application: this.state.application }));
                    }}
                />
            </ContainerForText>
        );

        const sidebar = (
            <VerticalButtonGroup>
                <OutlineButton
                    fullWidth
                    color="darkGreen"
                    onClick={() => this.exportParameters()}>
                    Export parameters
                </OutlineButton>
                <OutlineButton fullWidth color="darkGreen" as={"label"}>
                    Import parameters
                    <HiddenInputField
                        type="file"
                        onChange={e => { if (e.target.files) this.importParameters(e.target.files[0]) }} />
                </OutlineButton>
                <LoadingButton fullWidth loading={this.state.favoriteLoading} onClick={() => this.toggleFavorite()}>
                    {this.state.favorite ? "Remove from favorites" : "Add to favorites"}
                </LoadingButton>
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

const SubmitButton = ({ onSubmit, jobSubmitted }: SubmitButton) => {
    return (<LoadingButton onClick={onSubmit} loading={jobSubmitted} color="blue">Submit</LoadingButton>);
}

interface ParameterProps {
    values: ParameterValues
    parameters: ApplicationParameter[]
    schedulingOptions: JobSchedulingOptionsForInput
    app: WithAppMetadata & WithAppInvocation
    onSubmit: (e: React.FormEvent) => void
    onJobSchedulingParamsChange: (field: string, value: number | string, subField: number | string) => void
    onParameterUsed: (parameter: ApplicationParameter) => void
}

const Parameters = (props: ParameterProps) => {
    if (!props.parameters) return null

    const mandatory = props.parameters.filter(parameter => !parameter.optional);
    const visible = props.parameters.filter(parameter => parameter.optional && (parameter.visible === true || props.values.get(parameter.name)!.current != null));
    const optional = props.parameters.filter(parameter => parameter.optional && parameter.visible !== true && props.values.get(parameter.name)!.current == null);

    const mapParamToComponent = (parameter: ApplicationParameter, index: number) => {
        let ref = props.values.get(parameter.name)!;

        return (
            <Parameter
                parameterRef={ref}
                key={index}
                parameter={parameter}
            />
        );
    }

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

            <Heading.h4>Scheduling</Heading.h4>
            <JobSchedulingOptions
                onChange={props.onJobSchedulingParamsChange}
                options={props.schedulingOptions}
                app={props.app}
            />

            {optional.length > 0 ?
                <OptionalParameters parameters={optional} onUse={p => props.onParameterUsed(p)} />
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
            value={props.value == null || isNaN(props.value) ? "" : props.value}
            placeholder={`${props.defaultValue}`}
            onChange={({ target: { value } }) => {
                const parsed = parseInt(value);
                props.onChange(props.field, parsed, props.subField);
            }}
        />
    </Label>
);


interface JobSchedulingOptionsProps { onChange: (a, b, c) => void, options: any, app: WithAppMetadata & WithAppInvocation }
const JobSchedulingOptions = (props: JobSchedulingOptionsProps) => {
    if (!props.app) return null;
    const { maxTime, numberOfNodes, tasksPerNode } = props.options;
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

interface RunOperations {
    updatePageTitle: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): RunOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application"))
});

export default connect(null, mapDispatchToProps)(Run);
