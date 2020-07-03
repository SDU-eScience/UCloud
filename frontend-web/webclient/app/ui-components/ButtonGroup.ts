import styled from "styled-components";
import Button from "./Button";
import Flex from "./Flex";
import {height, HeightProps} from "styled-system";

const ButtonGroup = styled(Flex)`
    ${height}
    
    & ${Button} {
        height: 100%;
        width: 100%;
        padding: 0 0 0 0;
        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
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
