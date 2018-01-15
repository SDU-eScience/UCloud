import React from 'react';
import pubsub from 'pubsub-js';

class Whiteframes extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <div className="card">
                        <div className="card-heading">
                            <div className="card-title">Whiteframes</div>
                        </div>
                        <div className="card-body">
                            <div className="card shadow-z1">
                                <div className="card-body">
                                    <p className="mv-lg text-center">.shadow-z1</p>
                                </div>
                            </div>
                            <div className="card shadow-z2">
                                <div className="card-body">
                                    <p className="mv-lg text-center">.shadow-z2</p>
                                </div>
                            </div>
                            <div className="card shadow-z3">
                                <div className="card-body">
                                    <p className="mv-lg text-center">.shadow-z3</p>
                                </div>
                            </div>
                            <div className="card shadow-z4">
                                <div className="card-body">
                                    <p className="mv-lg text-center">.shadow-z4</p>
                                </div>
                            </div>
                            <div className="card shadow-z5">
                                <div className="card-body">
                                    <p className="mv-lg text-center">.shadow-z5</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Whiteframes;


