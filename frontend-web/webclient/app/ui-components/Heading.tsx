import * as React from "react";
import Text from './Text'

export const h1 = (props) => <Text as="h1" bold={true} fontSize={6} m={0} {...props} />
export const h2 = (props) => <Text as="h2" bold={true} fontSize={5} m={0} {...props} />
export const h3 = (props) => <Text as="h3" regular={true} fontSize={4} m={0} {...props} />
export const h4 = (props) => <Text as="h4" regular={true} fontSize={3} m={0} {...props} />
export const h5 = (props) => <Text as="h5" bold={true} fontSize={2} m={0} {...props} />
export const h6 = (props) => <Text as="h6" bold={true} caps={true} fontSize={0} m={0} {...props} />

const Heading = h3

export default Heading;