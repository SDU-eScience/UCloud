import React from 'react';
import pubsub from 'pubsub-js';

class Spinners extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {

        // Require here makes possible to run the js when the
        // markup is ready. Otherwise standard jQuery.ready won't work

        // Loaders.CSS
        require('../../../../node_modules/loaders.css/loaders.css.js');

    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h5 className="mt0">Loaders.css</h5>
                    <p className="mb-lg">Delightful and performance-focused pure css loading animations.</p>
                    <div className="row loader-primary">
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-pulse</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-pulse"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-grid-pulse</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-grid-pulse"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-clip-rotate</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-clip-rotate"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-clip-rotate-pulse</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-clip-rotate-pulse"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">square-spin</div>
                                <div className="loader-demo">
                                    <div className="loader-inner square-spin"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-clip-rotate-multiple</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-clip-rotate-multiple"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-pulse-rise</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-pulse-rise"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-rotate</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-rotate"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">cube-transition</div>
                                <div className="loader-demo">
                                    <div className="loader-inner cube-transition"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-zig-zag</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-zig-zag"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-zig-zag-deflect</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-zig-zag-deflect"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-triangle-path</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-triangle-path"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-scale</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-scale"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">line-scale</div>
                                <div className="loader-demo">
                                    <div className="loader-inner line-scale"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">line-scale-party</div>
                                <div className="loader-demo">
                                    <div className="loader-inner line-scale-party"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-scale-multiple</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-scale-multiple"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-pulse-sync</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-pulse-sync"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-beat</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-beat"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">line-scale-pulse-out</div>
                                <div className="loader-demo">
                                    <div className="loader-inner line-scale-pulse-out"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">line-scale-pulse-out-rapid</div>
                                <div className="loader-demo">
                                    <div className="loader-inner line-scale-pulse-out-rapid"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-scale-ripple</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-scale-ripple"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-scale-ripple-multiple</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-scale-ripple-multiple"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-spin-fade-loader</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-spin-fade-loader"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">line-spin-fade-loader</div>
                                <div className="loader-demo">
                                    <div className="loader-inner line-spin-fade-loader"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">triangle-skew-spin</div>
                                <div className="loader-demo">
                                    <div className="loader-inner triangle-skew-spin"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">pacman</div>
                                <div className="loader-demo">
                                    <div className="loader-inner pacman"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">ball-grid-beat</div>
                                <div className="loader-demo">
                                    <div className="loader-inner ball-grid-beat"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 col-xs-12">
                            <div className="card">
                                <div className="card-heading">semi-circle-spin</div>
                                <div className="loader-demo">
                                    <div className="loader-inner semi-circle-spin"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Spinners;
