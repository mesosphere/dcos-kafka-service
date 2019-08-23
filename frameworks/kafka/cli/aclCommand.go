package main

import (
	"net/url"

	"github.com/mesosphere/dcos-commons/cli/client"
	"gopkg.in/alecthomas/kingpin.v3-unstable"
)

type AclHandler struct {
	cluster             bool
	topicName           string
	consumerGroupName   string
	resourcePatternType string
	principal           string
	principalAction     string
	operation           string
	host                string
	hostAction          string
	resource            string
	producer            bool
	consumer            bool
	group               string
	transactionalId     string
	idempotent          bool
}

func handleAclSection(app *kingpin.Application) {
	cmd := &AclHandler{}
	acl := app.Command("acl", "Kafka acl configuration")

	list := acl.Command(
		"list",
		"List acl for given resource").Action(cmd.runAclList)
	list.Flag("cluster", "Specifies cluster as resource").Short('c').Default("false").BoolVar(&cmd.cluster)
	list.Flag("topic", "Specifies the topic as resource").Short('t').StringVar(&cmd.topicName)
	list.Flag("consumer-group", "Specifies the consumer-group as resource").Short('g').StringVar(&cmd.consumerGroupName)
	list.Flag("resourcePatternType", "The type of the resource pattern or pattern filter. Values: ANY|MATCH|LITERAL|PREFIXED.").Short('p').StringVar(&cmd.resourcePatternType)
	list.Flag("group", "Consumer Group to which the ACLs should be added or removed. A value of * indicates the ACLs should apply to all groups.").StringVar(&cmd.group)

	add := acl.Command(
		"add",
		"Add acl for given resource").Action(cmd.runAclAdd)
	add.Flag("cluster", "Specifies cluster as resource").Short('c').Default("false").BoolVar(&cmd.cluster)
	add.Flag("topic", "Specifies the topic as resource").Short('t').StringVar(&cmd.topicName)
	add.Flag("consumer-group", "Specifies the consumer-group as resource").Short('g').StringVar(&cmd.consumerGroupName)
	add.Flag("resourcePatternType", "The type of the resource pattern or pattern filter. Values: ANY|MATCH|LITERAL|PREFIXED.").Short('p').StringVar(&cmd.resourcePatternType)
	add.Flag("principal", "Principal is in PrincipalType:name format").PlaceHolder("User:test@EXAMPLE.COM").StringVar(&cmd.principal)
	add.Flag("principalAction", "Values: 'allow' OR 'deny'. The principal will be used to generate an ACL with Allow/Deny permission.").StringVar(&cmd.principalAction)
	add.Flag("operation", "Comma separated list of operations.\n Valid values are : Read, Write, Create, Delete, Alter, Describe, ClusterAction, All").StringVar(&cmd.operation)
	add.Flag("host", "Host from which principals listed in --allow-principal will have access. If --principals is specified defaults to * which translates to 'all hosts'").StringVar(&cmd.host)
	add.Flag("hostAction", "Values: 'allow' OR 'deny'.The host will be used to generate an ACL which will Allow/Deny the host.").StringVar(&cmd.hostAction)
	add.Flag("producer", "Convenience option to add/remove acls for producer role. This will generate acls that allows WRITE, DESCRIBE on topic and CREATE on cluster.").Default("false").BoolVar(&cmd.producer)
	add.Flag("consumer", "Convenience option to add/remove acls for consumer role. This will generate acls that allows READ, DESCRIBE on topic and READ on consumer-group.").Default("false").BoolVar(&cmd.consumer)
	add.Flag("group", "Consumer Group to which the ACLs should be added or removed. A value of * indicates the ACLs should apply to all groups.").StringVar(&cmd.group)
	add.Flag("transactionalId", "The transactionalId to which ACLs should be added or removed. A value of * indicates the ACLs should apply to all transactionalIds.").StringVar(&cmd.transactionalId)
	add.Flag("idempotent", "Enable idempotence for the producer. This should be used in combination with the --producer option. Note that idempotence is enabled automatically if the producer is authorized to a particular transactional-id.").BoolVar(&cmd.idempotent)

	remove := acl.Command(
		"remove",
		"Remove acl for given resource").Action(cmd.runAclRemove)
	remove.Flag("cluster", "Specifies cluster as resource").Short('c').Default("false").BoolVar(&cmd.cluster)
	remove.Flag("topic", "Specifies the topic as resource").Short('t').StringVar(&cmd.topicName)
	remove.Flag("consumer-group", "Specifies the consumer-group as resource").Short('g').StringVar(&cmd.consumerGroupName)
	remove.Flag("resourcePatternType", "The type of the resource pattern or pattern filter. Values: ANY|MATCH|LITERAL|PREFIXED.").Short('p').StringVar(&cmd.resourcePatternType)
	remove.Flag("principal", "Principal is in PrincipalType:name format").PlaceHolder("User:test@EXAMPLE.COM").StringVar(&cmd.principal)
	remove.Flag("principalAction", "Values: 'allow' OR 'deny'.The principal will be used to generate an ACL with Allow/Deny permission.").StringVar(&cmd.principalAction)
	remove.Flag("operation", "Comma separated list of operations.\n Valid values are : Read, Write, Create, Delete, Alter, Describe, ClusterAction, All").StringVar(&cmd.operation)
	remove.Flag("host", "Host from which principals listed in --allow-principal will have access. If --principals is specified defaults to * which translates to 'all hosts'").StringVar(&cmd.host)
	remove.Flag("hostAction", "Values: 'allow' OR 'deny'.The host will be used to generate an ACL which will Allow/Deny the host.").StringVar(&cmd.hostAction)
	remove.Flag("producer", "Convenience option to add/remove acls for producer role. This will generate acls that allows WRITE, DESCRIBE on topic and CREATE on cluster.").Default("false").BoolVar(&cmd.producer)
	remove.Flag("consumer", "Convenience option to add/remove acls for consumer role. This will generate acls that allows READ, DESCRIBE on topic and READ on consumer-group.").Default("false").BoolVar(&cmd.consumer)
	remove.Flag("group", "Consumer Group to which the ACLs should be added or removed. A value of * indicates the ACLs should apply to all groups.").StringVar(&cmd.group)
	remove.Flag("transactionalId", "The transactionalId to which ACLs should be added or removed. A value of * indicates the ACLs should apply to all transactionalIds.").StringVar(&cmd.transactionalId)
	remove.Flag("idempotent", "Enable idempotence for the producer. This should be used in combination with the --producer option. Note that idempotence is enabled automatically if the producer is authorized to a particular transactional-id.").BoolVar(&cmd.idempotent)
}

func (cmd *AclHandler) runAclList(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	query := url.Values{}
	query.Set("topicName", cmd.topicName)
	query.Set("consumerGroupName", cmd.consumerGroupName)
	query.Set("resourcePatternType", cmd.resourcePatternType)
	query.Set("group", cmd.group)
	if cmd.cluster {
		query.Set("cluster", "true")
	}
	responseBytes, err := client.HTTPServiceGetQuery("v1/acl/list", query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	} else {
		client.PrintJSONBytes(responseBytes)
	}
	return nil
}

func (cmd *AclHandler) runAclAdd(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	query := url.Values{}
	if cmd.cluster {
		query.Set("cluster", "true")
	}
	query.Set("topicName", cmd.topicName)
	query.Set("consumerGroupName", cmd.consumerGroupName)
	query.Set("resourcePatternType", cmd.resourcePatternType)
	query.Set("principal", cmd.principal)
	query.Set("principalAction", cmd.principalAction)
	query.Set("operation", cmd.operation)
	query.Set("host", cmd.host)
	query.Set("hostAction", cmd.hostAction)
	query.Set("resource", cmd.resource)
	if cmd.producer {
		query.Set("producer", "true")
	}
	if cmd.consumer {
		query.Set("consumer", "true")
	}
	query.Set("group", cmd.group)
	if cmd.idempotent {
		query.Set("idempotent", "true")
	}
	query.Set("transactionalId", cmd.transactionalId)
	responseBytes, err := client.HTTPServicePutQuery("v1/acl", query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	} else {
		client.PrintJSONBytes(responseBytes)
	}
	return nil
}

func (cmd *AclHandler) runAclRemove(a *kingpin.Application, e *kingpin.ParseElement, c *kingpin.ParseContext) error {
	query := url.Values{}
	if cmd.cluster {
		query.Set("cluster", "true")
	}
	query.Set("topicName", cmd.topicName)
	query.Set("consumerGroupName", cmd.consumerGroupName)
	query.Set("resourcePatternType", cmd.resourcePatternType)
	query.Set("principal", cmd.principal)
	query.Set("principalAction", cmd.principalAction)
	query.Set("operation", cmd.operation)
	query.Set("host", cmd.host)
	query.Set("hostAction", cmd.hostAction)
	query.Set("resource", cmd.resource)
	if cmd.producer {
		query.Set("producer", "true")
	}
	if cmd.consumer {
		query.Set("consumer", "true")
	}
	query.Set("group", cmd.group)
	if cmd.idempotent {
		query.Set("idempotent", "true")
	}
	query.Set("transactionalId", cmd.transactionalId)
	responseBytes, err := client.HTTPServiceDeleteQuery("v1/acl", query.Encode())
	if err != nil {
		client.PrintMessageAndExit(err.Error())
	} else {
		client.PrintJSONBytes(responseBytes)
	}
	return nil
}
