import * as React from "react";
import { identifierTypes } from "DefaultObjects";
import { allLicenses, License } from "./licenses";
import { Contributor, RelatedIdentifier, Subject, getByPath, updateById } from "./api";
import { blankOrUndefined } from "UtilityFunctions";
import * as PropTypes from "prop-types";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { CreateUpdateProps, CreateUpdateState } from ".";
import { getQueryParam } from "Utilities/URIUtilities";
import { projectViewPage } from "Utilities/ProjectUtilities";
import { Input, DataList, Box, Button, Flex, TextArea, Text, Label } from "ui-components";
import { contentValuePairLicenses, contentValuePairIdentifierTypes } from "ui-components/DataList";
import { TextSpan } from "ui-components/Text";
import { connect } from "react-redux";
import { license } from "ui-components/icons";
import { MainContainer } from "MainContainer/MainContainer";

// FIXME: MISSING TYPESAFETY THROUGHOUT

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

const identifierHasValue = (identifier: RelatedIdentifier): boolean =>
    !blankOrUndefined(identifier.identifier) ||
    !blankOrUndefined(identifier.relation);

const filePathFromProps = (props: CreateUpdateProps): string | null =>
    getQueryParam(props, "filePath");



class CreateUpdate extends React.Component<CreateUpdateProps, any> {
    constructor(props) {
        super(props);
        const path = filePathFromProps(props);
        this.state = {
            path: path!,
            title: "",
            description: "",
            license: undefined,
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
        props.dispatch(updatePageTitle("Edit Project"));
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvList = this.setStateEvList.bind(this);
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
            keywords: md.keywords || [""],
            notes: md.notes || "",
            contributors: md.contributors || [newContributor()],
            references: md.references || [""],
            grants: md.grants ? md.grants.map(it => it ? it.id : "") : [""],
            subjects: md.subjects || [newSubject()],
            relatedIdentifiers: md.relatedIdentifiers || [newIdentifier()]
        }));
    };

    shouldComponentUpdate(nextProps: CreateUpdateProps, _nextState) {
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
        return ({ target }) => {
            this.setState(() => ({ [key]: target.value }));
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
            <MainContainer
                main={
                    <form onSubmit={this.onSubmit}>
                        <Box mb="1em">
                            <Label><TextSpan bold>Title</TextSpan><Required />
                                <Input
                                    placeholder="Title"
                                    type="text"
                                    value={this.state.title}
                                    error={this.state.errors.title}
                                    onChange={this.setStateEv("title")}
                                    required
                                />
                            </Label>
                        </Box>
                        <Box mb="1em">
                            <Label>
                                <TextSpan bold>Description</TextSpan><Required />
                                <TextArea
                                    width={1}
                                    rows={6}
                                    value={this.state.description}
                                    placeholder="Description"
                                    onChange={this.setStateEv("description")}
                                    required
                                />
                            </Label>
                        </Box>
                        <Box mb="1em">
                            <label>
                                <Text bold>License</Text>
                                {this.state.license ?
                                    <Flex>
                                        <TextSpan mr="0.4em">Selected</TextSpan>
                                        <a href={this.state.license.link}>
                                            {this.state.license.title}
                                        </a>
                                        <Box ml="auto" />
                                        {/* <Button onClick={() => this.setState({ license: null })}><Icon name="close"/></Button> */}
                                    </Flex>

                                    :

                                    <Box>
                                        No license selected
                            </Box>
                                }
                                <LicenseDropdown onChange={this.setStateEv("license")} />
                            </label>
                        </Box>
                        <Box mb="1em">
                            <label>
                                <TextSpan bold>Keywords</TextSpan>
                                <FormFieldList
                                    items={this.state.keywords}
                                    name="keyword"
                                    onChange={this.setStateEvList("keywords")}
                                />
                            </label>

                            <Button type="button" onClick={e => this.addRow(e, "keywords")}>
                                New keyword
                            </Button>
                        </Box>

                        <Box mb="1em">
                            <label>Notes
                    <TextArea
                                    width={1}
                                    value={this.state.notes}
                                    placeholder="Notes..."
                                    onChange={this.setStateEv("notes")}
                                />
                            </label>
                        </Box>

                        <Box mb="1em">
                            <label>Data Management Plan
                                <TextArea
                                    width={1}
                                    value={this.state.dataManagementPlan}
                                    placeholder="Data Management Plan..."
                                    onChange={this.setStateEv("dataManagementPlan")}
                                />
                            </label>
                        </Box>

                        <Box mb="1em">
                            <label><TextSpan bold>Contributors</TextSpan>
                                <Contributors
                                    contributors={this.state.contributors}
                                    errors={this.state.errors.contributors}
                                    onChange={this.setStateEvList("contributors")}
                                />
                            </label>
                            <Button mt="0.5em"
                                type="button"
                                onClick={e => this.addCollaborator(e)}
                            >Add collaborator</Button>
                        </Box>

                        <Box mb="1em">
                            <label><TextSpan bold>References</TextSpan>
                                <FormFieldList
                                    name="reference"
                                    items={this.state.references}
                                    onChange={this.setStateEvList("references")}
                                />
                            </label>
                            <Button
                                type="button"
                                onClick={e => this.addRow(e, "references")}
                            >Add reference</Button>
                        </Box>

                        <Box mb="1em">
                            <label><TextSpan bold>Grants</TextSpan>
                                <FormFieldList
                                    name="grant"
                                    items={this.state.grants}
                                    onChange={this.setStateEvList("grants")}
                                />
                            </label>
                            <Button
                                type="button"
                                onClick={e => this.addRow(e, "grants")}
                            >Add grant</Button>
                        </Box>


                        <Box mb="1em">
                            <label><TextSpan bold>Subjects</TextSpan>
                                <Subjects
                                    subjects={this.state.subjects}
                                    onChange={this.setStateEvList("subjects")}
                                    errors={this.state.errors.subjects}
                                />
                            </label>
                        </Box>
                        <Button mb="1em" type="button" onClick={e => this.addSubject(e)} >
                            Add subject
                        </Button>
                        <Box mb="1em">
                            <label><TextSpan bold>Related identifiers</TextSpan>
                                <RelatedIdentifiers
                                    relatedIdentifiers={this.state.relatedIdentifiers}
                                    onChange={this.setStateEvList("relatedIdentifiers")}
                                    errors={this.state.errors.relatedIdentifiers}
                                />
                            </label>
                        </Box>
                        <Button
                            type="button"
                            onClick={e => this.addIdentifier(e)}
                        >Add identifier</Button>

                        <Flex>
                            <Box ml="auto" />
                            <Box mb="1em" mt="1em">
                                <Button
                                    color="green"
                                    type="button"
                                    onClick={(e) => this.onSubmit(e)}
                                >Submit</Button>
                            </Box>
                        </Flex>
                    </form >}
            />
        )
    }
}

interface LicenseDropdownProps {
    onChange: (details: any) => void
}

class LicenseDropdown extends React.Component<LicenseDropdownProps> {

    render() {
        return (
            <DataList
                clearOnSelect
                width={"100%"}
                placeholder="Search for a license..."
                onSelect={identifier => {
                    const license = allLicenses.find(it => it.identifier === identifier)!;
                    const value = { title: license.name, link: license.link, identifier: license.identifier }
                    this.props.onChange({ target: { value } });
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
        return (
            <Flex key={index}>
                <Box mr="0.5em" width={1 / 2}><InputInList name="term" displayName="Term" error={errors[index]}
                    {...sharedProps} /></Box>
                <Box width={1 / 2}><InputInList name="identifier" displayName="Identifier" {...sharedProps} /></Box>
            </Flex>);
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
        return <Flex key={index}>
            <Box width={1 / 2} mr="0.5em">
                <InputInList name="identifier" displayName="Identifier" error={errors[index]}
                    {...sharedProps} />
            </Box>
            <Box width={1 / 2}>
                <label><TextSpan bold>Type</TextSpan>
                    <DataList
                        options={contentValuePairIdentifierTypes}
                        placeholder="Select type"
                        onSelect={value => onChange(value, index, "relation")}
                    />
                </label>
            </Box>
        </Flex>;
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

                return (
                    <Flex key={index}>
                        <Box mr="0.5em" width={1 / 4}><InputInList name="name" displayName="Name" {...sharedProps}
                            error={errors[index]} /></Box>
                        <Box mr="0.5em" width={1 / 4}><InputInList name="affiliation" displayName="Affiliation" {...sharedProps} /></Box>
                        <Box mr="0.5em" width={1 / 4}><InputInList name="orcId" displayName="ORCID" {...sharedProps} /></Box>
                        <Box mr="0.5em" width={1 / 4}><InputInList name="gnd" displayName="GND" {...sharedProps} /></Box>
                    </Flex>)
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
        <label><TextSpan bold>{p.displayName}</TextSpan>
            <Input
                placeholder={`${p.displayName}...`}
                value={p.value[p.name]}
                onChange={({ target: { value } }) => p.onChange(value, p.index, p.name)}
                error={p.error}
            />
        </label>
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


const Required = () => <TextSpan color="red">{" *"}</TextSpan>

export default connect()(CreateUpdate);