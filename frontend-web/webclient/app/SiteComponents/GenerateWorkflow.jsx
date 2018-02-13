import React from "react";
import SectionContainerCard from "./SectionContainerCard";

class GenerateWorkflow extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            parameters: [],
        };
    }

    render() {
        return (
            <SectionContainerCard>
                <form>
                    <select name="ParameterType">
                        <option value="float">Floating Point Value</option>
                        <option value="int">Integer Value</option>
                        <option value="string">String Value</option>
                        <option value="range">Range</option>
                    </select>
                </form>
            </SectionContainerCard>
        );
    }
}

export default GenerateWorkflow;