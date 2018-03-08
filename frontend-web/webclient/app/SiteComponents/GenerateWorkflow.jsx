import React from "react";
import SectionContainerCard from "./SectionContainerCard";
import {Button} from "react-bootstrap";

class GenerateWorkflow extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            currentType: "",
            parameters: [],
        };
        this.addParameterType = this.addParameterType.bind(this);
        this.parameterTypeChange = this.parameterTypeChange.bind(this);
    }

    addParameterType() {
        const { parameters } = this.state;
        switch (this.state.currentType) {
            case "float":
                parameters.push({ type: this.state.currentType, name: "", prettyName: "", max: 0, min: 0 });
                break;
            case "int":
                parameters.push({ type: this.state.currentType, name: "", prettyName: "", max: 0, min: 0 });
                break;
            case "string":
                parameters.push({ type: this.state.currentType, name: "", prettyName: "", length: 0 });
                break;
            case "range":
                parameters.push({ type: this.state.currentType, name: "", prettyName: "", max: 0, min: 0, step: 1 });
                break;
        }
    }

    parameterTypeChange(e) {
        if (!e.target) {
            return
        }
        let type = e.target.value;
        this.setState(() => ({
            currentType: type,
        }));
    }


    render() {
        return (
            <SectionContainerCard>
                <form>
                    <select name="ParameterType" onChange={(e) => this.parameterTypeChange(e)}>
                        <option value="float">Floating Point Value</option>
                        <option value="int">Integer Value</option>
                        <option value="string">String Value</option>
                        <option value="range">Range</option>
                    </select>
                    <span><Button onClick={() => this.addParameterType()}>Add Parameter type</Button></span>
                </form>
                <Parameters/>
            </SectionContainerCard>
        );
    }
}

const Parameters = () => {
    return null;
};

export default GenerateWorkflow;