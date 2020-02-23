// ╦ ╦╔═╗┬  ┌─┐┬ ┬┌┬┐  ╔═╗┌─┐┬  ┌─┐┬─┐  ╔╦╗┬ ┬┌─┐┌┬┐┌─┐
// ║ ║║  │  │ ││ │ ││  ║  │ ││  │ │├┬┘   ║ ├─┤├┤ │││├┤ 
// ╚═╝╚═╝┴─┘└─┘└─┘─┴┘  ╚═╝└─┘┴─┘└─┘┴└─   ╩ ┴ ┴└─┘┴ ┴└─┘

export const app_colors = {
    "gray":    ["#eceff4", "#dde1eb", "#cad3df", "#AAB9CA", "#8493A8", "#64758c", "#4c596b", "#394051", "#2d3540"],
    "red":     ["#FFE1D2", "#FFBCA5", "#FF9079", "#FF6657", "#FF2020", "#DB1728", "#B7102D", "#930A2D", "#7A062E"],
    "magenta": ["#FDCFE6", "#FCA1D7", "#F771CD", "#F04DCC", "#e716cc", "#C610BF", "#9D0BA6", "#730785", "#55046E"],
    "purple":  ["#FAD7FF", "#F1AFFF", "#E387FF", "#D369FF", "#b838ff", "#9028DB", "#6C1CB7", "#4D1193", "#360A7A"],
    "blue":    ["#CCE9FF", "#99D0FF", "#66B2FF", "#3F97FF", "#006AFF", "#0051DB", "#003CB7", "#002A93", "#001E7A"],
    "cyan":    ["#D7FFFD", "#AFFBFF", "#87F1FF", "#69E4FF", "#38CDFF", "#28A1DB", "#1C7AB7", "#115793", "#0A3E7A"],
    "green":   ["#E4FCD6", "#C4F9AF", "#99ED83", "#70DC61", "#38C633", "#25AA2C", "#198E29", "#107225", "#095F23"],
    "yellow":  ["#FFF9CC", "#FFF299", "#FFEA66", "#FFE13F", "#ffd300", "#DBB100", "#B79100", "#937200", "#7A5C00"],
    "orange":  ["#FFEFCC", "#FFDB99", "#FFC166", "#FFA83F", "#FF7F00", "#DB6200", "#B74900", "#933300", "#7A2400"],
};

type OpacityValue = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 ;
export function transparent_color(hexcode: string, opacity: OpacityValue) {
    const opacity_value = 0.1*opacity;
    const values = [
      hexcode.substring(1, 3),
      hexcode.substring(3, 5),
      hexcode.substring(5, 7),
    ].map(string => parseInt(string, 16));
    return `rgba(${values[0]}, ${values[1]}, ${values[2]}, ${opacity_value})`;
  }

