import {callAPI} from "Authentication/DataHook";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Checkbox, Input, Label, Select, TextArea} from "ui-components";
import {InputLabel} from "ui-components/Input";
import {LabelProps} from "ui-components/Label";
import {errorMessageOrDefault, stopPropagation, stopPropagationAndPreventDefault} from "UtilityFunctions";

interface DataType {
    required: string[];
    fields: Record<string, any>;
}
const DataContext = React.createContext<DataType>({required: [], fields: {}});

interface ResourceField {
    id: string;
    label: string;
    required?: boolean;
    placeholder?: string;
    styling: LabelProps;
    leftLabel?: string;
    rightLabel?: string;
}

interface NumberField extends ResourceField {
    min?: number;
    max?: number;
    step?: string;
}

interface SelectField extends Omit<ResourceField, "leftLabel" | "rightLabel"> {
    options: {value: string, text: string}[];
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
}> {
    public data: DataType = {required: [], fields: {}};

    public resetData(): void {
        this.data = {required: [], fields: {}};
    }

    private async onSubmit(): Promise<void> {
        const validated = this.validate();
        if (validated) {
            const request = await this.props.createRequest(this.data);
            try {
                const res = await callAPI<Response>(request);
                this.props.onSubmitSucceded?.(res, this.data);
            } catch (err) {
                errorMessageOrDefault(err, "Failed to create " + this.props.title.toLocaleLowerCase());
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

    public static Number({id, label, styling, leftLabel, rightLabel, ...props}: NumberField): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required});

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        const isInt = !props.step?.includes(".");

        return <Label {...remainingStyle}>
            {label}
            <div style={{display: "flex"}}>
                {leftLabel ? <InputLabel leftLabel>{leftLabel}</InputLabel> : null}
                <Input
                    type="number"
                    onChange={e => {ctx.fields[id] = isInt ? parseInt(e.target.value) : parseFloat(e.target.value)}}
                    leftLabel={!!leftLabel}
                    rightLabel={!!rightLabel}
                    {...props}
                />
                {rightLabel ? <InputLabel rightLabel>{rightLabel}</InputLabel> : null}
            </div>
        </Label>
    }

    public static Select({id, label, options, styling, ...props}: SelectField): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

        React.useEffect(() => {
            if (props.required) ctx.fields[id] = options[0].value;
        }, []);

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        return <Label {...remainingStyle}>
            {label}
            <Select {...props} onChange={e => ctx.fields[id] = e.target.value}>
                {props.required ? null : <option></option>}
                {options.map(option => (
                    <option key={option.value} value={option.value}>{option.text}</option>
                ))}
            </Select>
        </Label>;
    }

    public static Checkbox({id, label, ...props}: CheckboxField): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required, defaultChecked: props.defaultChecked});

        return <Label>
            <Checkbox onClick={() => ctx.fields[id] = !ctx.fields[id]} onChange={stopPropagation} {...props} />
            {label}
        </Label>;
    }

    public static Text({id, label, styling, leftLabel, rightLabel, ...props}: ResourceField): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        return <Label {...remainingStyle}>
            {label}
            <div style={{display: "flex"}}>
                {leftLabel ? <InputLabel leftLabel>{leftLabel}</InputLabel> : null}
                <Input
                    type="text"
                    onChange={e => ctx.fields[id] = e.target.value}
                    leftLabel={!!leftLabel}
                    rightLabel={!!rightLabel}
                    {...props}
                />
                {rightLabel ? <InputLabel rightLabel>{rightLabel}</InputLabel> : null}
            </div>
        </Label>
    }

    public static TextArea({id, label, styling, ...props}: TextAreaField): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        return <Label {...remainingStyle}>
            {label}
            <div>
                <TextArea width="100%" onChange={e => ctx.fields[id] = e.target.value} {...props} />
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
