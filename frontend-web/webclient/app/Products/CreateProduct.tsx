import {callAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Checkbox, Input, Label, Select} from "ui-components";
import {LabelProps} from "ui-components/Label";
import {errorMessageOrDefault, stopPropagation, stopPropagationAndPreventDefault} from "UtilityFunctions";

interface DataType {
    required: string[];
    fields: Record<string, any>;
}
const DataContext = React.createContext<DataType>({required: [], fields: {}});

export default abstract class ResourceForm<Request> extends React.Component<{
    createRequest: (d: DataType) => APICallParameters<Request>;
    title: string;
    formatError?: (errors: string[]) => string;
    onSubmitSucceded?: (res: any, d: DataType) => void;
}> {
    public data: DataType = {required: [], fields: {}};

    public resetData(): void {
        this.data = {required: [], fields: {}};
    }

    private async onSubmit(): Promise<void> {
        const validated = this.validate();
        if (validated) {
            const request = this.props.createRequest(this.data);
            try {
                const res = await callAPI(request);
                this.props.onSubmitSucceded?.(res, this.data);
            } catch (err) {
                errorMessageOrDefault(err, "Failed to create " + this.props.title.toLocaleLowerCase());
            }
        }
    }

    public render(): JSX.Element {
        return (
            <form onSubmit={e => {stopPropagationAndPreventDefault(e); this.onSubmit()}} style={{maxWidth: "800px", marginTop: "30px", marginLeft: "auto", marginRight: "auto"}}>
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
                case "string":
                    if (value === "") missingFields.push(field);
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

    public static Number({id, label, styling, ...props}: {id: string; step?: string; label: string; required?: boolean; placeholder?: string; min?: number; max?: number; styling: LabelProps}): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        const isInt = !props.step?.includes(".");

        return <Label {...remainingStyle}>
            {label}
            <Input type="number" onChange={e => {ctx.fields[id] = isInt ? parseInt(e.target.value) : parseFloat(e.target.value)}} {...props} />
        </Label>
    }

    public static Select({id, label, options, styling, ...props}: {id: string; label: string; required?: boolean; options: {value: string, text: string}[], styling: LabelProps}): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

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

    public static Checkbox({id, label, ...props}: {id: string; label: string; required?: boolean; defaultChecked: boolean;}): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required, defaultChecked: props.defaultChecked});

        return <Label>
            <Checkbox onClick={() => ctx.fields[id] = !ctx.fields[id]} onChange={stopPropagation} {...props} />
            {label}
        </Label>;
    }

    public static Text({id, label, styling, ...props}: {id: string; label: string; required?: boolean; placeholder?: string; styling: LabelProps;}): JSX.Element {
        const ctx = useResourceFormField({id, required: props.required})

        /* Why in the world is color not allowed? */
        const {color, ...remainingStyle} = styling;

        return <Label {...remainingStyle}>
            {label}
            <Input type="text" onChange={e => ctx.fields[id] = e.target.value} {...props} />
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
