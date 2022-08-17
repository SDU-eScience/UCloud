export const _wcl = {};

Object.defineProperties(_wcl, {
  getUUID: {
    configurable: true,
    enumerable: true,
    value: function() {
      return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, (c) =>
        (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
      );
    }
  },
  getWCConfig: {
    configurable: true,
    enumerable: true,
    value: async function(host) {
      // gather web component's config from [remoteconfig] or inner script[type=application/json]
      let error, config;

      const remoteconfig = host.getAttribute('remoteconfig');
      const script = host.querySelector('script');

      if (remoteconfig) {
        // fetch remote config once [remoteconfig] exist
        try {
          const configUrl = new URL(remoteconfig);
          config = await fetch(configUrl.toString(), {
            headers: {
              'content-type': 'application/json'
            },
            method: 'GET',
            mode: 'cors'
          })
          .then(
            (response) => {
              if (!response.ok) {
                throw new Error('Network response was not OK');
              }

              return response.json();
            }
          )
          .catch(
            (err) => {
              error = err.message;
              return {};
            }
          );
        } catch(err) {
          error = err.message;
        }
      } else if (script) {
        // apply inner script's config
        try {
          config = JSON.parse(script.textContent.replace(/\n/g, '').trim());
        } catch(err) {
          error = err.message;
        }
      }

      return { config, error };
    }
  },
  whenDefined: {
    configurable: true,
    enumerable: true,
    value: function() {
      /**
       * Check lib ready
       * @example
       whenDefined('document.body', 'YT.Player').then(...)
       */

      const units = Array.from(arguments);

      if (units.indexOf('document.body') === -1) {
        units.push('document.body');
      }

      const promises = units.map(
        (unit) => {
          return new Promise((resolve, reject) => {
            let c, iid;
            const max = 10000;

            c = 0;
            iid = setInterval(() => {
              let _root, parts;
              c += 5;
              if (c > max) {
                clearInterval(iid);
                return reject(new Error(`"${unit}" unit missing.`));
              }

              _root = window;
              parts = unit.split('.');
              while (parts.length) {
                const prop = parts.shift();
                if (typeof _root[prop] === 'undefined') {
                  _root = null;
                  break;
                } else {
                  _root = _root[prop];
                }
              }

              if (_root !== null && document.readyState && document.readyState !== 'loading') {
                clearInterval(iid);
                return resolve();
              }
            }, 5);
          });
        }
      );

      return Promise.all(promises);
    }
  },
  loadScript: {
    configurable: true,
    enumerable: true,
    value: function() {
      /**
       * Load script(s) to the document
       * @example
       loadScript('script-path')

       loadScript('script-path1', 'script-path2')
       */
      const currentScripts = Array.from(document.getElementsByTagName('script')).map(
        (script) => script.src
      );

      Array.from(arguments).forEach(
        (path) => {
          if (currentScripts.includes(path)) {
            return;
          }

          const script = document.createElement('script');
          document.head.appendChild(script);
          script.async = true;
          script.src = path;
        }
      );
    }
  },
  camelCase: {
    configurable: true,
    enumerable: true,
    value: function(str) {
      return str.replace(/-([a-z])/ig,
        function(all, letter) {
          return letter.toUpperCase();
        }
      );
    }
  },
  capitalize: {
    configurable: true,
    enumerable: true,
    value: function(str) {
      return str.replace(/^[a-z]{1}/,
        function($1) {
          return $1.toUpperCase();
        }
      );
    }
  },
  rgbToHex: {
    configurable: true,
    enumerable: true,
    value: function(r, g, b) {
      /*
       * https://stackoverflow.com/questions/5623838/rgb-to-hex-and-hex-to-rgb
       * ex:
       * rgbToHex(255, 0, 0)
       */

      return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
    }
  },
  hexToRgb: {
    configurable: true,
    enumerable: true,
    value: function(hex) {
      /*
       * https://stackoverflow.com/questions/5623838/rgb-to-hex-and-hex-to-rgb
       * ex:
       * hexToRgb("#0033ff").g
       * hexToRgb("#03f").g
       */

      const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
      hex = hex.replace(shorthandRegex, function(m, r, g, b) {
        return r + r + g + g + b + b;
      });

      const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
      return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
      } : null;
    }
  },
  getLuminosity: {
    configurable: true,
    enumerable: true,
    value: function(hex) {
      /*
       * https://codepen.io/borlyjenkins/pen/dyPXjPr
       * https://github.com/Qix-/color
       * ex:
       * getLuminosity("#0033ff")
       * luminosity < 0.57 ? white-text : black-text    
       */

      const { r, g, b } = this.hexToRgb(hex);
      return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    }
  },
  classToTagName: {
    configurable: true,
    enumerable: true,
    value: function(str) {
      return str.replace(/([A-Z])/g,
        function(all, letter) {
          return '-' + letter.toLowerCase();
        }
      ).replace(/^-(.*)/, '$1');
    }
  },
  isObject: {
    configurable: true,
    enumerable: true,
    value: (value) => value && typeof value === 'object' && value.constructor === Object
  },
  isNumeric: {
    configurable: true,
    enumerable: true,
    value: (value) => !isNaN(value - parseFloat(value))
  },
  isAPISupport: {
    configurable: true,
    enumerable: true,
    value: function(apiName, element) {
      let node, flag;

      navigator.supports = navigator.supports || {};
      navigator.supports.api = navigator.supports.api || {};

      if (typeof navigator.supports.api[apiName] !== 'undefined') {
        flag = navigator.supports.api[apiName];
      } else {
        if (element) {
          node = (element.tagName) ? element.cloneNode(true) : element;
        } else {
          node = window;
        }

        flag = ['', 'webkit', 'moz', 'o', 'ms'].find(
          (key) => {
            let s;
            s = key + (key ? this.capitalize(apiName) : apiName);
            return node[s];
          }
        );

        flag = (flag !== undefined) ? true : false;

        //localStorage
        if (['localStorage', 'sessionStorage'].includes(apiName) && flag) {
          try {
            localStorage.setItem('isapisupport', 'dummy');
            localStorage.removeItem('isapisupport');
          } catch(err) {
            flag = false;
          }
        }//end if

        navigator.supports.api[apiName] = flag;
        node = null;
      }

      return flag;
    }
  },
  isEventSupport: {
    configurable: true,
    enumerable: true,
    value: function(eventName, element) {
      /**
       * reference:
       * http://perfectionkills.com/detecting-event-support-without-browser-sniffing/
       * @example
       * isEventSupport('touchstart');
       */

      let node, flag;

      navigator.supports = navigator.supports || {};
      navigator.supports.event = navigator.supports.event || {};

      if (typeof navigator.supports.event[eventName] !== 'undefined') {
        flag = navigator.supports.event[eventName];
      } else {
        if (element) {
          node = (element.tagName) ? element.cloneNode(true) : element;
        } else {
          node = document.createElement('div');
        }

        flag = `on${eventName}` in node;

        if (!flag && node.setAttribute) {
          node.setAttribute(eventName, 'return;');
          flag = typeof node[eventName] == 'function';
        }

        navigator.supports.event[eventName] = flag;
        node = null;
      }

      return flag;
    }
  },
  isCSSPropertySupport: {
    configurable: true,
    enumerable: true,
    value: function(property) {
      /**
       * @example
       * isCSSPropertySupport('overscroll-behavior');
       *
       * otherwise, maybe we can try CSS.supports
       * https://developer.mozilla.org/en-US/docs/Web/API/CSS/supports
       */

      let node, flag;

      navigator.supports = navigator.supports || {};
      navigator.supports.css = navigator.supports.css || {};

      property = (/^-ms/.test(property)) ? ('ms' + this.camelCase(property.replace(/-ms/,''))) : this.camelCase(property);

      if (typeof navigator.supports.css[property] !== 'undefined') {
        flag = navigator.supports.css[property];
      } else {
        node = document.createElement('div');
        flag = property in node.style;
        navigator.supports.css[property] = flag;
        node = null;
      }

      return flag;
    }
  },
  isStaticImportSupport:{
    configurable: true,
    enumerable: true,
    value: () => {
      /*
       * https://gist.github.com/ebidel/3201b36f59f26525eb606663f7b487d0
       */
      navigator.supports = navigator.supports || {};

      if (typeof navigator.supports.staticImport === 'undefined') {
        const script = document.createElement('script');
        navigator.supports.staticImport = 'noModule' in script;
      }

      return navigator.supports.staticImport;
    }
  },
  isDynamicImportSupport:{
    configurable: true,
    enumerable: true,
    value: () => {
      /*
       * https://gist.github.com/ebidel/3201b36f59f26525eb606663f7b487d0
       */

      navigator.supports = navigator.supports || {};

      if (typeof navigator.supports.dynamicImport === 'undefined') {
        try {
          new Function('import("")');
          navigator.supports.dynamicImport = true;
        } catch (err) {
          navigator.supports.dynamicImport = false;
        }
      }

      return navigator.supports.dynamicImport;
    }
  },
  isPrefersColorSchemeSet: {
    configurable: true,
    enumerable: true,
    get: () => {
      return Array.from(document.styleSheets).some(
        (styleSheet) => {
          try {
            return Array.from(styleSheet.rules).some(
              (rule) => {
                return rule.media && /prefers-color-scheme:.*dark/i.test(rule.conditionText);
              }
            );
          } catch(err) {
            return false;
          }
        }
      );
    }
  },
  isIOS: {
    configurable: true,
    enumerable: true,
    get: () => !!navigator.platform && /iPad|iPhone|iPod/.test(navigator.platform)
  },
  isMobile: {
    configurable: true,
    enumerable: true,
    value: () => navigator.userAgent.match(/(iPhone|iPod|iPad|Android|webOS|BlackBerry|IEMobile|Opera Mini)/i) || false
  },
  grabEventLock: {
    configurable: true,
    enumerable: true,
    value: function() {
      return navigator.eventLock || (function() {
        this.addStylesheetRules('.scroll-lock', {
          'overflow': 'hidden',
          'pointer-events': 'none'
        });

        navigator.eventLock = (evt) => {
          evt.preventDefault();
          evt.stopImmediatePropagation();
        };

        return navigator.eventLock;
      }).bind(this)();
    }
  },
  scrollLock: {
    configurable: true,
    enumerable: true,
    value: function(isLock = true, eventLock = this.grabEventLock()) {
      isLock = Boolean(isLock);
      if (isLock) {
        document.body.classList.add('scroll-lock');
        window.addEventListener('touchmove', eventLock, { passive: false });
      } else {
        document.body.classList.remove('scroll-lock');
        window.removeEventListener('touchmove', eventLock);
      }
    }
  },
  scrollX: {
    configurable: true,
    enumerable: true,
    get: () => {
      return _wcl.getScroll().x;
    },
    set: (x) => {
      if (document.documentElement && document.documentElement.scrollLeft) {
        document.documentElement.scrollLeft = x;
      } else {
        document.body.scrollLeft = x;
      }
    }
  },
  scrollY: {
    configurable: true,
    enumerable: true,
    get: () => {
      return _wcl.getScroll().y;
    },
    set: (y) => {
      if (document.documentElement && document.documentElement.scrollTop) {
        document.documentElement.scrollTop = y;
      } else {
        document.body.scrollTop = y;
      }
    }
  },
  rollTo: {
    configurable: true,
    enumerable: true,
    value: function(y, offset = 0) {
      /*
       * y could be y-coord or DOM element
       */
      const { height } = this.getPageSize();
      const { height:winHeight } = this.getViewportSize();

      if (typeof y.nodeType !== 'undefined' && y.nodeType === 1 && typeof y.getBoundingClientRect === 'function') {
        y = this.getPosition(y).y;
      }

      y += offset;

      return new Promise((resolve) => {
        let iid;
        const scroll = () => {
          let shift = Math.ceil((y - this.scrollY) * 0.15);
          
          cancelAnimationFrame(iid);
          scrollBy(0, shift);

          if (this.scrollY < 0 || this.scrollY + winHeight >= height || shift === 0 || Math.abs(this.scrollY - y) <= Math.abs(shift)) {
            this.scrollY = y;
            resolve();
            return;
          } else {
            iid = requestAnimationFrame(scroll);
          }
        };

        iid = requestAnimationFrame(scroll);
      });
    }
  },
  maxZIndex: {
    configurable: true,
    enumerable: true,
    get: () => {
      return Array.from(document.querySelectorAll('body *'))
            .map((a) => parseFloat(window.getComputedStyle(a).zIndex))
            .filter((a) => !isNaN(a))
            .sort((a,b) => a - b)
            .pop();
    }
  },
  pointer: {
    configurable: true,
    enumerable: true,
    value: function(e) {
      let x, y, docElement, body;
      
      docElement = document.documentElement;

      //x
      body = document.body || { scrollLeft: 0 };
      x = e.pageX || (e.clientX + (docElement.scrollLeft || body.scrollLeft) - (docElement.clientLeft || 0));

      //y
      body = document.body || { scrollTop: 0 };
      y = e.pageY || (e.clientY + (docElement.scrollTop || body.scrollTop) - (docElement.clientTop || 0));

      return { x, y };
    }
  },
  pursuer: {
    configurable: true,
    enumerable: true,
    value: function() {
      let down, move, up;

      if (this.isEventSupport('touchstart')) {
        down = 'touchstart';
        move = 'touchmove';
        up = 'touchend';
      } else if (typeof navigator.msPointerEnabled != 'undefined' && navigator.msPointerEnabled) {
        down = 'MSPointerDown';
        move = 'MSPointerMove';
        up = 'MSPointerUp';
      } else {
        down = 'mousedown';
        move = 'mousemove';
        up = 'mouseup';
      }

      return {down, move, up};
    }
  },
  purgeObject: {
    configurable: true,
    enumerable: true,
    value: function(targetObject) {
      if (this.isObject(targetObject)) {
        Object.keys(targetObject).forEach(
          (key) => {
            let { [key]:prop } = targetObject;

            if (Array.isArray(prop)) {
              while (prop.length) {
                prop.pop();
              }// end while
            }// end if

            targetObject[key] = null;
          }
        );
      }// end if
    }
  },
  isPiPSupport: {
    configurable: true,
    enumerable: true,
    value: function() {
      navigator.supports = navigator.supports || {};
      if (typeof navigator.supports.PiP === 'undefined') {
        const node = document.createElement('video');

        navigator.supports.PiP = this.isAPISupport('requestPictureInPicture', node) || (node.webkitSupportsPresentationMode && typeof node.webkitSetPresentationMode === 'function');
      }
      return navigator.supports.PiP;
    }
  },
  isFullscreenSupport: {
    configurable: true,
    enumerable: true,
    value: function() {
      navigator.supports = navigator.supports || {};
      if (!navigator.supports.fullscreen) {
        const node = document.createElement('div');
        let request = false;
        let exit = false;
        let element = '';
        let event = '';   

        if (node.requestFullscreen) {
          request = 'requestFullscreen';
          exit = 'exitFullscreen';
          element = 'fullscreenElement';
          event = 'fullscreenchange';
        // } else if (node.msRequestFullscreen) {
        //  request = 'msRequestFullscreen';
        //  exit = 'msExitFullscreen';
        } else if (node.webkitRequestFullscreen) {
          request = 'webkitRequestFullscreen';
          exit = 'webkitExitFullscreen';
          element = 'webkitFullscreenElement';
          event = 'webkitfullscreenchange';
        }

        navigator.supports.fullscreen = {
          request,
          exit,
          element,
          event
        };
      }
      return navigator.supports.fullscreen;
    }
  },
  supports: {
    configurable: true,
    enumerable: true,
    value: function() {
      // let flag;
      navigator.supports = navigator.supports || {};
      if (!navigator.supports.wp) {
        // flag = true;
        // try {
        //   class DummyClass {}
        // } catch (err) {
        //   flag = false;
        // }

        navigator.supports.wp = {
          // classes: flag,
          customElements: 'customElements' in window,
          import: 'import' in document.createElement('link'),
          shadowDOM: !!HTMLElement.prototype.attachShadow,
          template: 'content' in document.createElement('template')
        };
      }
      return navigator.supports.wp;
    }
  },
  getScroll: {
    configurable: true,
    enumerable: true,
    value: () => {
      let x, y;
      x = (self.pageXOffset) ? self.pageXOffset : (document.documentElement && document.documentElement.scrollLeft) ? document.documentElement.scrollLeft : document.body.scrollLeft;
      y = (self.pageYOffset) ? self.pageYOffset : (document.documentElement && document.documentElement.scrollTop) ? document.documentElement.scrollTop : document.body.scrollTop;
      return { x, y };
    }
  },
  getPosition: {
    configurable: true,
    enumerable: true,
    value: (element) => {
      let x, y;
      x = 0;
      y = 0;
      while (element != null) {
        x += element.offsetLeft;
        y += element.offsetTop;
        element = element.offsetParent;
      }
      return { x, y };
    }
  },
  getPageSize: {
    configurable: true,
    enumerable: true,
    value: function() {
      let xScroll, yScroll, width, height;
      const { width:winWidth, height:winHeight } = this.getViewportSize();

      if (window.innerHeight && window.scrollMaxY) {
        xScroll = document.body.scrollWidth;
        yScroll = window.innerHeight + window.scrollMaxY;
      } else if (document.body.scrollHeight > document.body.offsetHeight) {
        xScroll = document.body.scrollWidth;
        yScroll = document.body.scrollHeight;
      } else {
        xScroll = document.body.offsetWidth;
        yScroll = document.body.offsetHeight;
      }

      width = (xScroll < winWidth) ? winWidth : xScroll;
      height = (yScroll < winHeight) ? winHeight : yScroll;

      return { width, height };
    }
  },
  getViewportSize: {
    configurable: true,
    enumerable: true,
    value: () => {
      let width, height;

      if (self.innerHeight) {
        width = self.innerWidth;
        height = self.innerHeight;
      } else if (document.documentElement && document.documentElement.clientHeight) {
        width = document.documentElement.clientWidth;
        height = document.documentElement.clientHeight;
      } else if (document.body) {
        width = document.body.clientWidth;
        height = document.body.clientHeight;
      }

      return { width, height };
    }
  },
  getSize: {
    configurable: true,
    enumerable: true,
    value: (element) => {
      let width, height;

      width = element.offsetWidth;
      height = element.offsetHeight;
      return { width, height };
    }
  },
  getRand: {
    configurable: true,
    enumerable: true,
    value: (a, b) => {
      let min, max;
      
      if (a > b) {
        min = a;
        max = b;
      } else {
        min = b;
        max = a;
      }

      return Math.floor(Math.random() * (max - min + 1) + min);
    }
  },
  grabStyleSheet: {
    configurable: true,
    enumerable: true,
    value: function() {
      return navigator.customStyleSheet || (function() {
        navigator.customStyleSheet = document.createElement('style');
        document.head.appendChild(navigator.customStyleSheet);
        return navigator.customStyleSheet;
      })();
    }
  },
  removeAllChildNodes: {
    configurable: true,
    enumerable: true,
    value: function(parent) {
      while (parent.firstChild) {
          parent.removeChild(parent.firstChild);
      }
    }
  },
  addStylesheetRules: {
    configurable: true,
    enumerable: true,
    value: function(selector = '', props = {}, styleSheet = this.grabStyleSheet()) {
      /**
       * Add a stylesheet rule to the document
       * @example
       addStylesheetRules(
        'body',
        {
          background: '#f00',
          color: '#0f0'
        }
        [, styleSheet]
       )

       addStylesheetRules(
        '@keyframes fancy-anchor-ripple',
        {
          '0%': '{transform:scale(1);opacity:1;}',
          '100%': '{transform:scale(100);opacity:0;}'
        }
        [, styleSheet]
       )
       */
      if (!selector || !Object.keys(props).length || !styleSheet.sheet) return;

      let propStr, findIndex, sign;

      sign = (/keyframes/i.test(selector)) ? '' : ';';
      styleSheet = styleSheet.sheet;
      propStr = Object.keys(props).reduce(
        (acc, cur) => {
          let sign;

          sign = /^\{.*\}$/.test(props[cur]) ? '' : ':';
          return acc.concat([`${cur}${sign}${props[cur]}`]);
        }
      , []).join(sign);
      findIndex = Array.from(styleSheet.cssRules).findIndex((rule) => rule.selectorText == selector);

      if (findIndex !== -1) {
        try {
          styleSheet.cssRules[findIndex].style.cssText = propStr;
        } catch(err) { /*error*/ }
      } else {
        try {
          styleSheet.insertRule(`${selector}{${propStr}}`, styleSheet.cssRules.length);
        } catch(err) { /*error*/ }
      }
    }
  }
});