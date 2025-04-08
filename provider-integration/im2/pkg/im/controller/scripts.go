package controller

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"net/http"
	"os"
	"os/exec"
	"strings"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
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
	CliScriptsCreate.Handler(func(r *ipc.Request[CliScriptsCreateRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		err := db.NewTx(func(tx *db.Transaction) error {
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

			err := tx.ConsumeError()
			if err != nil {
				log.Warn("Failed to insert script log entry: %v", err)
				return err
			}
			return nil
		})

		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusBadRequest,
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
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

			err := tx.ConsumeError()
			if err != nil {
				log.Warn("Error while retrieving script log entry: %v", err)
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

		sqlQuery := sql.NullString{}
		sqlFilterBefore := sql.NullString{}
		sqlFilterAfter := sql.NullString{}
		sqlFilterScript := sql.NullString{}

		if r.Payload.Query != "" {
			sqlQuery = sql.NullString{String: r.Payload.Query, Valid: true}
		}
		if r.Payload.After != "" {
			sqlFilterAfter = sql.NullString{String: r.Payload.After, Valid: true}
		}
		if r.Payload.Before != "" {
			sqlFilterBefore = sql.NullString{String: r.Payload.Before, Valid: true}
		}
		if r.Payload.Script != "" {
			sqlFilterScript = sql.NullString{String: r.Payload.Script, Valid: true}
		}

		return db.NewTx(func(tx *db.Transaction) ipc.Response[[]scriptLogEntry] {
			result := db.Select[scriptLogEntry](
				tx,
				`
					select timestamp, id, request, script_path, stdout, stderr, status_code, success, uid
					from script_log
                    where
						(cast(:filter_failure as bool) is false or cast(:filter_failure as bool) = not success) and
						(cast(:filter_script as text) is null or script_path ilike '%' || :filter_script || '%') and
						(cast(:filter_before as text) is null or timestamp < now() - cast(:filter_before as interval)) and
						(cast(:filter_after as text) is null or timestamp > now() - cast(:filter_after as interval)) and
                        (
                            cast(:query as text) is null or
                            stdout ilike '%' || :query || '%' or
                            stderr ilike '%' || :query || '%' or
                            request ilike '%' || :query || '%' or
                            script_path ilike '%' || :query || '%'
                        )
                    order by timestamp desc
                    limit 35
				`,
				db.Params{
					"filter_failure": r.Payload.FailuresOnly,
					"query":          sqlQuery,
					"filter_after":   sqlFilterAfter,
					"filter_before":  sqlFilterBefore,
					"filter_script":  sqlFilterScript,
				},
			)

			err := tx.ConsumeError()
			if err != nil {
				return ipc.Response[[]scriptLogEntry]{
					StatusCode: http.StatusNotFound,
				}
			}

			return ipc.Response[[]scriptLogEntry]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})
	})

	CliScriptsClear.Handler(func(r *ipc.Request[CliScriptsClearRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		err := db.NewTx(func(tx *db.Transaction) error {
			db.Exec(
				tx,
				`
					delete from script_log where true
				`,
				db.Params{},
			)

			err := tx.ConsumeError()
			if err != nil {
				log.Warn("Unable to clear script log: %v", err)
				return err
			}

			return nil
		})

		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusBadRequest,
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	CliScriptsRemove.Handler(func(r *ipc.Request[CliScriptsRemoveRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		err := db.NewTx(func(tx *db.Transaction) error {
			db.Exec(
				tx,
				`
					delete from script_log where id = :id 
				`,
				db.Params{"id": r.Payload.Id},
			)

			err := tx.ConsumeError()
			if err != nil {
				log.Warn("Unable to delete from script log: %v", err)
				return err
			}

			return nil
		})

		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusBadRequest,
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
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

	reqBytes, err := json.Marshal(req)

	if err != nil {
		CliScriptsCreate.Invoke(
			CliScriptsCreateRequest{e.Script, "Unable to read request", "", "", -1, false},
		)
		return resp, false
	}

	requestFile, ok := prepareFile(req)
	if !ok {
		CliScriptsCreate.Invoke(
			CliScriptsCreateRequest{e.Script, string(reqBytes), "", "", -1, false},
		)
		return resp, false
	}

	var stdout bytes.Buffer
	var stderr bytes.Buffer

	cmd := exec.Command(e.Script, requestFile)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err = cmd.Run()

	var exitCode int
	var statusSuccess bool

	if cmd.ProcessState != nil {
		exitCode = cmd.ProcessState.ExitCode()
		statusSuccess = cmd.ProcessState.Success()
	}

	if err != nil {
		s := strings.TrimSpace(stderr.String())
		log.Warn("%v script failed: %s %v", e.Script, s, err)

		reqBytes, _ := json.Marshal(req)

		CliScriptsCreate.Invoke(
			CliScriptsCreateRequest{e.Script, string(reqBytes), stdout.String(), stderr.String(), exitCode, statusSuccess},
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

	CliScriptsCreate.Invoke(
		CliScriptsCreateRequest{e.Script, string(reqBytes), stdout.String(), stderr.String(), exitCode, statusSuccess},
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

type CliScriptsRemoveRequest struct {
	Id uint64
}

type CliScriptsListRequest struct {
	Query        string
	Before       string
	After        string
	Script       string
	FailuresOnly bool
}

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
	CliScriptsCreate   = ipc.NewCall[CliScriptsCreateRequest, util.Empty]("cli.slurm.scripts.create")
	CliScriptsRetrieve = ipc.NewCall[CliScriptsRetrieveRequest, scriptLogEntry]("cli.slurm.scripts.retrieve")
	CliScriptsList     = ipc.NewCall[CliScriptsListRequest, []scriptLogEntry]("cli.slurm.scripts.browse")
	CliScriptsRemove   = ipc.NewCall[CliScriptsRemoveRequest, util.Empty]("cli.slurm.scripts.remove")
	CliScriptsClear    = ipc.NewCall[CliScriptsClearRequest, util.Empty]("cli.slurm.scripts.clear")
)
