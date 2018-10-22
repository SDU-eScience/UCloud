import styled from "styled-components";

export const ArrowUp = styled.div`
    position: absolute;
    width: 0; 
    height: 0; 
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    
    border-bottom: 5px solid rgba(235, 239, 243, 1);
`;

export const HiddenArrowUp = styled(ArrowUp)`
    display: none;
`;
  
/*  
  .arrow-down {
    width: 0; 
    height: 0; 
    border-left: 20px solid transparent;
    border-right: 20px solid transparent;
    
    border-top: 20px solid #f00;
  }
  
  .arrow-right {
    width: 0; 
    height: 0; 
    border-top: 60px solid transparent;
    border-bottom: 60px solid transparent;
    
    border-left: 60px solid green;
  }
  
  .arrow-left {
    width: 0; 
    height: 0; 
    border-top: 10px solid transparent;
    border-bottom: 10px solid transparent; 
    
    border-right:10px solid blue; 
  } */