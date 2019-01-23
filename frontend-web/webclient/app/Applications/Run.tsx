import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import LoadingIcon from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import { connect } from "react-redux";
import { inSuccessRange, failureNotification, infoNotification, errorMessageOrDefault } from "UtilityFunctions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { RunAppProps, RunAppState, MaxTime, ApplicationParameter, ParameterTypes, JobSchedulingOptionsForInput, MaxTimeForInput } from "."
import { Application } from ".";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { Box, Flex, Text, Label, Error, OutlineButton, ContainerForText, VerticalButtonGroup, LoadingButton, Button } from "ui-components";
import Input, { HiddenInputField } from "ui-components/Input";
import { MainContainer } from "MainContainer/MainContainer";
import { Parameter } from "./ParameterWidgets";
import { extractParameters, hpcFavoriteApp } from "Utilities/ApplicationUtilities";
import { AppHeader } from "./View";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";

type Tool = any;

class Run extends React.Component<RunAppProps, RunAppState> {
    private siteVersion = 1;

    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            jobSubmitted: false,

            loading: false,
            error: undefined,

            parameterValues: {},
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

    private onJobSchedulingParamsChange = (field, value, timeField) => {
        let { schedulingOptions } = this.state;
        if (timeField) {
            schedulingOptions[field][timeField] = !isNaN(value) ? value : null;
        } else {
            schedulingOptions[field] = value;
        }
        this.setState(() => ({
            schedulingOptions
        }));
    }

    private onSubmit = event => {
        event.preventDefault();
        if (!this.state.application) return;
        if (this.state.jobSubmitted) return;

        let maxTime: MaxTimeForInput | null = this.extractJobInfo(this.state.schedulingOptions).maxTime;
        if (maxTime && maxTime.hours === null && maxTime.minutes === null && maxTime.seconds === null) maxTime = null;

        let job = {
            application: this.state.application.description.info,
            parameters: { ...this.state.parameterValues },
            numberOfNodes: this.state.schedulingOptions.numberOfNodes,
            tasksPerNode: this.state.schedulingOptions.tasksPerNode,
            maxTime: maxTime,
            type: "start"
        };

        this.setState(() => ({ jobSubmitted: true }));

        Cloud.post("/hpc/jobs", job).then(req => {
            inSuccessRange(req.request.status) ?
                this.props.history.push(`/applications/results/${req.response.jobId}`) :
                this.setState(() => ({ error: "An error occured", jobSubmitted: false }))
        }).catch(err => {
            this.setState(() => ({ error: err.message, jobSubmitted: false }))
        });
    }

    private onInputChange = (parameterName: string, value: string | number | object) =>
        this.setState(() => ({ parameterValues: { ...this.state.parameterValues, [parameterName]: value } }));

    private extractJobInfo(jobInfo): JobSchedulingOptionsForInput {
        let extractedJobInfo = { maxTime: { hours: null, minutes: null, seconds: null }, numberOfNodes: null, tasksPerNode: null };
        const { maxTime, numberOfNodes, tasksPerNode } = jobInfo;
        if (maxTime != null && (maxTime.hours != null || maxTime.minutes != null || maxTime.seconds != null)) {
            extractedJobInfo.maxTime.hours = maxTime.hours ? maxTime.hours : null;
            extractedJobInfo.maxTime.minutes = maxTime.minutes ? maxTime.minutes : null;
            extractedJobInfo.maxTime.seconds = maxTime.seconds ? maxTime.seconds : null;
        }
        extractedJobInfo.numberOfNodes = numberOfNodes;
        extractedJobInfo.tasksPerNode = tasksPerNode;
        return extractedJobInfo;
    }

    private toggleFavorite() {
        if (!this.state.application) return;
        const { name, version } = this.state.application.description.info;
        this.setState(() => ({ favoriteLoading: true }));
        this.state.promises.makeCancelable(Cloud.post(hpcFavoriteApp(name, version))).promise
            .then(it => this.setState(() => ({ favorite: !this.state.favorite })))
            .catch(it => this.setState(() => ({ error: errorMessageOrDefault(it, "An error occurred") })))
            .then(() => this.setState(() => ({ favoriteLoading: false })), () => this.setState(() => ({ favoriteLoading: false })))

    }

    private retrieveApplication(name: string, version: string) {
        this.setState(() => ({ loading: true }));

        this.state.promises.makeCancelable(
            Cloud.get(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
        ).promise.then((req: { response: Application }) => {
            const app = req.response;

            this.setState(() => ({
                application: app,
                loading: false,
                favorite: app.favorite
            }));
        }).catch(() => this.setState(() => ({
            loading: false,
            error: `An error occurred fetching ${name}`
        })));
    }

    private importParameters(file: File) {
        const thisApp = this.state.application;
        if (!thisApp) return;

        const fileReader = new FileReader();
        fileReader.onload = () => {
            const result = fileReader.result as string;
            try {
                const { application, parameters, numberOfNodes, tasksPerNode, maxTime, siteVersion } = JSON.parse(result);
                if (application.name !== thisApp.description.info.name) {
                    failureNotification("Application name does not match");
                    return;
                } else if (application.version !== thisApp.description.info.version) {
                    infoNotification("Application version does not match. Some parameters may not be filled out correctly.")
                }
                const extractedParameters = extractParameters(
                    parameters,
                    thisApp.description.parameters.map(it => ({
                        name: it.name, type: it.type as ParameterTypes
                    })),
                    siteVersion
                );

                this.setState(() => ({
                    parameterValues: { ...this.state.parameterValues, ...extractedParameters },
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
        const appInfo = application.description.info;

        const jobInfo = this.extractJobInfo(schedulingOptions);
        const element = document.createElement("a");
        element.setAttribute("href", "data:application/json;charset=utf-8," + encodeURIComponent(JSON.stringify({
            siteVersion: this.siteVersion,
            application: appInfo,
            parameters: { ...this.state.parameterValues },
            numberOfNodes: jobInfo.numberOfNodes,
            tasksPerNode: jobInfo.tasksPerNode,
            maxTime: jobInfo.maxTime,
        })));

        element.setAttribute("download", `${appInfo.name}-${appInfo.version}-params.json`);
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
                <AppHeader application={application} />
            </Flex>
        );

        const main = (
            <ContainerForText>
                <Error
                    clearError={() => this.setState(() => ({ error: undefined }))}
                    error={error} />

                <Parameters
                    values={parameterValues}
                    parameters={application.description.parameters}
                    onSubmit={this.onSubmit}
                    onChange={this.onInputChange}
                    schedulingOptions={schedulingOptions}
                    app={application}
                    onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                />
            </ContainerForText>
        );

        const requiredKeys = application.description.parameters.filter(it => !it.optional).map(it => ({ name: it.name, title: it.title }));
        let disabled = requiredKeys.map(it => it.name).some(it => !parameterValues[it]);
        let title: string | undefined = undefined;
        title = `Missing input for fields ${requiredKeys.map(it => it.title).join(", ")}.`

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
                    {this.state.favorite ? "Remove from My Apps" : "Add to My Apps"}
                </LoadingButton>
                <Dropdown>
                    <LoadingButton disabled={disabled} onClick={this.onSubmit} loading={jobSubmitted} color="blue">Submit</LoadingButton>
                    {disabled ? <DropdownContent width={"auto"} colorOnHover={false} color="white" backgroundColor="black">
                        <Text>{title}</Text>
                    </DropdownContent> : null}
                </Dropdown>
            </VerticalButtonGroup>
        );

        return (
            <MainContainer
                header={header}
                headerSize={192}
                main={main}
                sidebar={sidebar}
            />
        )
    }
}

interface ParameterValues {
    [name: string]: any
}

interface ParameterProps {
    values: ParameterValues,
    parameters: ApplicationParameter[],
    schedulingOptions: JobSchedulingOptionsForInput,
    app: Application,
    onChange: (name: string, value: any) => void,
    onSubmit: (e: React.FormEvent) => void,
    onJobSchedulingParamsChange: (field, value, subField) => void,
}
const Parameters = (props: ParameterProps) => {
    if (!props.parameters) return null

    let parametersList = props.parameters.map((parameter, index) => {
        let value = props.values[parameter.name];
        return (
            <Parameter
                key={index}
                parameter={parameter}
                onChange={props.onChange}
                value={value}
            />
        );
    });

    return (
        <form onSubmit={props.onSubmit}>
            {parametersList}
            <JobSchedulingOptions
                onChange={props.onJobSchedulingParamsChange}
                options={props.schedulingOptions}
                app={props.app}
            />
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


interface JobSchedulingOptionsProps { onChange: (a, b, c) => void, options: any, app: Application }
const JobSchedulingOptions = (props: JobSchedulingOptionsProps) => {
    if (!props.app) return null;
    const tool = props.app.tool.description;
    const { maxTime, numberOfNodes, tasksPerNode } = props.options;
    const defaultMaxTime = tool.defaultMaxTime;

    return (
        <>
            <Flex mb="1em">
                {!props.app.description.resources.multiNodeSupport ? null :
                    <>
                        <SchedulingField min={1} field="numberOfNodes" text="Number of Nodes" defaultValue={tool.defaultNumberOfNodes} value={numberOfNodes} onChange={props.onChange} />
                        <Box ml="5px" />
                        <SchedulingField min={1} field="tasksPerNode" text="Tasks per Node" defaultValue={tool.defaultTasksPerNode} value={tasksPerNode} onChange={props.onChange} />
                    </>
                }
            </Flex>

            <Label>Maximum time allowed</Label>
            <Flex mb="1em">
                <SchedulingField min={0} field="maxTime" subField="hours" text="Hours" defaultValue={defaultMaxTime.hours} value={maxTime.hours} onChange={props.onChange} />
                <Box ml="4px" />
                <SchedulingField min={0} field="maxTime" subField="minutes" text="Minutes" defaultValue={defaultMaxTime.minutes} value={maxTime.minutes} onChange={props.onChange} />
                <Box ml="4px" />
                <SchedulingField min={0} field="maxTime" subField="seconds" text="Seconds" defaultValue={defaultMaxTime.seconds} value={maxTime.seconds} onChange={props.onChange} />
            </Flex>
        </>)
};

interface RunOperations {
    updatePageTitle: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): RunOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application"))
});

const mapStateToProps = ({ }: ReduxObject) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(Run);
