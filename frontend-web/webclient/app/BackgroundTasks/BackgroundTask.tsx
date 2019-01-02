import * as React from "react";
import { Icon } from "ui-components";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { connect } from "react-redux";

class BackgroundTasks extends React.Component {

    render() {
        const totalTasks = 0;
        if (!totalTasks) return null;
        return (
            <ClickableDropdown
                trigger={<TasksIcon />}
            >
                <div/>
            </ClickableDropdown>
        )
    }
}


const TasksIconBase = styled(Icon)`
    animation: spin 4s linear infinite;
    margin-right: 8px;

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
`;

const TasksIcon = () => <TasksIconBase name="notchedCircle" />

export default connect()(BackgroundTasks);