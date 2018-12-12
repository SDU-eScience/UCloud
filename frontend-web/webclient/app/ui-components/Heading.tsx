import * as React from "react";
import Text from './Text'

export const h1 = ({ children, ...props }) => (<Text as="h1" bold={true} fontSize={6} m={0} {...props}>{children}</Text>);
export const h2 = ({ children, ...props }) => (<Text as="h2" bold={true} fontSize={5} m={0} {...props}>{children}</Text>);
export const h3 = ({ children, ...props }) => (<Text as="h3" regular={true} fontSize={4} m={0} {...props}>{children}</Text>);
export const h4 = ({ children, ...props }) => (<Text as="h4" regular={true} fontSize={3} m={0} {...props}>{children}</Text>);
export const h5 = ({ children, ...props }) => (<Text as="h5" bold={true} fontSize={2} m={0} {...props}>{children}</Text>);
export const h6 = ({ children, ...props }) => (<Text as="h6" bold={true} caps={true} fontSize={0} m={0} {...props}>{children}</Text>);

const Heading = h3

export default Heading;