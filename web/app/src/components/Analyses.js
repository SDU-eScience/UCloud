import $ from 'jquery';
import React from 'react';
import LoadingIcon from './LoadingIconComponent'
import {WebSocketSupport} from './UtilityFunctions'
import Modal from 'react-modal'

class AnalysesComponent extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      analyses: [],
      loading: false,
      currentAnalysis: null,
      comment: "",
    }
  }

  componentDidMount() {
    this.getAnalyses();
    Modal.setAppElement('#app');
  }

  getAnalyses() {
    this.setState({
      loading: true
    });
    $.getJSON("/api/getAnalyses").then(analyses => {
      analyses.forEach(it => {
        it.jobId = Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000)
      });
      analyses.sort((a, b) => {
        return a.name.localeCompare(b.name);
      });
      this.setState({
        loading: false,
        analyses: analyses,
      });
    });
  }

  handleUpdate(event) {
    this.setState({comment: event.target.value})
  }

  postComment() {
    console.log("Trying to post");
  }

  showModal(analysis) {
    this.setState({currentAnalysis: analysis, shown: true})
  }

  hideModal() {
    this.setState({shown: false})
  }

  render() {
    const noAnalysis = this.state.analyses.length ? '' : <h3 className="text-center">
      <small>No analyses found.</small>
    </h3>;

    return (
      <section>
        <div className="container-fluid">
          <div className="col-lg-10">
            <LoadingIcon loading={this.state.loading}/>
            <div className="card">
              <div className="card-body">
                <WebSocketSupport/>
                {noAnalysis}
                <table id="table-options" className="table-datatable table table-hover mv-lg">
                  <thead>
                  <tr>
                    <th>Workflow Name</th>
                    <th>Job Id</th>
                    <th>Status</th>
                    <th>Comments</th>
                  </tr>
                  </thead>
                  <Analyses onClick={(analysis) => this.showModal(analysis)} analyses={this.state.analyses}/>
                </table>
              </div>
            </div>
          </div>
        </div>
        <CommentsModal shown={this.state.shown} comment={this.state.comment} onChange={() => this.handleUpdate}
                       analysis={this.state.currentAnalysis} onClick={() => this.postComment()}
                       hide={() => this.hideModal()}/>
      </section>
    )
  }
}

function Analyses(props) {
  const analyses = props.analyses.slice();
  let i = 0;
  const analysesList = analyses.map(analysis =>
    <tr key={i++} className="gradeA row-settings">
      <td>{analysis.name}</td>
      <td>{analysis.jobId}</td>
      <td>{analysis.status}</td>
      <td>
        <AnalysesButton analysis={analysis} comments={analysis.comments} onClick={() => props.onClick(analysis)}/>
      </td>
    </tr>
  );

  return (
    <tbody>
    {analysesList}
    </tbody>)
}

function AnalysesButton(props) {
  let analysis = props.analysis;
  if (props.comments.length) {
    return (
      <button className="btn btn-primary" onClick={() => props.onClick(analysis)}>
        Show {props.comments.length} comments
      </button>
    )
  } else {
    return (<button className="btn btn-secondary" onClick={() => props.onClick(analysis)}>Write comment</button>)
  }
}

function CommentsModal(props) {
  if (!props.analysis) return null;
  let comment = props.comment;
  const comments = props.analysis.comments.slice();
  let i = 0;
  const commentsList = comments.map(comment =>
    <div key={i++}>
      <b>{comment.author} @</b> <i>{new Date(comment.timestamp).toLocaleString() + ':'}</i>
      <br/><span>{comment.content}</span>
    </div>
  );

  return (
      <Modal isOpen={props.shown} className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <button type="button" className="close" onClick={() => {
              props.hide()
            }}>&times;</button>
            <h4 className="modal-title"> {props.analysis.name}<br/>
              <small>Comments</small>
            </h4>
          </div>
          <div className="modal-body">
            {commentsList}
            <hr/>
            <div>You: <input value={this.state.comment} onChange={props.onChange} type="text"/>
              <button onClick={props.onClick} className="btn btn-primary text-right">Send</button>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-default" onClick={() => {
              props.hide()
            }}>Close
            </button>
          </div>
        </div>
      </Modal>
  )
}

export {AnalysesComponent}

/*
<Modal
  isOpen={bool}
  onAfterOpen={afterOpenFn}
  onRequestClose={requestCloseFn}
  closeTimeoutMS={n}
  style={customStyle}
  contentLabel="Modal">
  <h1>Modal Content</h1>
  <p>Etc.</p>
</Modal>
 */
