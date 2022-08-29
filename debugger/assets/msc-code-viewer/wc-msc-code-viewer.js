import { _wcl } from './common-lib.js';
import { _wccss } from './common-css.js';
import hljs from './highlight.js';

const defaults = {};
const custumEvents = {
  mutate: 'msc-code-viewer-mutate'
};

const template = document.createElement('template');
template.innerHTML = `
<style>
${_wccss}

/*!
  Theme: GitHub Dark
  Description: Light / Dark theme as seen on github.com
  Author: github.com
  Maintainer: @Hirse
  Updated: 2021-05-15

  Outdated base version:
    - https://github.com/primer/github-syntax-light
    - https://github.com/primer/github-syntax-dark
  Current colors taken from GitHub's CSS
*/

.hljs {
  --text-color: #24292e;
  --keyword: #d73a49;
  --title: #6f42c1;
  --attr: #005cc5;
  --regexp: #032f62;
  --symbol: #e36209;
  --comment: #6a737d;
  --name: #22863a;
  --subst: #24292e;
  --section: #005cc5;
  --bullet: #735c0f;
  --emphasis: #24292e;
  --strong: #24292e;
  --addition: #22863a;
  --addition-bg: #f0fff4;
  --deletion: #b31d28;
  --deletion-bg: #ffeef0;
}

.hljs {
  color: var(--text-color);
  background: transparent;
}

.hljs-doctag,
.hljs-keyword,
.hljs-meta .hljs-keyword,
.hljs-template-tag,
.hljs-template-variable,
.hljs-type,
.hljs-variable.language_ {
  /* prettylights-syntax-keyword */
  color: var(--keyword);
}

.hljs-title,
.hljs-title.class_,
.hljs-title.class_.inherited__,
.hljs-title.function_ {
  /* prettylights-syntax-entity */
  color: var(--title);
}

.hljs-attr,
.hljs-attribute,
.hljs-literal,
.hljs-meta,
.hljs-number,
.hljs-operator,
.hljs-variable,
.hljs-selector-attr,
.hljs-selector-class,
.hljs-selector-id {
  /* prettylights-syntax-constant */
  color: var(--attr);
}

.hljs-regexp,
.hljs-string,
.hljs-meta .hljs-string {
  /* prettylights-syntax-string */
  color: var(--regexp);
}

.hljs-built_in,
.hljs-symbol {
  /* prettylights-syntax-variable */
  color: var(--symbol);
}

.hljs-comment,
.hljs-code,
.hljs-formula {
  /* prettylights-syntax-comment */
  color: var(--comment);
}

.hljs-name,
.hljs-quote,
.hljs-selector-tag,
.hljs-selector-pseudo {
  /* prettylights-syntax-entity-tag */
  color: var(--name);
}

.hljs-subst {
  /* prettylights-syntax-storage-modifier-import */
  color: var(--subst);
}

.hljs-section {
  /* prettylights-syntax-markup-heading */
  color: var(--section);
  font-weight: bold;
}

.hljs-bullet {
  /* prettylights-syntax-markup-list */
  color: var(--bullet);
}

.hljs-emphasis {
  /* prettylights-syntax-markup-italic */
  color: var(--emphasis);
  font-style: italic;
}

.hljs-strong {
  /* prettylights-syntax-markup-bold */
  color: var(--strong);
  font-weight: bold;
}

.hljs-addition {
  /* prettylights-syntax-markup-inserted */
  color: var(--addition);
  background-color: var(--addition-bg);
}

.hljs-deletion {
  /* prettylights-syntax-markup-deleted */
  color: var(--deletion);
  background-color: var(--deletion-bg);
}

.hljs-char.escape_,
.hljs-link,
.hljs-params,
.hljs-property,
.hljs-punctuation,
.hljs-tag {
  /* purposely ignored */
}

:host {
  --msc-code-viewer-border-radius: 16px;
  --bgcolor: #f6f8fa;
}

.cv__copy {
  --clip-path: path('M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z');
  --color-light: #5a616a;
  --color-dark: #8c939c;
  --color: var(--color-light);

  --opacity-start: 1;
  --opacity-hover: 1;
  --opacity: var(--opacity-start);

  --bgcolor-hover: #f3f4f6;
  --bgcolor-start: var(--bgcolor-hover);
  --bgcolor: var(--bgcolor-start);
}

:host{position:relative;background:var(--bgcolor);border-radius:var(--msc-code-viewer-border-radius);padding:1.5em 1em;display:block;}
.main{inline-size:100%;overflow-x:auto;overflow-y:hidden;mask-image:linear-gradient(to right, black calc(100% - 10px), transparent 100%);-webkit-mask-image:linear-gradient(to right, black calc(100% - 10px), transparent 100%);-webkit-overflow-scrolling:touch;}
.cv__pre{line-height:1.45;}
.cv__copy{position:absolute;inset-inline-end:.5em;inset-block-start:.5em;inline-size:3em;block-size:3em;display:block;transition:opacity 150ms ease,background-color 150ms ease;opacity:var(--opacity);border-radius:3em;background-color:var(--bgcolor);}
.cv__copy::before{position:absolute;content:'';inline-size:24px;block-size:24px;inset-inline-start:0;inset-inline-end:0;inset-block-start:0;inset-block-end:0;display:block;margin:auto;background:var(--color);clip-path:var(--clip-path);}
.cv__copy:focus{outline:0 none;--opacity:var(--opacity-hover);--bgcolor:var(--bgcolor-hover);}
.cv__copy:active{transform:scale(.95);}

@media (hover: hover) {
  .cv__copy{--opacity-start:0;--bgcolor-start:rgba(0,0,0,0);}
  .cv__copy:hover{--bgcolor:var(--bgcolor-hover);}
  :host(:hover) .cv__copy,.cv__copy:hover{--opacity:var(--opacity-hover);}
}

/*
@media (prefers-color-scheme: dark) {
  :host {
    --bgcolor: #171b21;
  }

  .hljs {
    --text-color: #c9d1d9;
    --keyword: #ff7b72;
    --title: #d2a8ff;
    --attr: #79c0ff;
    --regexp: #a5d6ff;
    --symbol: #ffa657;
    --comment: #8b949e;
    --name: #7ee787;
    --subst: #c9d1d9;
    --section: #1f6feb;
    --bullet: #f2cc60;
    --emphasis: #c9d1d9;
    --strong: #c9d1d9;
    --addition: #aff5b4;
    --addition-bg: #033a16;
    --deletion: #ffdcd7;
    --deletion-bg: #67060c;
  }

  .cv__copy {
    --color: var(--color-dark);
    --bgcolor-hover: #22262c;
    --bgcolor-start: var(--bgcolor-hover);
  }

  @media (hover: hover) {
    .cv__copy{--bgcolor-start:rgba(0,0,0,0);}
  }
}
 */
</style>

<div class="main">
  <pre class="cv__pre"><code class="cv__code"></code></pre>
</div>
<a href="#copy" class="cv__copy stuff" title="COPY" aria-label="COPY">COPY</a>
`;

// Houdini Props and Vals
if (CSS?.registerProperty) {
  CSS.registerProperty({
    name: '--msc-code-viewer-border-radius',
    syntax: '<length>',
    inherits: true,
    initialValue: '16px'
  });
}

export class MscCodeViewer extends HTMLElement {
  #data;
  #nodes;
  #config;

  constructor(config) {
    super();

    // template
    this.attachShadow({ mode: 'open' });
    this.shadowRoot.appendChild(template.content.cloneNode(true));

    // data
    this.#data = {
      controller: '',
      observer: ''
    };

    // nodes
    this.#nodes = {
      styleSheet: this.shadowRoot.querySelector('style'),
      pre: this.shadowRoot.querySelector('.cv__pre'),
      code: this.shadowRoot.querySelector('.cv__code'),
      copy: this.shadowRoot.querySelector('.cv__copy')
    };

    // config
    this.#config = {
      ...defaults,
      ...config // new MscCodeViewer(config)
    };

    // evt
    this._onClick = this._onClick.bind(this);
    this._onTouch = this._onTouch.bind(this);
  }

  async connectedCallback() {
    const { copy } = this.#nodes;
    const { config, error } = await _wcl.getWCConfig(this);

    if (error) {
      console.warn(`${_wcl.classToTagName(this.constructor.name)}: ${error}`);
      this.remove();
      return;
    } else {
      this.#config = {
        ...this.#config,
        ...config
      };
    }

    // upgradeProperty
    Object.keys(defaults).forEach((key) => this._upgradeProperty(key));

    // MutationObserver
    this.#data.observer = new MutationObserver(
      (mutations) => {
        mutations.forEach(
          () => {
            this._mutate();
          }
        );
      }
    );

    this.#data.observer.observe(this, {
      subtree: true,
      childList: true,
      characterData: true
    });

    // evts
    this.#data.controller = new AbortController();
    const signal = this.#data.controller.signal;
    copy.addEventListener('click', this._onClick, { signal });
    if (_wcl.isEventSupport('touchstart')) {
      /*
       * CSS :active can only active when event: touchstart add
       * https://developer.apple.com/library/archive/documentation/AppleApplications/Reference/SafariWebContent/AdjustingtheTextSize/AdjustingtheTextSize.html
       */
      copy.addEventListener('touchstart', this._onTouch, { signal });
    }

    this._mutate();
  }

  disconnectedCallback() {
    if (this.#data?.controller) {
      this.#data.controller.abort();
    }

    if (this.#data?.observer) {
      this.#data.observer.disconnect();
    }
  }

  _format(attrName, oldValue, newValue) {
    const hasValue = newValue !== null;

    this.#config[attrName] = (!hasValue) ? defaults[attrName] : newValue;
  }

  attributeChangedCallback(attrName, oldValue, newValue) {
    if (!MscCodeViewer.observedAttributes.includes(attrName)) {
      return;
    }

    this._format(attrName, oldValue, newValue);
  }

  static get observedAttributes() {
    return Object.keys(defaults); // MscCodeViewer.observedAttributes
  }

  _upgradeProperty(prop) {
    let value;

    if (MscCodeViewer.observedAttributes.includes(prop)) {
      if (Object.prototype.hasOwnProperty.call(this, prop)) {
        value = this[prop];
        delete this[prop];
      } else {
        if (this.hasAttribute(prop)) {
          value = this.getAttribute(prop);
        } else {
          value = this.#config[prop];
        }
      }

      this[prop] = value;
    }
  }

  _fireEvent(evtName, detail) {
    this.dispatchEvent(new CustomEvent(evtName,
      {
        bubbles: true,
        composed: true,
        ...(detail && { detail })
      }
    ));
  }

  set value(textContent) {
    this.textContent = textContent.trim();
  }

  get value() {
    return this.#nodes.code.textContent;
  }

  async _onClick(evt) {
    evt.preventDefault();

    try {
      await navigator.clipboard.writeText(this.value);
    } catch (err) {
      console.error('Failed to copy: ', err);
    }
  }

  _onTouch() {
    // do nothing
  }

  _mutate() {
    this.#nodes.code.className = 'cv__code';
    this.#nodes.code.textContent = this.textContent.trim();
    hljs.highlightElement(this.#nodes.code);

    this._fireEvent(custumEvents.mutate, {
      value: this.value
    });
  }
}

// define web component
const S = _wcl.supports();
const T = _wcl.classToTagName('MscCodeViewer');
if (S.customElements && S.shadowDOM && S.template && !window.customElements.get(T)) {
  window.customElements.define(_wcl.classToTagName('MscCodeViewer'), MscCodeViewer);
}
