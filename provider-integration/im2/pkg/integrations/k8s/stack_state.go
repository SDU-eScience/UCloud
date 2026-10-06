package k8s

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"

	cfg "ucloud.dk/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

var (
	stackStateErrInvalid   = errors.New("invalid stack state request")
	stackStateErrConflict  = errors.New("stack state revision or lease conflict")
	stackStateErrForbidden = errors.New("stack state access is forbidden")
)

const stackStateMaxKeyLength = 512

type stackStateDbRow struct {
	RecordKey string
	Value     string
	Revision  int64
}

func stackStateIdentityHash(identity any) ([]byte, error) {
	encoded, err := json.Marshal(identity)
	if err != nil {
		return nil, err
	}

	hash := sha256.Sum256(encoded)
	return hash[:], nil
}

func stackStateStackScopeHash(owner orc.ResourceOwner) ([]byte, error) {
	providerId := strings.TrimSpace(cfg.Provider.Id)
	if providerId == "" {
		return nil, stackStateErrForbidden
	}

	identity := struct {
		Kind       string
		ProviderId string
		ProjectId  string
		Username   string
	}{
		ProviderId: providerId,
	}
	if owner.Project.Present {
		projectId := strings.TrimSpace(owner.Project.Value)
		if projectId == "" {
			return nil, stackStateErrForbidden
		}

		identity.Kind = "project"
		identity.ProjectId = projectId
	} else {
		username := strings.TrimSpace(owner.CreatedBy)
		if username == "" {
			return nil, stackStateErrForbidden
		}

		identity.Kind = "personal"
		identity.Username = username
	}

	return stackStateIdentityHash(identity)
}

func stackStateValidateScopeHash(scopeHash []byte) error {
	if len(scopeHash) != sha256.Size {
		return stackStateErrForbidden
	}
	return nil
}

func stackStateJobPrincipalHash(jobId string) ([]byte, error) {
	trimmed := strings.TrimSpace(jobId)
	if trimmed == "" {
		return nil, stackStateErrForbidden
	}

	return stackStateIdentityHash(struct {
		Kind  string
		JobId string
	}{
		Kind:  "job",
		JobId: trimmed,
	})
}

func stackStateValidateStackId(stackId string) error {
	if stackId == "" || strings.TrimSpace(stackId) != stackId || strings.ContainsRune(stackId, '\x00') || len(stackId) > stackStateMaxKeyLength {
		return fmt.Errorf("%w: invalid stack id", stackStateErrInvalid)
	}
	return nil
}

func stackStateValidateKey(key string) error {
	if key == "" || strings.TrimSpace(key) == "" || strings.ContainsRune(key, '\x00') || len(key) > stackStateMaxKeyLength {
		return fmt.Errorf("%w: invalid key", stackStateErrInvalid)
	}
	return nil
}

func stackStateValidatePrefix(prefix string) error {
	if strings.ContainsRune(prefix, '\x00') || len(prefix) > stackStateMaxKeyLength {
		return fmt.Errorf("%w: invalid prefix", stackStateErrInvalid)
	}
	return nil
}

func stackStateRecordFromDb(row stackStateDbRow) ucxapi.StackStateRecord {
	return ucxapi.StackStateRecord{
		Key:      row.RecordKey,
		Value:    json.RawMessage(row.Value),
		Revision: row.Revision,
	}
}

func stackStateRead(scopeHash []byte, stackId string, key string) (ucxapi.StackStateReadResponse, error) {
	err := stackStateValidateScopeHash(scopeHash)
	if err != nil {
		return ucxapi.StackStateReadResponse{}, err
	}
	err = stackStateValidateStackId(stackId)
	if err != nil {
		return ucxapi.StackStateReadResponse{}, err
	}
	err = stackStateValidateKey(key)
	if err != nil {
		return ucxapi.StackStateReadResponse{}, err
	}

	row, ok := db.NewTx2(func(tx *db.Transaction) (stackStateDbRow, bool) {
		return db.Get[stackStateDbRow](
			tx,
			`
				select
					record_key,
					value::text as value,
					revision
				from k8s.stack_state_records
				where stack_scope_hash = :stack_scope_hash
					and stack_id = :stack_id
					and record_key = :record_key
			`,
			db.Params{
				"stack_scope_hash": scopeHash,
				"stack_id":         stackId,
				"record_key":       key,
			},
		)
	})

	if !ok {
		return ucxapi.StackStateReadResponse{Found: false}, nil
	}
	return ucxapi.StackStateReadResponse{
		Found:  true,
		Record: stackStateRecordFromDb(row),
	}, nil
}

func stackStateList(
	scopeHash []byte,
	stackId string,
	prefix string,
	next util.Option[string],
	itemsPerPage int,
) (fnd.PageV2[ucxapi.StackStateRecord], error) {
	err := stackStateValidateScopeHash(scopeHash)
	if err != nil {
		return fnd.PageV2[ucxapi.StackStateRecord]{}, err
	}
	err = stackStateValidateStackId(stackId)
	if err != nil {
		return fnd.PageV2[ucxapi.StackStateRecord]{}, err
	}
	err = stackStateValidatePrefix(prefix)
	if err != nil {
		return fnd.PageV2[ucxapi.StackStateRecord]{}, err
	}
	if next.Present {
		err = stackStateValidateKey(next.Value)
		if err != nil {
			return fnd.PageV2[ucxapi.StackStateRecord]{}, err
		}
	}

	limit := fnd.ItemsPerPage(itemsPerPage)
	rows := db.NewTx(func(tx *db.Transaction) []stackStateDbRow {
		return db.Select[stackStateDbRow](
			tx,
			`
				select
					record_key,
					value::text as value,
					revision
				from k8s.stack_state_records
				where stack_scope_hash = :stack_scope_hash
					and stack_id = :stack_id
					and left(record_key, char_length(:prefix)) = :prefix
					and (cast(:next as text) is null or record_key > cast(:next as text))
				order by record_key
				limit :limit
			`,
			db.Params{
				"stack_scope_hash": scopeHash,
				"stack_id":         stackId,
				"prefix":           prefix,
				"next":             next.Sql(),
				"limit":            limit + 1,
			},
		)
	})

	page := fnd.PageV2[ucxapi.StackStateRecord]{
		Items:        make([]ucxapi.StackStateRecord, 0, limit),
		Next:         util.OptNone[string](),
		ItemsPerPage: limit,
	}
	if len(rows) > limit {
		rows = rows[:limit]
		page.Next.Set(rows[len(rows)-1].RecordKey)
	}

	for _, row := range rows {
		page.Items = append(page.Items, stackStateRecordFromDb(row))
	}

	return page, nil
}

func stackStateWrite(
	scopeHash []byte,
	stackId string,
	request ucxapi.StackStateWriteRequest,
) (ucxapi.StackStateWriteResponse, error) {
	err := stackStateValidateScopeHash(scopeHash)
	if err != nil {
		return ucxapi.StackStateWriteResponse{}, err
	}
	err = stackStateValidateStackId(stackId)
	if err != nil {
		return ucxapi.StackStateWriteResponse{}, err
	}
	err = stackStateValidateKey(request.Key)
	if err != nil {
		return ucxapi.StackStateWriteResponse{}, err
	}
	if request.ExpectedRevision < 0 || !json.Valid(request.Value) {
		return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: invalid value or expected revision", stackStateErrInvalid)
	}
	conditionKeys := make([]string, 0, len(request.Conditions))
	for _, condition := range request.Conditions {
		err = stackStateValidateKey(condition.Key)
		if err != nil {
			return ucxapi.StackStateWriteResponse{}, err
		}
		if condition.ExpectedRevision < 0 {
			return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: invalid condition revision", stackStateErrInvalid)
		}
		conditionKeys = append(conditionKeys, condition.Key)
	}
	for _, condition := range request.ValueConditions {
		err = stackStateValidateKey(condition.Key)
		if err != nil {
			return ucxapi.StackStateWriteResponse{}, err
		}
		if len(condition.Fields) == 0 {
			return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: empty value condition", stackStateErrInvalid)
		}
		for field, values := range condition.Fields {
			if field == "" || len(values) == 0 {
				return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: invalid value condition field", stackStateErrInvalid)
			}
			for _, value := range values {
				if !json.Valid(value) {
					return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: invalid value condition", stackStateErrInvalid)
				}
			}
		}
	}
	valueConditions, err := json.Marshal(request.ValueConditions)
	if err != nil {
		return ucxapi.StackStateWriteResponse{}, fmt.Errorf("%w: invalid value conditions", stackStateErrInvalid)
	}

	row, ok := db.NewTx2(func(tx *db.Transaction) (struct{ Revision int64 }, bool) {
		db.Exec(
			tx,
			`
				select pg_advisory_xact_lock(hashtextextended(
					encode(cast(:stack_scope_hash as bytea), 'hex') || ':' || cast(:stack_id as text),
					0
				))
			`,
			db.Params{
				"stack_scope_hash": scopeHash,
				"stack_id":         stackId,
			},
		)
		if len(conditionKeys) > 0 {
			conditions := db.Select[struct {
				RecordKey string
				Revision  int64
			}](
				tx,
				`
					select record_key, revision
					from k8s.stack_state_records
					where stack_scope_hash = :stack_scope_hash
						and stack_id = :stack_id
						and record_key = any(cast(:condition_keys as text[]))
				`,
				db.Params{
					"stack_scope_hash": scopeHash,
					"stack_id":         stackId,
					"condition_keys":   conditionKeys,
				},
			)
			revisions := make(map[string]int64, len(conditions))
			for _, condition := range conditions {
				revisions[condition.RecordKey] = condition.Revision
			}
			for _, condition := range request.Conditions {
				if revisions[condition.Key] != condition.ExpectedRevision {
					return struct{ Revision int64 }{}, false
				}
			}
		}
		if len(request.ValueConditions) > 0 {
			check, _ := db.Get[struct{ Valid bool }](
				tx,
				`
					select not exists (
						select 1
						from
							jsonb_array_elements(cast(:value_conditions as jsonb)) expected
							left join k8s.stack_state_records r on
								r.stack_scope_hash = :stack_scope_hash
								and r.stack_id = :stack_id
								and r.record_key = expected.value ->> 'key'
						where
							r.record_key is null
							or exists (
								select 1
								from jsonb_each(expected.value -> 'fields') expected_field
								where not exists (
									select 1
									from jsonb_array_elements(expected_field.value) allowed_value
									where r.value -> expected_field.key = allowed_value.value
								)
							)
					) as valid
				`,
				db.Params{
					"stack_scope_hash": scopeHash,
					"stack_id":         stackId,
					"value_conditions": string(valueConditions),
				},
			)
			if !check.Valid {
				return struct{ Revision int64 }{}, false
			}
		}
		if request.ExpectedRevision == 0 {
			return db.Get[struct{ Revision int64 }](
				tx,
				`
					insert into k8s.stack_state_records as existing_record(
						stack_scope_hash,
						stack_id,
						record_key,
						value,
						revision
					)
					select
						:stack_scope_hash,
						:stack_id,
						:record_key,
						cast(:value as jsonb),
						1
					where cast(:valid_until as bigint) is null
						or clock_timestamp() < to_timestamp(cast(:valid_until as double precision) / 1000)
					on conflict (stack_scope_hash, stack_id, record_key) do update set
						value = excluded.value,
						revision = existing_record.revision + 1
					where existing_record.revision = 0
						and (
							cast(:valid_until as bigint) is null
							or clock_timestamp() < to_timestamp(cast(:valid_until as double precision) / 1000)
						)
					returning revision
				`,
				db.Params{
					"stack_scope_hash": scopeHash,
					"stack_id":         stackId,
					"record_key":       request.Key,
					"value":            string(request.Value),
					"valid_until":      request.ValidUntil.Sql(),
				},
			)
		}
		return db.Get[struct{ Revision int64 }](
			tx,
			`
				update k8s.stack_state_records
				set
					value = cast(:value as jsonb),
					revision = revision + 1
				where stack_scope_hash = :stack_scope_hash
					and stack_id = :stack_id
					and record_key = :record_key
					and revision = :expected_revision
					and (
						cast(:valid_until as bigint) is null
						or clock_timestamp() < to_timestamp(cast(:valid_until as double precision) / 1000)
					)
				returning revision
			`,
			db.Params{
				"value":             string(request.Value),
				"stack_scope_hash":  scopeHash,
				"stack_id":          stackId,
				"record_key":        request.Key,
				"expected_revision": request.ExpectedRevision,
				"valid_until":       request.ValidUntil.Sql(),
			},
		)
	})

	if !ok {
		return ucxapi.StackStateWriteResponse{}, stackStateErrConflict
	}

	return ucxapi.StackStateWriteResponse{Revision: row.Revision}, nil
}

func stackStateRegisterProxyHandlers(
	proxy *ucx.Proxy,
	authorize func(stackId string) (string, []byte, []byte, error),
) {
	ucxapi.StackStateRead.HandlerProxy(proxy, func(_ context.Context, request ucxapi.StackStateReadRequest) (ucxapi.StackStateReadResponse, error) {
		stackId, scopeHash, _, err := authorize(request.StackId)
		if err != nil {
			return ucxapi.StackStateReadResponse{}, err
		}
		return stackStateRead(scopeHash, stackId, request.Key)
	})

	ucxapi.StackStateList.HandlerProxy(proxy, func(_ context.Context, request ucxapi.StackStateListRequest) (fnd.PageV2[ucxapi.StackStateRecord], error) {
		stackId, scopeHash, _, err := authorize(request.StackId)
		if err != nil {
			return fnd.PageV2[ucxapi.StackStateRecord]{}, err
		}
		return stackStateList(scopeHash, stackId, request.Prefix, request.Next, request.ItemsPerPage)
	})

	write := func(_ context.Context, request ucxapi.StackStateWriteRequest) (ucxapi.StackStateWriteResponse, error) {
		stackId, scopeHash, _, err := authorize(request.StackId)
		if err != nil {
			return ucxapi.StackStateWriteResponse{}, err
		}
		return stackStateWrite(scopeHash, stackId, request)
	}
	ucxapi.StackStateWrite.HandlerProxy(proxy, write)
	ucxapi.StackStateWriteChecked.HandlerProxy(proxy, write)
	ucxapi.StackStateWriteCheckedValues.HandlerProxy(proxy, write)
}

func stackStateControlHttpError(err error) *util.HttpError {
	if errors.Is(err, stackStateErrInvalid) {
		return util.HttpErr(http.StatusBadRequest, "invalid stack state request")
	}
	if errors.Is(err, stackStateErrConflict) {
		return util.HttpErr(http.StatusConflict, "stack state revision or lease conflict")
	}
	return util.HttpErr(http.StatusInternalServerError, "stack state operation failed")
}

func stackStateControlPrincipal(token string) (string, []byte, []byte, *util.HttpError) {
	_, stackId, scopeHash, principalHash, herr := stackStateControlPrincipalWithJob(token)
	return stackId, scopeHash, principalHash, herr
}

func stackStateControlPrincipalWithJob(token string) (*orc.Job, string, []byte, []byte, *util.HttpError) {
	job, herr := stackControlTokenAuthenticate(token)
	if herr != nil {
		return nil, "", nil, nil, herr
	}

	stackId := strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance])
	if stackId == "" {
		return nil, "", nil, nil, stackControlForbidden()
	}

	scopeHash, err := stackStateStackScopeHash(job.Owner)
	if err != nil {
		return nil, "", nil, nil, stackStateControlHttpError(err)
	}
	principalHash, err := stackStateJobPrincipalHash(job.Id)
	if err != nil {
		return nil, "", nil, nil, stackStateControlHttpError(err)
	}

	return job, stackId, scopeHash, principalHash, nil
}

func stackStateControlInitServer() {
	ucxapi.StackControlStateRead.Handler(func(_ rpc.RequestInfo, request ucxapi.StackControlStateReadRequest) (ucxapi.StackStateReadResponse, *util.HttpError) {
		stackId, scopeHash, _, herr := stackStateControlPrincipal(request.Token)
		if herr != nil {
			return ucxapi.StackStateReadResponse{}, herr
		}
		response, err := stackStateRead(scopeHash, stackId, request.Key)
		if err != nil {
			return ucxapi.StackStateReadResponse{}, stackStateControlHttpError(err)
		}
		return response, nil
	})

	ucxapi.StackControlStateList.Handler(func(_ rpc.RequestInfo, request ucxapi.StackControlStateListRequest) (fnd.PageV2[ucxapi.StackStateRecord], *util.HttpError) {
		stackId, scopeHash, _, herr := stackStateControlPrincipal(request.Token)
		if herr != nil {
			return fnd.PageV2[ucxapi.StackStateRecord]{}, herr
		}
		page, err := stackStateList(scopeHash, stackId, request.Prefix, request.Next, request.ItemsPerPage)
		if err != nil {
			return fnd.PageV2[ucxapi.StackStateRecord]{}, stackStateControlHttpError(err)
		}
		return page, nil
	})

	write := func(_ rpc.RequestInfo, request ucxapi.StackControlStateWriteRequest) (ucxapi.StackStateWriteResponse, *util.HttpError) {
		stackId, scopeHash, _, herr := stackStateControlPrincipal(request.Token)
		if herr != nil {
			return ucxapi.StackStateWriteResponse{}, herr
		}
		response, err := stackStateWrite(scopeHash, stackId, ucxapi.StackStateWriteRequest{
			Key:              request.Key,
			Value:            request.Value,
			ExpectedRevision: request.ExpectedRevision,
			Conditions:       request.Conditions,
			ValueConditions:  request.ValueConditions,
			ValidUntil:       request.ValidUntil,
		})
		if err != nil {
			return ucxapi.StackStateWriteResponse{}, stackStateControlHttpError(err)
		}
		return response, nil
	}
	ucxapi.StackControlStateWrite.Handler(write)
	ucxapi.StackControlStateWriteChecked.Handler(write)
	ucxapi.StackControlStateWriteCheckedValues.Handler(write)
}

func stackStateAppAuthorize(
	instanceId string,
	owner orc.ResourceOwner,
	mu *sync.Mutex,
	stackToDeletionRequest map[string]int,
	confirmedStacks map[string]bool,
) (string, []byte, []byte, error) {
	err := stackStateValidateStackId(instanceId)
	if err != nil {
		return "", nil, nil, err
	}

	mu.Lock()
	_, hasDeletionLease := stackToDeletionRequest[instanceId]
	confirmed := confirmedStacks[instanceId]
	mu.Unlock()
	if !hasDeletionLease && !confirmed {
		return "", nil, nil, stackStateErrForbidden
	}

	scopeHash, err := stackStateStackScopeHash(owner)
	if err != nil {
		return "", nil, nil, err
	}
	principalHash, err := stackStateJobPrincipalHash(strings.TrimSpace(owner.CreatedBy))
	if err != nil {
		return "", nil, nil, err
	}
	return instanceId, scopeHash, principalHash, nil
}

func stackStateJobAuthorize(instanceId string, job orc.Job) (string, []byte, []byte, error) {
	stackId := strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance])
	if stackId == "" || instanceId != stackId {
		return "", nil, nil, stackStateErrForbidden
	}

	scopeHash, err := stackStateStackScopeHash(job.Owner)
	if err != nil {
		return "", nil, nil, err
	}

	principalHash, err := stackStateJobPrincipalHash(job.Id)
	if err != nil {
		return "", nil, nil, err
	}
	return stackId, scopeHash, principalHash, nil
}

func stackStateInitAppProxy(
	proxy *ucx.Proxy,
	owner func() orc.ResourceOwner,
	mu *sync.Mutex,
	stackToDeletionRequest map[string]int,
	confirmedStacks map[string]bool,
) {
	stackStateRegisterProxyHandlers(proxy, func(instanceId string) (string, []byte, []byte, error) {
		return stackStateAppAuthorize(instanceId, owner(), mu, stackToDeletionRequest, confirmedStacks)
	})
}

func stackStateInitJobProxy(proxy *ucx.Proxy, job func() orc.Job) {
	stackStateRegisterProxyHandlers(proxy, func(instanceId string) (string, []byte, []byte, error) {
		return stackStateJobAuthorize(instanceId, job())
	})
}
