export const _wccss = `
/* reset */
div,ul,ol,li,h1,h2,h3,h4,h5,h6,pre,form,fieldset,legend,input,textarea,p,article,aside,figcaption,figure,nav,section,mark,audio,video,main{margin:0;padding:0}
article,aside,figcaption,figure,nav,section,main{display:block}
fieldset,img{border:0}
address,caption,cite,em,strong{font-style:normal;font-weight:400}
ol,ul{list-style:none}
caption{text-align:left}
h1,h2,h3,h4,h5,h6{font-size:100%;font-weight:400}
abbr{border:0;font-variant:normal}
input,textarea,select{font-family:inherit;font-size:inherit;font-weight:inherit;}
body{-webkit-text-size-adjust:none}
select,input,button,textarea{font:100% arial,helvetica,clean,sans-serif;}
del{font-style:normal;text-decoration:none}
pre{font-family:monospace;line-height:100%}
progress{-webkit-appearance:none;appearance:none;overflow:hidden;border:0 none;}

/* component style */
a{cursor:pointer;text-decoration:none;}
.stuff{text-indent:100%;white-space:nowrap;overflow:hidden;}
.aspect-ratio{position:relative;width:100%;--w:4;--h:3;}
.aspect-ratio:before{content:'';width:100%;padding-top:calc(var(--h) * 100% / var(--w));display:block;}
.aspect-ratio .content{position:absolute;top:0;left:0;right:0;bottom:0;}
.line-clampin{display:-webkit-box;-webkit-line-clamp:var(--line-clamp, 2);-webkit-box-orient:vertical;text-overflow:ellipsis;overflow:hidden;}
.overscrolling{-webkit-overflow-scrolling:touch;overflow:hidden;overflow-y:scroll;overscroll-behavior:contain;}
.overscrolling-x{-webkit-overflow-scrolling:touch;overflow:hidden;overflow-x:scroll;overscroll-behavior:contain;}
.absolute-center{position:absolute;top:0;left:0;bottom:0;right:0;margin:auto;}

:host{all:initial;font-family:system-ui,sans-serif;text-size-adjust:100%;-ms-text-size-adjust:100%;-webkit-text-size-adjust:100%;font-size:16px;-webkit-tap-highlight-color:transparent;}
`;