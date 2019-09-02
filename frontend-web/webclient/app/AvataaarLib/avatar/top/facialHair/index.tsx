import * as React from 'react'

import BeardLight from './BeardLight'
import BeardMajestic from './BeardMajestic'
import BeardMedium from './BeardMedium'
import Blank from './Blank'
import MoustacheFancy from './MoustacheFancy'
import MoustacheMagnum from './MoustacheMagnum'
import { FacialHairOption } from '../../../options'
import Selector from 'AvataaarLib/options/Selector'

export default class FacialHair extends React.Component {
  render () {
    return (
      <Selector option={FacialHairOption} defaultOption={Blank}>
        <Blank />
        <BeardMedium />
        <BeardLight />
        <BeardMajestic />
        <MoustacheFancy />
        <MoustacheMagnum />
      </Selector>
    )
  }
}
