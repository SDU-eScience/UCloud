import * as React from 'react'
import styled from 'styled-components'
import { space, color } from 'styled-system'
import { icons } from './icons.json'
import theme from './theme'

const getPath = ({ name }) => icons[name];

const Svg = styled.svg`
  flex: none;
  ${space} ${color};
`

const IconBase = ({ name, size, ...props }): JSX.Element => {
  const icon = getPath({ name })
  if (!icon) return (<></>);

  const listPath = icon.path.map((path: [string, string?], i: number) =>
    //fill can be null, in which case it will not render 
    <path key={i} d={path[0]} fill={path[1]} />
  )

  return (
    <Svg
      {...props}
      viewBox={icon.viewBox}
      width={size}
      height={size}
      fill="currentcolor"
    >
      {listPath}
    </Svg>
  )
}

export interface IconProps {
  name: IconName
  size?: string | number
  color?: string
  cursor?: string
}

const Icon = styled(IconBase) <IconProps>`
  flex: none;
  cursor: ${(props: IconProps) => props.cursor}
  ${space} ${color};
`;

Icon.displayName = "Icon"

Icon.defaultProps = {
  theme,
  cursor: "auto",
  name: "notification",
  size: 24
}

// Use to see every available icon.
export const EveryIcon = () => (
  <>
    {Object.keys(icons).map((it: IconName, i: number) =>
      (<span><span>{it}</span>: <Icon name={it} key={i} />, </span>)
    )}
  </>
);

export type IconName =
  "ac" |
  "accessible" |
  "activity" |
  "admin" |
  "apps" |
  "arrival" |
  "arrowDown" |
  "arrowLeft" |
  "arrowRight" |
  "arrowUp" |
  "attention" |
  "automatic" |
  "bag" |
  "beach" |
  "bed" |
  "boxChecked" |
  "boxEmpty" |
  "boxMinus" |
  "boxPlus" |
  "breakfast" |
  "build" |
  "business" |
  "calendar" |
  "carCircle" |
  "carDoor" |
  "carriage" |
  "cars" |
  "casino" |
  "chart" |
  "chat" |
  "check" |
  "chevronDown" |
  "chevronLeft" |
  "chevronRight" |
  "chevronUp" |
  "cityView" |
  "clock" |
  "close" |
  "cloud" |
  "collisionCoverage" |
  "coupon" |
  "creditCard" |
  "cruises" |
  "dashboard" |
  "departure" |
  "devices" |
  "directions" |
  "discount" |
  "document" |
  "dollar" |
  "dollarCircle" |
  "earlyBird" |
  "edit" |
  "electric" |
  "email" |
  "event" |
  "eventAvailable" |
  "eventBusy" |
  "facebook" |
  "favoriteHotel" |
  "files" |
  "filter" |
  "fitness" |
  "flame" |
  "flightCircle" |
  "flightCoverage" |
  "flights" |
  "freeCancellation" |
  "fridge" |
  "gallery" |
  "gas" |
  "globe" |
  "gps" |
  "graph" |
  "grid" |
  "guests" |
  "help" |
  "home" |
  "hotelCircle" |
  "hotels" |
  "hybrid" |
  "inclusive" |
  "information" |
  "informationOutline" |
  "instagram" |
  "key" |
  "kitchenette" |
  "laptop" |
  "lateNight" |
  "list" |
  "lock" |
  "loyalty" |
  "luggage" |
  "manual" |
  "map" |
  "menu" |
  "microwave" |
  "mileage" |
  "minus" |
  "notification" |
  "overnight" |
  "parking" |
  "pets" |
  "phone" |
  "picture" |
  "pin" |
  "copy" |
  "download" |
  "trash" |
  "move" |
  "open" |
  "rename" |
  "uploadFolder" |
  "upload" |
  "delete" |
  "play" |
  "apps" |
  "starRibbon" |
  "starFilled" |
  "starEmpty" |
  "activity" |
  "dashboard" |
  "shares" |
  "publish" |
  "admin" |
  "notification" |
  "search" |
  "radioChecked" |
  "radioEmpty" |
  "chevronDown" |
  "boxEmpty" |
  "boxChecked";

export default Icon
