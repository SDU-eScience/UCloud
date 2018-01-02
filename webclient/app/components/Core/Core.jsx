import React from 'react';
import ReactCSSTransitionGroup from 'react-addons-css-transition-group';

import './Core.scss';
import './LayoutVariants.scss';

import Header from '../Header/Header';
import HeaderSearch from '../Header/HeaderSearch';
import Sidebar from '../Sidebar/Sidebar';
import Settings from '../Settings/Settings';

class Core extends React.Component {
    render() {

        const animationName = 'rag-fadeIn'

        return (
            <div className="layout-container">

                <Header />

                <Sidebar />
                <div className="sidebar-layout-obfuscator"></div>

                <ReactCSSTransitionGroup
                  component="main"
                  className="main-container"
                  transitionName={animationName}
                  transitionEnterTimeout={500}
                  transitionLeaveTimeout={500}
                >
                    {/* Page content */}
                    {React.cloneElement(this.props.children, {
                        key: this.props.location.pathname
                    })}

                    {/* Page footer */}
                    <footer>
                        <span>2017 - Centric app.</span>
                    </footer>
                </ReactCSSTransitionGroup>

                {/* Search template */}
                <HeaderSearch/>

                {/* Settings template */}
                <Settings/>

                {/*> yield region="bodyChild" }*/}

            </div>
        );
    }
}

export default Core;
