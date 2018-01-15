import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Search extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-overlap bg-indigo-500">
                    <div className="container container-md pr-xl">
                        <form action="">
                            <div className="mda-form-group mda-form-group-lg float-label">
                                <div className="mda-form-control mda-form-control-dark">
                                    <input type="text" className="no-resize form-control"/>
                                    <div className="mda-form-control-line"></div>
                                    <label>Search</label>
                                </div>
                                <span className="mda-form-msg right"><span>1500</span> results (<span>0.15</span> seconds)</span>
                            </div>
                        </form>
                    </div>
                </div>
                <Grid className="container-md">
                    <div className="card">
                        <div className="card-body">
                            <p className="lead">Search results for &quot;material template&quot;</p>
                            <ul className="list-group">
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">Material Marketing Template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Donec nec euismod nulla. Sed vel elementum ante. Mauris bibendum lectus ut leo pharetra imperdiet. Nullam auctor, dolor at facilisis tristique interdum at fermen</p>
                                </li>
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">Material Web Template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Sed id lacus enim, sit amet imperdiet orci. Integer in convallis felis. Suspendisse nisl nulla, interdum at fermentum eget, adipiscing in elit, dui nunc ornare nibh</p>
                                </li>
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">How to create Material cards template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Phasellus sit amet sem nibh, sed aliquam est. In pretium mauris id ipsum mollis varius. Nullam pretium fermentum orci id lacinia, dui nunc ornare nibh</p>
                                </li>
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">Material design lite template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Integer cursus lectus vel eros placerat vitae faucibus leo dignissim. Phasellus sit amet sem nibh, sed aliquam est. Proin suscipit porta diam id mollis.</p>
                                </li>
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">HTML5 material template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Vestibulum eget nunc tellus, eu elementum velit. Praesent malesuada erat ut tellus egestas sagittis. Ut rutrum fermentum accumsan. Sed volutpat leo eu urn</p>
                                </li>
                                <li className="list-group-item">
                                    <h5 className="mb-sm"><a href="#">One page material template</a></h5><a href="#" className="text-soft">http://example.com/search-result-link</a>
                                    <p>Ut non tortor nec eros gravida tincidunt at vitae dui. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.</p>
                                </li>
                            </ul>
                            <nav className="text-center">
                                <ul className="pagination pagination-rounded">
                                    <li className="disabled"><a href="#" aria-label="Previous"><span aria-hidden="true">&laquo;</span></a></li>
                                    <li className="active"><a href="#">1</a></li>
                                    <li><a href="#">2</a></li>
                                    <li><a href="#">3</a></li>
                                    <li><span>...</span></li>
                                    <li><a href="#">14</a></li>
                                    <li><a href="#">15</a></li>
                                    <li><a href="#" aria-label="Next"><span aria-hidden="true">&raquo;</span></a></li>
                                </ul>
                            </nav>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default Search;
