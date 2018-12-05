import * as React from "react";
import { Form as SForm, Button as SButton } from "semantic-ui-react";
import { identifierTypes } from "DefaultObjects";
import { allLicenses } from "./licenses";
import { Contributor, RelatedIdentifier, Subject, getByPath, updateById } from "./api";
import { blankOrUndefined } from "UtilityFunctions";
import * as PropTypes from "prop-types";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { CreateUpdateProps, CreateUpdateState } from ".";
import { getQueryParam } from "Utilities/URIUtilities";
import { projectViewPage } from "Utilities/ProjectUtilities";
import { Input, DataList, Box } from "ui-components";
import { contentValuePairLicenses } from "ui-components/DataList";

const newContributor = (): Contributor => ({ name: "", affiliation: "", orcId: "", gnd: "" });
const newIdentifier = (): RelatedIdentifier => ({ identifier: "", relation: "" });
const newSubject = (): Subject => ({ term: "", identifier: "" });

const contributorHasValue = (contributor: Contributor): boolean => {
    return (
        !blankOrUndefined(contributor.affiliation) ||
        !blankOrUndefined(contributor.orcId) ||
        !blankOrUndefined(contributor.gnd) ||
        !blankOrUndefined(contributor.name)
    );
}

const subjectHasValue = (subject: Subject): boolean => {
    return (
        !blankOrUndefined(subject.identifier) ||
        !blankOrUndefined(subject.term)
    );
}

const identifierHasValue = (identifier: RelatedIdentifier): boolean => {
    return (
        !blankOrUndefined(identifier.identifier) ||
        !blankOrUndefined(identifier.relation)
    );
};

const filePathFromProps = (props: CreateUpdateProps): string | null => {
    return getQueryParam(props, "filePath");
}

export class CreateUpdate extends React.Component<CreateUpdateProps, any> {
    constructor(props, ctx) {
        super(props);
        const path = filePathFromProps(props);
        this.state = {
            path,
            title: "",
            description: "",
            license: null,
            keywords: [""],
            notes: "",
            dataManagementPlan: "",
            contributors: [newContributor()],
            references: [""],
            grants: [""],
            subjects: [newSubject()],
            relatedIdentifiers: [newIdentifier()],
            errors: { contributors: {}, subjects: {}, relatedIdentifiers: {} }
        };
        ctx.store.dispatch(updatePageTitle("Edit Project"));
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvList = this.setStateEvList.bind(this);
    }

    static contextTypes = {
        store: PropTypes.object
    }

    componentDidMount() {
        getByPath(this.state.path).then(it => this.setMetadata(it, this.state.path));
    }

    setMetadata(it, path: string) {
        const md = it.metadata;
        const license = allLicenses.find(it => it.identifier == md.license);
        const mappedLicense = license ? {
            title: license.name,
            link: license.link,
            identifier: license.identifier
        } : null;

        this.setState(() => ({
            path: path,
            id: md.id,
            title: md.title,
            description: md.description,
            license: mappedLicense,
            keywords: md.keywords ? md.keywords : [""],
            notes: md.notes ? md.notes : "",
            contributors: md.contributors ? md.contributors : [newContributor()],
            references: md.references ? md.references : [""],
            grants: md.grants ? md.grants.map(it => it ? it.id : "") : [""],
            subjects: md.subjects ? md.subjects : [newSubject()],
            relatedIdentifiers: md.relatedIdentifiers ? md.relatedIdentifiers : [newIdentifier()]
        }));
    };

    shouldComponentUpdate(nextProps, _nextState) {
        const path = filePathFromProps(nextProps);
        if (!!path && path !== this.state.path) {
            getByPath(path).then(it => this.setMetadata(it, path))
        }
        return true;
    }

    onSubmit(e) {
        e.preventDefault();

        const hasErrors = this.validateForm();
        console.log(hasErrors);

        if (!hasErrors) {
            const s = this.state;
            const licenseIdentifier = s.license ? s.license.identifier : null;

            const payload = {
                id: s.id,
                title: s.title,
                description: s.description,
                license: licenseIdentifier,
                keywords: s.keywords.filter(e => !blankOrUndefined(e)),
                // notes, // TODO Needs to be user editable
                // dataManagementPlan: s.dataManagementPlan,
                contributors: s.contributors.filter(e => contributorHasValue(e)),
                references: s.references.filter(e => !blankOrUndefined(e)),
                subjects: s.subjects.filter(e => subjectHasValue(e)),
                relatedIdentifiers: s.relatedIdentifiers.filter(e => identifierHasValue(e)),
                grants: s.grants.filter(e => !blankOrUndefined(e)).map(it => ({ id: it }))
            };

            updateById(payload)
                .then(it => this.props.history.push(projectViewPage(this.state.path)))
                .catch(it => console.warn("Failure!", it));
        }
    }

    validateForm(): boolean {
        let errors = {};

        if (blankOrUndefined(this.state.title)) errors["title"] = true;
        if (blankOrUndefined(this.state.description)) errors["description"] = true;

        let errCollaborators = {};
        this.state.contributors.forEach((element, index) => {
            if (contributorHasValue(element)) {
                if (blankOrUndefined(element.name)) errCollaborators[index] = true;
            }
        });
        errors["contributors"] = errCollaborators;

        let errSubjects = {};
        this.state.subjects.forEach((element, index) => {
            if (subjectHasValue(element)) {
                if (blankOrUndefined(element.term)) errSubjects[index] = true;
            }
        });
        errors["subjects"] = errSubjects;

        let errIdentifiers = {};
        this.state.relatedIdentifiers.forEach((element, index) => {
            if (identifierHasValue(element)) {
                if (blankOrUndefined(element.identifier)) errIdentifiers[index] = true;
            }
        });
        errors["relatedIdentifiers"] = errIdentifiers;

        this.setState({ errors });

        let hasError = false;
        Object.keys(errors).forEach(key => {
            if (typeof errors[key] === "object") {
                Object.keys(errors[key]).forEach(nestedKey => {
                    if (errors[key][nestedKey] === true) {
                        hasError = true;
                    }
                });
            } else if (errors[key] === true) {
                hasError = true;
            }
        });

        return hasError;
    }


    addRow(e, key) {
        e.preventDefault();
        this.setState(() => ({ [key]: this.state[key].concat([""]) }));
    }

    addCollaborator(e) {
        e.preventDefault();
        this.setState(() => ({ contributors: this.state.contributors.concat(newContributor()) }));
    }

    addSubject(e) {
        e.preventDefault();
        this.setState(() => ({ subjects: this.state.subjects.concat(newSubject()) }));
    }

    addIdentifier(e) {
        e.preventDefault();
        this.setState(() => ({ relatedIdentifiers: this.state.relatedIdentifiers.concat(newIdentifier()) }));
    }

    setStateEv(key) {
        return (e, { value }) => {
            this.setState(() => ({ [key]: value }));
        };
    }

    setStateEvList(key: keyof CreateUpdateState) {
        return (value, index, member) => {
            const list = this.state[key];
            if (!!member) list[index][member] = value;
            else list[index] = value;
            this.setState(() => ({ [key]: list }));
        };
    }

    render() {
        return (
            <SForm onSubmit={this.onSubmit}>
                <SForm.Field required>
                    <label>Title</label>
                    <SForm.Input
                        placeholder="Title"
                        value={this.state.title}
                        error={this.state.errors.title}
                        onChange={this.setStateEv("title")}
                        required
                    />
                </SForm.Field>
                <SForm.Field required>
                    <label>Description</label>
                    <SForm.TextArea
                        value={this.state.description}
                        error={this.state.errors.description}
                        rows={15}
                        placeholder="Description"
                        onChange={this.setStateEv("description")}
                        required
                    />
                </SForm.Field>
                <Box width="100%">
                    <label>License</label>
                    <p>
                        {this.state.license ?
                            <span>
                                Selected
                                {" "}
                                <a href={this.state.license.link}>
                                    {this.state.license.title}
                                </a>

                                <SButton
                                    type="button"
                                    basic
                                    onClick={() => this.setState({ license: null })}
                                    icon="remove"
                                    size="tiny"
                                    floated="right"
                                />
                            </span>

                            :

                            <span>
                                No license selected
                            </span>
                        }
                    </p>
                    <LicenseDropdown onChange={this.setStateEv("license")} />
                </Box>

                <SForm.Field>
                    <label>Keywords</label>
                    <FormFieldList
                        items={this.state.keywords}
                        name="keyword"
                        onChange={this.setStateEvList("keywords")}
                    />
                    <SButton
                        type="button"
                        content="New keyword"
                        onClick={(e) => this.addRow(e, "keywords")}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>Notes</label>
                    <SForm.TextArea
                        value={this.state.notes}
                        placeholder="Notes..."
                        onChange={this.setStateEv("notes")}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>Data Management Plan</label>
                    <SForm.TextArea
                        value={this.state.dataManagementPlan}
                        placeholder="Data Management Plan..."
                        onChange={this.setStateEv("dataManagementPlan")}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>Contributors</label>
                    <Contributors
                        contributors={this.state.contributors}
                        errors={this.state.errors.contributors}
                        onChange={this.setStateEvList("contributors")}
                    />
                    <SButton
                        type="button"
                        content="Add collaborator"
                        onClick={(e) => this.addCollaborator(e)}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>References</label>
                    <FormFieldList
                        name="reference"
                        items={this.state.references}
                        onChange={this.setStateEvList("references")}
                    />
                    <SButton
                        type="button"
                        content="Add reference"
                        onClick={(e) => this.addRow(e, "references")}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>Grants</label>
                    <FormFieldList
                        name="grant"
                        items={this.state.grants}
                        onChange={this.setStateEvList("grants")}
                    />
                    <SButton
                        type="button"
                        content="Add grant"
                        onClick={(e) => this.addRow(e, "grants")}
                    />
                </SForm.Field>


                <SForm.Field>
                    <label>Subjects</label>
                    <Subjects
                        subjects={this.state.subjects}
                        onChange={this.setStateEvList("subjects")}
                        errors={this.state.errors.subjects}
                    />
                    <SButton
                        type="button"
                        content="Add subject"
                        onClick={(e) => this.addSubject(e)}
                    />
                </SForm.Field>

                <SForm.Field>
                    <label>Related identifiers</label>

                    <RelatedIdentifiers
                        relatedIdentifiers={this.state.relatedIdentifiers}
                        onChange={this.setStateEvList("relatedIdentifiers")}
                        errors={this.state.errors.relatedIdentifiers}
                    />
                    <SButton
                        type="button"
                        content="Add identifier"
                        onClick={(e) => this.addIdentifier(e)}
                    />
                </SForm.Field>

                <SButton
                    positive
                    type="button"
                    content="Submit"
                    floated="right"
                    icon="checkmark"
                    onClick={(e) => this.onSubmit(e)}
                />
                <div className="clear"></div>
            </SForm>
        )
    }
}

interface LicenseDropdownProps {
    onChange: (e: any, details: any) => void
}

class LicenseDropdown extends React.Component<LicenseDropdownProps> {

    render() {
        return (
            <DataList
                placeholder="Search for a license..."
                onSelect={identifier => {
                    const license = allLicenses.find(it => it.identifier === identifier)!;
                    const value = { ...license, title: license.name }
                    this.props.onChange({}, { value });
                }}
                options={contentValuePairLicenses}
            />
        );
    }
}

interface SubjectsProps {
    subjects: Subject[]
    onChange: (value, index: number, key: string) => void
    errors: any
}

const Subjects = ({ subjects, errors, onChange }: SubjectsProps) => {
    const elements = subjects.map((value, index) => {
        const sharedProps = { value, onChange, index };
        return <SForm.Group key={index} widths="equal">
            <InputInList name="term" displayName="Term" error={errors[index]}
                {...sharedProps} />
            <InputInList name="identifier" displayName="Identifier" {...sharedProps} />
        </SForm.Group>;
    });
    return <>{elements}</>;
};

interface RelatedIdentifiersProps {
    relatedIdentifiers: RelatedIdentifier[]
    onChange: (value, index: number, key: string) => void
    errors: any
}

const RelatedIdentifiers = ({ relatedIdentifiers, errors, onChange }: RelatedIdentifiersProps) => {
    const elements = relatedIdentifiers.map((value, index) => {
        const sharedProps = { value, onChange, index };
        return <SForm.Group key={index} widths="equal">
            <InputInList name="identifier" displayName="Identifier" error={errors[index]}
                {...sharedProps} />

            <SForm.Dropdown label="Type"
                search
                searchInput={{ type: "string" }}
                selection
                options={identifierTypes}
                value={value.relation}
                placeholder="Select type"
                onChange={(e, { value }) => onChange(value, index, "relation")}
            />
        </SForm.Group>;
    });

    return <>{elements}</>;
};

interface CollaboratorsProps {
    contributors: Contributor[]
    onChange: (value, index: number, key: string) => void
    errors: any
}

const Contributors = ({ contributors, errors, onChange }: CollaboratorsProps) =>
    <>
        {
            contributors.map((value, index) => {
                const sharedProps = { value, onChange, index };

                return <SForm.Group key={index} widths="equal">
                    <InputInList name="name" displayName="Name" {...sharedProps}
                        error={errors[index]} />
                    <InputInList name="affiliation" displayName="Affiliation" {...sharedProps} />
                    <InputInList name="orcId" displayName="ORCID" {...sharedProps} />
                    <InputInList name="gnd" displayName="GND" {...sharedProps} />
                </SForm.Group>
            })
        }
    </>

const InputInList = (p: {
    name: string,
    value: any,
    displayName: string,
    index: number,
    onChange: (value, i: number, name: string) => void,
    error?: any
}) => (
        <SForm.Input
            fluid
            label={p.displayName}
            placeholder={`${p.displayName}...`}
            value={p.value[p.name]}
            onChange={(e, { value }) => p.onChange(value, p.index, p.name)}
            error={p.error}
        />
    );

const FormFieldList = ({ items, name, onChange }) =>
    <>
        {
            items.map((c, i) =>
                <Input
                    mt="0.4em"
                    mb="0.4em"
                    key={i}
                    value={c}
                    placeholder={`Enter ${name}`}
                    onChange={({ target }) => onChange(target.value, i)}
                />)
        }
    </>;
