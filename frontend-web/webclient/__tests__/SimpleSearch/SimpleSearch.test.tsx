import { configure } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import { createBrowserHistory } from "history";
import "jest-styled-components";
import * as React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";
import { create } from "react-test-renderer";
import { ThemeProvider } from "styled-components";
import Search from "../../app/Search/Search";
import theme from "../../app/ui-components/theme";
import { store } from "../../app/Utilities/ReduxUtilities";

configure({ adapter: new Adapter() });

test("Search mount", () => {
  expect(
    create(
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <Search
              match={{
                isExact: false,
                params: { priority: "FILES" },
                url: "",
                path: ""
              }}
              history={createBrowserHistory()}
              location={{ search: "" }}
            />
          </MemoryRouter>
        </ThemeProvider>
      </Provider>
    )
  ).toMatchInlineSnapshot(`
    .c4 {
      display: -webkit-box;
      display: -webkit-flex;
      display: -ms-flexbox;
      display: flex;
    }

    .c0 {
      margin-left: 190px;
      padding-top: 48px;
      padding-bottom: 14px;
      padding-left: 14px;
      padding-right: 0;
      background-color: #fff;
    }

    .c5 {
      margin-left: auto;
    }

    .c10 {
      padding-top: 96px;
      padding-right: 14px;
    }

    .c9 {
      -webkit-flex: none;
      -ms-flex: none;
      flex: none;
      vertical-align: middle;
      cursor: inherit;
      margin-left: .7em;
    }

    .c7 {
      cursor: pointer;
    }

    .c8 {
      cursor: inherit;
    }

    .c6 {
      position: relative;
      display: inline-block;
    }

    .c6:hover > div {
      display: block;
    }

    .c2 {
      display: -webkit-box;
      display: -webkit-flex;
      display: -ms-flexbox;
      display: flex;
      border-bottom: 2px solid #c9d3df;
      cursor: pointer;
    }

    .c3 {
      cursor: pointer;
      font-size: 20px;
      margin-right: 1em;
    }

    .c1 {
      padding-top: 14px;
      padding-bottom: 14px;
      padding-left: 204px;
      padding-right: 14px;
      width: 100%;
      height: 96px;
      background-color: #fff;
      position: absolute;
      top: 48px;
      left: 0;
      position: fixed;
      z-index: 50;
    }

    @media screen and (min-width:768px) and (max-width:1023px) {
      .c11 {
        display: none;
      }
    }

    @media screen and (min-width:1024px) and (max-width:1279px) {
      .c11 {
        display: none;
      }
    }

    @media screen and (min-width:1280px) {
      .c11 {
        display: none;
      }
    }

    <div
      className="c0"
    >
      <div
        className="c1"
        height={96}
        width={1}
      >
        <div
          className="c2"
        >
          <div
            className="c3"
            cursor="pointer"
            fontSize={3}
            onClick={[Function]}
            selected={false}
          >
            Files
          </div>
          <div
            className="c3"
            cursor="pointer"
            fontSize={3}
            onClick={[Function]}
            selected={false}
          >
            Applications
          </div>
        </div>
        <div
          className="c4"
        >
          <div
            className="c5"
          />
          <div
            className="c6"
            data-tag="dropdown"
          >
            <span
              className="c7"
              cursor="pointer"
              onClick={[Function]}
            >
              <span
                className="c8"
                cursor="inherit"
              >
                 
                Files per page 25
              </span>
              <svg
                className="c9"
                clipRule="evenodd"
                cursor="inherit"
                fill="currentcolor"
                fillRule="evenodd"
                height=".7em"
                ml=".7em"
                viewBox="0 0 24 12"
                width=".7em"
              >
                <path
                  d="M0 0l11.959 12 12-12H0z"
                />
              </svg>
            </span>
          </div>
        </div>
      </div>
      <div
        className="c10"
      >
        <div
          className="c11"
        />
      </div>
    </div>
  `);
});
