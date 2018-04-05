import React from "react";
import { connect } from "react-redux";
import { updatePageTitle } from "../../Actions/Status";

const Status = ({ status, dispatch }) => {
    dispatch(updatePageTitle("System Status"));
    return (
        <section>
            <div className={"container-overlap " + statusToButton(status)}>
                <div className="media m0 pv">
                    <div className="media-left"><a href="#"/></div>
                    <div className="media-body media-middle">
                        <h4 className="media-heading">{status.title}</h4>
                    </div>
                </div>
            </div>
            <div className="container-fluid">
                <div className="row">
                    <div className="col">
                        <form className="card">
                            <div className="card-body">
                                <h3 className="text-inherit editable editable-pre-wrapped editable-click editable-disabled">{status.body}</h3>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </section>);
};

const statusToButton = (status) => {
    switch (status.level) {
        case "NO ISSUES":
            return 'bg-green-500';
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return 'bg-yellow-500';
        case "ERROR":
            return 'bg-red-500';
    }
}

const mapStateToProps = (state) => ({ status: state.status.status })
export default connect(mapStateToProps)(Status);