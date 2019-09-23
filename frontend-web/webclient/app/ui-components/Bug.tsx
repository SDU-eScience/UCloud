import * as React from "react";
import * as icons from "./icons";

const randomInt = (min:number, max:number) => {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function Bug({size, theme, color2, spin, ...props}) {
  const [idx, setIdx] = React.useState(0);
  const bugs: string[] = ['bug1','bug2','bug3','bug4','bug5','bug6'];

  React.useEffect(() => {
    const timer = setInterval(() => {
      setIdx(randomInt(0, bugs.length-1));
    }, 300000); //5 minutes in ms
    return () => clearInterval(timer);
  }, []);

  const Component = icons[bugs[idx]];

  return (
    <Component width={size} height={size} color2={color2 ? theme.colors[color2] : undefined} {...props} />
  );
}

export default Bug;