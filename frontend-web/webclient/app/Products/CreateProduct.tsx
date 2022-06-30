import {callAPI} from "@/Authentication/DataHook";
import * as React from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Button, Checkbox, Input, Label, Select, TextArea} from "@/ui-components";
import {InputLabel} from "@/ui-components/Input";
import {LabelProps} from "@/ui-components/Label";
import {errorMessageOrDefault, stopPropagation, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {useLayoutEffect, useState} from "react";

export interface DataType {
    required: string[];
    fields: Record<string, any>;
}
const DataContext = React.createContext<DataType>({required: [], fields: {}});

const dynamicPropExclusion: string[] = [
    "id", "dependencies", "styling", "required"
];

function evaluateProperties(ctx: DataType, props: any): Record<string, any> {
    const output: Record<string, any> = {};
    for (const key of Object.keys(props)) {
        const propValue = props[key];
        if (dynamicPropExclusion.indexOf(key) !== -1) {
            output[key] = propValue;
        } else if (typeof propValue === "function") {
            output[key] = propValue(ctx);
        } else {
            output[key] = propValue;
        }
    }
    return output;
}

function useEvaluatedProperties<E extends ResourceField>(ctx: DataType, props: E): Record<string, any> {
    const [evaluatedProps, setEvaluatedProps] = useState<Record<string, any>>(evaluateProperties(ctx, props));
    useLayoutEffect(() => {
        if (props.dependencies) {
            const listener: () => void = () => {
                setTimeout(() => setEvaluatedProps(evaluateProperties(ctx, props)), 0);
            };

            for (const dep of props.dependencies) {
                const depElement = document.querySelector(`#${dep}`);
                if (depElement) {
                    depElement.addEventListener("blur", listener);
                    depElement.addEventListener("change", listener);
                }
            }

            return () => {
                if (!props.dependencies) return;
                for (const dep of props.dependencies) {
                    const depElement = document.querySelector(`#${dep}`);
                    if (depElement) {
                        depElement.removeEventListener("blur", listener);
                        depElement.removeEventListener("change", listener);
                    }
                }
            };
        }
        return () => {};
    }, [props.dependencies]);

    return evaluatedProps as E;
}

type DynamicProp<E> = ((t: DataType) => E) | E;

interface ResourceField {
    id: string;
    label: DynamicProp<string>;
    required?: boolean;
    placeholder?: DynamicProp<string>;
    styling: LabelProps;
    leftLabel?: DynamicProp<string>;
    rightLabel?: DynamicProp<string>;

    dependencies?: string[];
}

interface NumberField extends ResourceField {
    min?: DynamicProp<number>;
    max?: DynamicProp<number>;
    step?: DynamicProp<string>;
}

interface SelectField extends Omit<ResourceField, "leftLabel" | "rightLabel"> {
    options: DynamicProp<{value: string, text: string}[]>;
}

interface CheckboxField extends Omit<ResourceField, "leftLabel" | "rightLabel" | "placeholder"> {
    defaultChecked: boolean;
    // NOTE(Jonas): Should we omit styling here?
}

interface TextAreaField extends ResourceField {
    rows: number;
}

export default abstract class ResourceForm<Request, Response> extends React.Component<{
    /* Note(jonas): Seems passing "fields" only would work just as well. */
    createRequest: (d: DataType) => Promise<APICallParameters<Request>>;
    title: string;
    formatError?: (errors: string[]) => string;
    onSubmitSucceded?: (res: Response, d: DataType) => void;
    onSubmitError?: (err: string) => void;
}> {
    public data: DataType = {required: [], fields: {}};

    public resetData(): void {
        this.data = {required: [], fields: {}};
    }

    private async onSubmit(): Promise<void> {
        const validated = this.validate();
        if (validated) {
            if (this.data.fields.freeToUse) {
                // Required if freeToUse is true
                this.data.fields.pricePerUnit = 0.000001;
            }

            const request = await this.props.createRequest(this.data);
            try {
                const res = await callAPI<Response>(request);
                this.props.onSubmitSucceded?.(res, this.data);
            } catch (err) {
                const message = errorMessageOrDefault(err, "Failed to create " + this.props.title.toLocaleLowerCase());
                this.props.onSubmitError?.(message);
            }
        }
    }

    public render(): JSX.Element {
        return (
            <form
                onSubmit={e => {stopPropagationAndPreventDefault(e); this.onSubmit()}}
                style={{maxWidth: "800px", marginTop: "30px", marginLeft: "auto", marginRight: "auto"}}
            >
                <DataContext.Provider value={this.data}>
                    {this.props.children}
                </DataContext.Provider>
                <Button type="submit" my="12px" disabled={false}>Submit</Button>
            </form>
        );
    }

    public validate(): boolean {
        const requiredFields = this.data.required;
        const missingFields: string[] = [];
        for (const field of requiredFields) {

            const value = this.data.fields[field];
            if (value == null) {
                missingFields.push(field);
                continue;
            }

            const type = typeof value;

            switch (type) {
                case "number":
                    if (isNaN(value)) missingFields.push(field);
                    break;
                case "string":
                    if (value === "") missingFields.push(field);
                    break;
            }
        }

        if (missingFields.length > 0) {
            if (missingFields.length > 0 && this.props.formatError) {
                const error = this.props.formatError(missingFields);
                snackbarStore.addFailure(error, false);
            } else {
                snackbarStore.addFailure(`Fields ${missingFields.join(", ")} are invalid`, false);
            }
        }

        return missingFields.length === 0;
    }

    public static Number(props: NumberField): JSX.Element {
        const ctx = useResourceFormField({id: props.id, required: props.required as boolean});
        const p = useEvaluatedProperties(ctx, props);

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = props.styling;

        const isInt = !p.step?.includes(".");

        return <Label {...remainingStyle}>
            {p.label}
            <div style={{display: "flex"}}>
                {p.leftLabel ? <InputLabel leftLabel>{p.leftLabel}</InputLabel> : null}
                <Input
                    type="number"
                    onChange={e => {ctx.fields[props.id] = isInt ? parseInt(e.target.value) : parseFloat(e.target.value)}}
                    leftLabel={!!p.leftLabel}
                    rightLabel={!!p.rightLabel}
                    {...p}
                />
                {p.rightLabel ? <InputLabel rightLabel>{p.rightLabel}</InputLabel> : null}
            </div>
        </Label>
    }

    public static Select(props: SelectField): JSX.Element {
        const ctx = useResourceFormField({id: props.id, required: props.required})
        const p = useEvaluatedProperties(ctx, props);

        React.useEffect(() => {
            if (props.required) ctx.fields[props.id] = p.options[0].value;
        }, []);

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = props.styling;

        return <Label {...remainingStyle}>
            {p.label}
            <Select {...p} onChange={e => ctx.fields[props.id] = e.target.value}>
                {props.required ? null : <option></option>}
                {p.options.map(option => (
                    <option key={option.value} value={option.value}>{option.text}</option>
                ))}
            </Select>
        </Label>;
    }

    public static Checkbox(props: CheckboxField): JSX.Element {
        const ctx = useResourceFormField({id: props.id, required: props.required, defaultChecked: props.defaultChecked});
        const p = useEvaluatedProperties(ctx, props);

        return <Label>
            <Checkbox onClick={() => ctx.fields[props.id] = !ctx.fields[props.id]} onChange={stopPropagation} {...p} />
            {p.label}
        </Label>;
    }

    public static Text(props: ResourceField): JSX.Element {
        const ctx = useResourceFormField({id: props.id, required: props.required})
        const p = useEvaluatedProperties(ctx, props);

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = props.styling;

        return <Label {...remainingStyle}>
            {p.label}
            <div style={{display: "flex"}}>
                {p.leftLabel ? <InputLabel leftLabel>{p.leftLabel}</InputLabel> : null}
                <Input
                    type="text"
                    onChange={e => ctx.fields[props.id] = e.target.value}
                    leftLabel={!!p.leftLabel}
                    rightLabel={!!p.rightLabel}
                    {...p}
                />
                {p.rightLabel ? <InputLabel rightLabel>{p.rightLabel}</InputLabel> : null}
            </div>
        </Label>
    }

    public static TextArea(props: TextAreaField): JSX.Element {
        const ctx = useResourceFormField({id: props.id, required: props.required})
        const p = useEvaluatedProperties(ctx, props);

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = props.styling;

        return <Label {...remainingStyle}>
            {p.label}
            <div>
                <TextArea width="100%" onChange={e => ctx.fields[props.id] = e.target.value} {...p} />
            </div>
        </Label>
    }
}

function useResourceFormField({required, id, defaultChecked}: {id: string; required?: boolean; defaultChecked?: boolean}) {
    const ctx = React.useContext(DataContext);

    React.useEffect(() => {
        if (required) ctx.required.push(id);
        if (defaultChecked != null) ctx.fields[id] = defaultChecked;
        return () => {
            delete ctx.fields[id];
            if (required) {
                ctx.required = ctx.required.filter(it => it !== id);
            }
        }
    }, []);

    return ctx;
}
