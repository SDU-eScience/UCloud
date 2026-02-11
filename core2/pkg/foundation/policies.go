package foundation

import (
	"encoding/json"
	"net/http"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var policySchemas map[string]fndapi.PolicySchema

func initPolicies() {
	readPolicies()

	loadPoliciesFromDB()

	fndapi.PoliciesRetrieve.Handler(func(info rpc.RequestInfo, request util.Empty) (map[string]fndapi.Policy, *util.HttpError) {
		return retrievePolicies(info.Actor)
	})

	fndapi.PoliciesUpdate.Handler(func(info rpc.RequestInfo, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
		return updatePolicies(info.Actor, request)
	})
}

func readPolicies() {
	policies := pullProjectPolicies()
	policySchemas = make(map[string]fndapi.PolicySchema, len(policies))
	for _, policy := range policies {
		var document yaml.Node
		success := true

		err := yaml.Unmarshal(policy.Bytes, &document)
		if err != nil {
			log.Fatal("Error loading policy document ", policy.PolicyName, ": ", err)
		}

		var policySchema fndapi.PolicySchema

		cfgutil.Decode("", &document, &policySchema, &success)

		if !success {
			log.Fatal("Error decoding policy document ", policy.PolicyName, ": ", err)
		}
		policySchemas[policySchema.Name] = policySchema
	}
}

func loadPoliciesFromDB() {
	db.NewTx0(func(tx *db.Transaction) {
		db.Select[struct {
			ProjectID string
			Policy    string
		}](
			tx,
			`
				select * 
				from project.policies
				order by project_id
		    `,
			db.Params{},
		)

		//TODO(chaching)
	})
}

func retrievePolicies(actor rpc.Actor) (map[string]fndapi.Policy, *util.HttpError) {
	if !actor.Project.Present {
		return nil, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
	}
	if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		return nil, util.HttpErr(http.StatusForbidden, "Only PIs and Admins may list the policies")
	}
	return nil, nil
}

type SimplePolicyProperty struct {
	PropertyType  fndapi.PolicyPropertyType `yaml:"property_type"`
	PropertyValue any                       `yaml:"property_value"`
}

func updatePolicies(actor rpc.Actor, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
	if !actor.Project.Present {
		return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
	}
	if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRolePI) {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "Only PIs may update the policies")
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)
		for _, specification := range request.UpdatedPolicies {
			_, ok := policySchemas[specification.Schema]
			//When trying to update a schema that does not exist, we just skip it
			if !ok {
				log.Debug("Unknown Schema ", specification.Schema)
				continue
			}
			projectId := specification.Project

			isEnabled := false
			for _, property := range specification.Properties {
				if property.Name == "enabled" {
					isEnabled = property.Bool
				}
			}

			//If the policy is not enabled we need to delete it form the DB.
			//Else we need to insert or update already existing project policy
			if !isEnabled {
				db.BatchExec(
					b,
					`
						delete from project.policies 
					    where project_id = :project_id and policy_name = :policy_name
				    `,
					db.Params{
						"project_id":  projectId,
						"policy_name": specification.Schema,
					},
				)
			} else {
				properties, err := json.Marshal(specification.Properties)
				if err != nil {
					log.Debug("Error marshalling policy document ", specification.Schema, " ", specification.Properties)
					continue
				}
				db.BatchExec(
					b,
					`
					insert into project.policies (policy_name, policy_property, project_id)
					values (:policy_name, :policy_properties, :project_id)
					on conflict (policy_name, project_id) do 
						update set policy_property = excluded.policy_property,
			    `,
					db.Params{
						"policy_name":       specification.Schema,
						"policy_properties": properties,
						"project_id":        projectId,
					},
				)
			}
		}
		db.BatchSend(b)

		//TODO( DO caching update)
	})

	return util.Empty{}, nil
}
