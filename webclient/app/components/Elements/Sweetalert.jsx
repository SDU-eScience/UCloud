import React from 'react';
import pubsub from 'pubsub-js';

import SweetalertRun from './Sweetalert.run';

class Sweetalert extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        SweetalertRun();
    }

    render() {
        return (
            <section>
                <div className="container-md">
                    <h4 className="page-header mt0">Usage examples</h4>
                    <div className="list-group">
                        <div className="list-group-item">
                            <div className="pull-right">
                                <p><a id="swal-demo1" href="#" className="btn btn-primary">Try me!</a></p>
                            </div>
                            <p>A basic message</p>
                        </div>
                    </div>
                    <div className="list-group">
                        <div className="list-group-item">
                            <div className="pull-right">
                                <p><a id="swal-demo2" href="#" className="btn btn-primary">Try me!</a></p>
                            </div>
                            <p>A title with a text under</p>
                        </div>
                    </div>
                    <div className="list-group">
                        <div className="list-group-item">
                            <div className="pull-right">
                                <p><a id="swal-demo3" href="#" className="btn btn-primary">Try me!</a></p>
                            </div>
                            <p>A success message!</p>
                        </div>
                    </div>
                    <div className="list-group">
                        <div className="list-group-item">
                            <div className="pull-right">
                                <p><a id="swal-demo4" href="#" className="btn btn-primary">Try me!</a></p>
                            </div>
                            <p>A warning message, with a function attached to the &quot;Confirm&quot;-button</p>
                        </div>
                    </div>
                    <div className="list-group">
                        <div className="list-group-item">
                            <div className="pull-right">
                                <p><a id="swal-demo5" href="#" className="btn btn-primary">Try me!</a></p>
                            </div>
                            <p>... and by passing a parameter, you can execute something else for &quot;Cancel&quot;.</p>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Sweetalert;
