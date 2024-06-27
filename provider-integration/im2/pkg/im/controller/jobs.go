package controller

import (
	"fmt"
	"net/http"
	"strings"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Jobs JobsService

type JobsService struct {
	Submit           func(request JobSubmitRequest) error
	Terminate        func(request JobTerminateRequest) error
	Extend           func(request JobExtendRequest) error
	RetrieveProducts func() []orc.JobSupport
}

type JobTerminateRequest struct {
	Job *orc.Job
}

type JobSubmitRequest struct {
	JobToSubmit *orc.Job
}

type JobExtendRequest struct {
	Job           *orc.Job
	RequestedTime orc.SimpleDuration
}

func controllerJobs(mux *http.ServeMux) {
	jobContext := fmt.Sprintf("/ucloud/%v/jobs/", cfg.Provider.Id)

	creationUrl, _ := strings.CutSuffix(jobContext, "/")
	mux.HandleFunc(creationUrl, HttpUpdateHandler[fnd.BulkRequest[*orc.Job]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Job]) {
			var errors []error

			for _, item := range request.Items {
				err := Jobs.Submit(JobSubmitRequest{
					JobToSubmit: item,
				})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[*fnd.FindByStringId]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, nil)
				}

				sendResponseOrError(w, response, nil)
			}
		}),
	)

	mux.HandleFunc(jobContext+"terminate", HttpUpdateHandler[fnd.BulkRequest[*orc.Job]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[*orc.Job]) {
			var errors []error

			for _, item := range request.Items {
				err := Jobs.Terminate(JobTerminateRequest{
					Job: item,
				})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				sendResponseOrError(w, response, nil)
			}
		}),
	)

	type extendRequest struct {
		Job           *orc.Job           `json:"jobId"`
		RequestedTime orc.SimpleDuration `json:"requestedTime"`
	}

	mux.HandleFunc(jobContext+"extend", HttpUpdateHandler[fnd.BulkRequest[extendRequest]](
		0,
		func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[extendRequest]) {
			var errors []error

			for _, item := range request.Items {
				err := Jobs.Extend(JobExtendRequest{
					Job:           item.Job,
					RequestedTime: item.RequestedTime,
				})

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				sendError(w, errors[0])
			} else {
				var response fnd.BulkResponse[util.Empty]
				for i := 0; i < len(request.Items); i++ {
					response.Responses = append(response.Responses, util.Empty{})
				}

				sendResponseOrError(w, response, nil)
			}
		}),
	)

	mux.HandleFunc(jobContext+"retrieveProducts", HttpRetrieveHandler[util.Empty](
		0,
		func(w http.ResponseWriter, r *http.Request, _ util.Empty) {
			products := Jobs.RetrieveProducts()
			sendResponseOrError(
				w,
				fnd.BulkResponse[orc.JobSupport]{
					Responses: products,
				},
				nil,
			)
		}),
	)
}
