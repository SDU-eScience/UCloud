import {Client} from "Authentication/HttpClientInstance";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Checkbox, Flex, Input, Label, Select, TextArea} from "ui-components";
import {stopPropagation, stopPropagationAndPreventDefault} from "UtilityFunctions";

export enum CreationType {
    CHECKBOX,
    INPUT,
    TEXTAREA,
    SELECT,
    GROUP
}

interface Field {
    label: string;
    required: boolean;
    width?: string;
    ml?: string;
    mr?: string;
}

interface CheckboxField extends Field {
    type: CreationType.CHECKBOX;
    value: boolean;
}

export interface InputField extends Field {
    type: CreationType.INPUT;
    inputType: "text" | "number";
    placeholder?: string;
    min?: number;
    max?: number;
    ref: React.RefObject<HTMLInputElement>;
}

interface TextAreaField extends Field {
    type: CreationType.TEXTAREA;
    placeholder?: string;
    ref: React.RefObject<HTMLTextAreaElement>;
    rows?: number;
}

interface SelectField extends Field {
    type: CreationType.SELECT;
    options: {value: number | string, text: string}[];
    ref: React.RefObject<HTMLSelectElement>;
}

interface Styling {
    ml?: string;
    mr?: string;
    width?: string;
}

interface Group {
    type: CreationType.GROUP;
    fields: ElementField[];
}

export type ElementField = CheckboxField | InputField | TextAreaField | SelectField;
export type CreationField = ElementField | Group;

export class FieldCreator {
    static makeTextField(placeholder: string, label: string, required: boolean, styling?: Styling): InputField {
        return {
            type: CreationType.INPUT,
            inputType: "text",
            placeholder,
            label,
            required,
            width: styling?.width,
            ml: styling?.ml,
            mr: styling?.mr,
            ref: React.createRef()
        }
    }

    static makeNumberField(placeholder: string, label: string, required: boolean, min?: number, max?: number, styling?: Styling): InputField {
        return {
            type: CreationType.INPUT,
            inputType: "number",
            placeholder,
            label,
            required,
            min,
            max,
            width: styling?.width,
            ml: styling?.ml,
            mr: styling?.mr,
            ref: React.createRef()
        }
    }

    static makeCheckboxField(label: string, value: boolean, styling?: Styling): CheckboxField {
        return {
            type: CreationType.CHECKBOX,
            label,
            required: true,
            value,
            width: styling?.width,
            ml: styling?.ml,
            mr: styling?.mr,
        }
    }

    static makeTextAreaField(label: string, required: boolean, placeholder: string, rows: number, styling?: Styling): TextAreaField {
        return {
            type: CreationType.TEXTAREA,
            label,
            required,
            placeholder,
            rows,
            width: styling?.width,
            ml: styling?.ml,
            mr: styling?.mr,
            ref: React.createRef()
        }
    }

    static makeSelectField(label: string, required: boolean, options: [], styling?: Styling): SelectField {
        return {
            type: CreationType.SELECT,
            label,
            options,
            required,
            width: styling?.width,
            ml: styling?.ml,
            mr: styling?.mr,
            ref: React.createRef()
        }
    }

    static makeGroup(fields: ElementField[]): CreationField {
        return {
            type: CreationType.GROUP,
            fields
        }
    }
}

export abstract class ProductCreator<Request> {
    public abstract title: string;
    public abstract fields: CreationField[];
    public abstract isUnavailable(): boolean;
    public abstract createRequest(): APICallParameters<Request>;

    private async onSubmit(): Promise<void> {
        const result = this.validate();
        if (result.validated === true) {
            const request = this.createRequest();
            Client.call(request as any);
        } else {
            snackbarStore.addFailure(result.missingFields.join(", ") ?? "", false)
        }
    }

    public render(): JSX.Element {
        return (
            <form onSubmit={e => {stopPropagationAndPreventDefault(e); this.onSubmit()}} style={{maxWidth: "800px", marginTop: "30px", marginLeft: "auto", marginRight: "auto"}}>
                {this.fields.map(it => this.renderField(it))}
                <Button type="submit" my="12px" disabled={false}>Submit</Button>
            </form>
        );
    }

    private renderField(field: CreationField): JSX.Element {
        switch (field.type) {
            case CreationType.GROUP: {
                return (<Flex key={"group" + field.fields[0].label}>
                    {field.fields.map(field => this.renderField(field))}
                </Flex>);
            }
            case CreationType.CHECKBOX: {
                return <Label key={field.label}>
                    <Checkbox onClick={() => !field.value} defaultChecked={field.value} onChange={stopPropagation} />
                    {field.label}
                </Label>
            }
            case CreationType.INPUT: {
                return (
                    <Label width={field.width} ml={field.ml} mr={field.mr} key={field.label}>
                        {field.label}
                        <Input required={field.required} type={field.inputType} min={field.min} max={field.max} ref={field.ref} placeholder={field.placeholder} />
                    </Label>
                );
            }
            case CreationType.TEXTAREA: {
                return (
                    <Label width={field.width} ml={field.ml} mr={field.mr} key={field.label}>
                        {field.label}
                        <TextArea required={field.required} rows={field.rows} />
                    </Label>
                );
            }
            case CreationType.SELECT: {
                return (
                    <Label width={field.width} ml={field.ml} mr={field.mr} key={field.label}>
                        {field.label}
                        <Select selectRef={field.ref}>
                            {field.required ? null : <option></option>}
                            {field.options.map(option => (
                                <option key={option.value} value={option.value}>{option.text}</option>
                            ))}
                        </Select>
                    </Label>
                )
            }
        }
    }

    public extractFieldsByLabel(fields: CreationField[]): Record<string, string | boolean | number> {
        const result = {};
        for (const field of fields) {
            switch (field.type) {
                case CreationType.GROUP: {
                    const groupResult = this.extractFieldsByLabel(field.fields);
                    Object.keys(groupResult).forEach(res => result[res] = groupResult[res]);
                    continue;
                }
                case CreationType.CHECKBOX: {
                    result[field.label] = field.value;
                    continue;
                }
                case CreationType.INPUT: {
                    result[field.label] = field.ref.current?.value ?? "";
                    continue;
                }
                case CreationType.TEXTAREA: {
                    result[field.label] = field.ref.current?.value ?? "";
                    continue;
                }
                case CreationType.SELECT: {
                    result[field.label] = field.ref.current?.value ?? "";
                    continue;
                }
            }
        }
        return result;
    }

    private requiredFields(fields: CreationField[]): string[] {
        const requiredFields: string[] = [];

        for (const field of fields) {
            if (field.type === CreationType.GROUP) {
                for (const innerField of field.fields) {
                    if (innerField.required) {
                        requiredFields.push(innerField.label);
                    }
                }
            } else {
                if (field.required) {
                    requiredFields.push(field.label);
                }
            }
        }

        return requiredFields;
    }

    public validate(): {validated: boolean, missingFields: string[]} {

        const fields = this.extractFieldsByLabel(this.fields);
        const requiredFields = this.requiredFields(this.fields);

        const missingFields: string[] = [];

        for (const required of requiredFields) {
            const value = fields[required];
            if ("" === value || null === value || undefined === value) {
                missingFields.push(required);
            }
        }

        return {validated: missingFields.length === 0, missingFields};
    }
}

export default ProductCreator;