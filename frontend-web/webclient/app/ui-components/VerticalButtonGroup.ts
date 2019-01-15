import styled from "styled-components";
import { Button, OutlineButton, Flex } from ".";


const VerticalButtonGroup = styled(Flex)`
    flex-direction: column;
    
    //leave some space on top if buttons grow on hover
    margin-top: 4px; 

    & ${Button}, & ${OutlineButton} {
        width: 100%;
        margin-bottom: 8px;
    }
`;

export default VerticalButtonGroup;