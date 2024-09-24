package controller

import (
	"bytes"
	"encoding/json"
	"net/http"
	"os"
	"os/exec"
	"strings"

	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Script[Req any, Resp any] struct {
	Script string
}

func prepareFile(req any) (string, bool) {
	file, err := os.CreateTemp("", "*.json")
	defer util.SilentClose(file)
	if err != nil {
		log.Warn("Failed to create temporary file: %v", err)
		return "", false
	}
	err = file.Chmod(0600)
	if err != nil {
		log.Warn("Failed to chmod temporary file: %v", err)
		return "", false
	}

	jsonBytes, err := json.Marshal(req)
	if err != nil {
		log.Warn("Failed to marshal JSON request: %v", err)
		return "", false
	}

	_, err = file.Write(jsonBytes)
	if err != nil {
		log.Warn("Failed to write script request: %v", err)
		return "", false
	}

	return file.Name(), true
}

func InitScriptsLogDatabase() {
	CliScriptsCreate.Handler(func(r *ipc.Request[CliScriptsCreateRequest]) ipc.Response[int] {
		if r.Uid != 0 {
			return ipc.Response[int]{
				StatusCode: http.StatusForbidden,
				Payload:    0,
			}
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into script_log(timestamp, request, script_path, stdout, stderr, status_code, uid, success)
					values (now(), :request, :script_path, :stdout, :stderr, :status_code, :uid, :success)
				`,
				db.Params{
					"request":     r.Payload.Request,
					"script_path": r.Payload.Path,
					"stdout":      r.Payload.Stdout,
					"stderr":      r.Payload.Stderr,
					"status_code": r.Payload.StatusCode,
					"uid":         r.Uid,
					"success":     r.Payload.Success,
				},
			)
		})

		return ipc.Response[int]{
			StatusCode: http.StatusOK,
			Payload:    0,
		}
	})

	CliScriptsRetrieve.Handler(func(r *ipc.Request[CliScriptsRetrieveRequest]) ipc.Response[scriptLogEntry] {
		if r.Uid != 0 {
			return ipc.Response[scriptLogEntry]{
				StatusCode: http.StatusForbidden,
			}
		}

		return db.NewTx(func(tx *db.Transaction) ipc.Response[scriptLogEntry] {
			result, ok := db.Get[scriptLogEntry](
				tx,
				`
					select timestamp, id, request, script_path, stdout, stderr, status_code, success, uid
					from script_log
					where id = :id
				`,
				db.Params{
					"id": r.Payload.Id,
				},
			)

			if !ok {
				return ipc.Response[scriptLogEntry]{
					StatusCode: http.StatusNotFound,
				}
			}

			return ipc.Response[scriptLogEntry]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})
	})

	CliScriptsList.Handler(func(r *ipc.Request[CliScriptsListRequest]) ipc.Response[[]scriptLogEntry] {
		if r.Uid != 0 {
			return ipc.Response[[]scriptLogEntry]{
				StatusCode: http.StatusForbidden,
			}
		}

		result := db.NewTx(func(tx *db.Transaction) []scriptLogEntry {
			return db.Select[scriptLogEntry](
				tx,
				`
				select timestamp, id, request, script_path, stdout, stderr, status_code, success, uid
				from script_log
				order by id limit 100
			`,
				db.Params{},
			)
		})

		return ipc.Response[[]scriptLogEntry]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})
}

func (e *Script[Req, Resp]) Invoke(req Req) (Resp, bool) {
	var resp Resp
	if e.Script == "" {
		log.Error(
			"You must set the Script property of a script after NewScript but it was never set: %v %v",
			req,
			resp,
		)
		return resp, false
	}

	requestFile, ok := prepareFile(req)
	if !ok {
		reqBytes, _ := json.Marshal(req)
		reqString := string(reqBytes)

		CliScriptsCreate.Invoke(
			CliScriptsCreateRequest{e.Script, reqString, "", "", 0, false},
		)
		return resp, false
	}

	var stdout bytes.Buffer
	var stderr bytes.Buffer

	cmd := exec.Command(e.Script, requestFile)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		s := strings.TrimSpace(stderr.String())
		log.Warn("%v script failed: %s %v", e.Script, s, err)

		reqBytes, _ := json.Marshal(req)
		reqString := string(reqBytes)

		CliScriptsCreate.Invoke(
			CliScriptsCreateRequest{e.Script, reqString, stdout.String(), stderr.String(), 0, false},
		)
		return resp, false
	}

	err = json.Unmarshal(stdout.Bytes(), &resp)
	if err != nil {
		log.Warn(
			"%v script returned invalid response: \n\terr=%v\n\tstdout%v\n\tstderr=%v",
			e.Script,
			err,
			stdout.String(),
			stderr.String(),
		)
		return resp, false
	}

	reqBytes, _ := json.Marshal(req)
	reqString := string(reqBytes)

	CliScriptsCreate.Invoke(
		CliScriptsCreateRequest{e.Script, reqString, stdout.String(), stderr.String(), 0, true},
	)

	return resp, true
}

func NewScript[Req any, Resp any]() Script[Req, Resp] {
	return Script[Req, Resp]{}
}

type CliScriptsCreateRequest struct {
	Path       string
	Request    string
	Stdout     string
	Stderr     string
	StatusCode int
	Success    bool
}

type CliScriptsRetrieveRequest struct {
	Id uint64
}

type CliScriptsListRequest struct{}
type CliScriptsClearRequest struct{}

type scriptLogEntry struct {
	Timestamp  fnd.Timestamp
	Id         uint64
	Request    string
	ScriptPath string
	Stdout     string
	Stderr     string
	StatusCode string
	Success    bool
	Uid        int
}

var (
	CliScriptsCreate   = ipc.NewCall[CliScriptsCreateRequest, int]("cli.slurm.scripts.create")
	CliScriptsRetrieve = ipc.NewCall[CliScriptsRetrieveRequest, scriptLogEntry]("cli.slurm.scripts.retrieve")
	CliScriptsList     = ipc.NewCall[CliScriptsListRequest, []scriptLogEntry]("cli.slurm.scripts.browse")
	CliScriptsClear    = ipc.NewCall[CliScriptsClearRequest, int]("cli.slurm.scripts.clear")
)
