import styled from "styled-components";
import Button from "./Button";
import Flex from "./Flex";
import {height} from "styled-system";

const ButtonGroup = styled(Flex)`
    ${height}
    
    & ${Button} {
        height: 100%;
        width: 100%;
        padding: 0 10px;
        border-radius: 0px;
        white-space: nowrap;
    }

    & > ${Button}:last-child, .last {
        border-top-right-radius: 3px;
        border-bottom-right-radius: 3px;
    }

    & > ${Button}:first-child, .first {
        border-top-left-radius: 3px;
        border-bottom-left-radius: 3px;
    }
`;

ButtonGroup.displayName = "ButtonGroup";
ButtonGroup.defaultProps = {
    height: "35px"
};

export default ButtonGroup;
