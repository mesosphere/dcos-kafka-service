package main

import (
	"encoding/json"
	"fmt"
	"github.com/mesosphere/dcos-commons/cli"
	"gopkg.in/alecthomas/kingpin.v2"
	"log"
	"os"
	"strconv"
	"strings"
)

func main() {
	modName, err := cli.GetModuleName()
	if err != nil {
		log.Fatalf(err.Error())
	}

	app, err := cli.NewApp(
		"0.1.0",
		"Mesosphere",
		fmt.Sprintf("Deploy and manage %s clusters", strings.Title(modName)))
	if err != nil {
		log.Fatalf(err.Error())
	}

	handleTopicSection(app)
	cli.HandleCommonArgs(
		app,
		modName,
		fmt.Sprintf("%s DC/OS CLI Module", strings.Title(modName)),
		[]string{"address","dns"})

	// Omit modname:
	kingpin.MustParse(app.Parse(os.Args[2:]))
}

type TopicHandler struct {
	topic string // shared by many commands
	createPartitions int
	createReplication int
	offsetsTime string
	partitionCount int
	produceMessageCount int
}

func (cmd *TopicHandler) runCreate(c *kingpin.ParseContext) error {
	payload, err := json.Marshal(map[string]interface{} {
		"name": cmd.topic,
		"partitions": cmd.createPartitions,
		"replication": cmd.createReplication,
	})
	if err != nil {
		return err
	}
	cli.PrintJSON(cli.HTTPPostJSON("v1/topics/create", string(payload)))
	return nil
}
func (cmd *TopicHandler) runDelete(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPDelete(fmt.Sprintf("v1/topics/%s", cmd.topic)))
	return nil
}
func (cmd *TopicHandler) runDescribe(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet(fmt.Sprintf("v1/topics/%s", cmd.topic)))
	return nil
}
func (cmd *TopicHandler) runList(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/topics"))
	return nil
}
func (cmd *TopicHandler) runOffsets(c *kingpin.ParseContext) error {
	var timeVal int64
	var err error
	switch (cmd.offsetsTime) {
	case "first":
		timeVal = -2
	case "last":
		timeVal = -1
	default:
		timeVal, err = strconv.ParseInt(cmd.offsetsTime, 10, 64)
		if err != nil {
			log.Fatalf("Invalid value '%s' for --time (expected integer, 'first', or 'last'): %s",
				cmd.offsetsTime, err)
		}
	}

	payload, err := json.Marshal(map[string]interface{} {
		"time": timeVal,
	})
	if err != nil {
		return err
	}
	cli.PrintJSON(cli.HTTPGetJSON(fmt.Sprintf("v1/topics/%s/offsets", cmd.topic), string(payload)))
	return nil
}
func (cmd *TopicHandler) runPartitions(c *kingpin.ParseContext) error {
	payload, err := json.Marshal(map[string]interface{} {
		"operation": "partitions",
		"partitions": cmd.partitionCount,
	})
	if err != nil {
		return err
	}
	cli.PrintJSON(cli.HTTPPutJSON(fmt.Sprintf("v1/topics/%s", cmd.topic), string(payload)))
	return nil
}
func (cmd *TopicHandler) runProducerTest(c *kingpin.ParseContext) error {
	payload, err := json.Marshal(map[string]interface{} {
		"operation": "producer-test",
		"messages": cmd.produceMessageCount,
	})
	if err != nil {
		return err
	}
	cli.PrintJSON(cli.HTTPPutJSON(fmt.Sprintf("v1/topics/%s", cmd.topic), string(payload)))
	return nil
}
func (cmd *TopicHandler) runUnavailablePartitions(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/unavailable_partitions"))
	return nil
}
func (cmd *TopicHandler) runUnderReplicatedPartitions(c *kingpin.ParseContext) error {
	cli.PrintJSON(cli.HTTPGet("v1/unavailable_partitions"))
	return nil
}

func handleTopicSection(app *kingpin.Application) {
	cmd := &TopicHandler{}
	topic := app.Command("topic", "Kafka topic maintenance")

	create := topic.Command(
		"create",
		"Creates a new topic").Action(cmd.runCreate)
	create.Arg("topic", "The topic to create").StringVar(&cmd.topic)
	create.Flag("partitions", "Number of partitions").Short('p').Default("1").OverrideDefaultFromEnvar("KAFKA_DEFAULT_PARTITION_COUNT").IntVar(&cmd.createPartitions)
	create.Flag("replication", "Replication factor").Short('r').Default("3").OverrideDefaultFromEnvar("KAFKA_DEFAULT_REPLICATION_FACTOR").IntVar(&cmd.createReplication)

	delete := topic.Command(
		"delete",
		"Deletes an existing topic").Action(cmd.runDelete)
	delete.Arg("topic", "The topic to delete").StringVar(&cmd.topic)

	describe := topic.Command(
		"describe",
		"Describes a single existing topic").Action(cmd.runDescribe)
	describe.Arg("topic", "The topic to describe").StringVar(&cmd.topic)

	topic.Command(
		"list",
		"Lists all available topics").Action(cmd.runList)

	offsets := topic.Command(
		"offsets",
		"Returns the current offset counts for a topic").Action(cmd.runOffsets)
	offsets.Arg("topic", "The topic to examine").StringVar(&cmd.topic)
	offsets.Flag("time", "Offset for the topic: 'first'/'last'/timestamp_millis").Default("last").StringVar(&cmd.topic)

	partitions := topic.Command(
		"partitions",
		"Alters partition count for an existing topic").Action(cmd.runPartitions)
	partitions.Arg("topic", "The topic to update").StringVar(&cmd.topic)
	partitions.Arg("count", "The number of partitions to assign").IntVar(&cmd.partitionCount)

	producerTest := topic.Command(
		"producer_test",
		"Produces some test messages against a topic").Action(cmd.runProducerTest)
	producerTest.Arg("topic", "The topic to test").StringVar(&cmd.topic)
	producerTest.Arg("messages", "The number of messages to produce").IntVar(&cmd.produceMessageCount)

	topic.Command(
		"unavailable_partitions",
		"Gets info for any unavailable partitions").Action(cmd.runUnavailablePartitions)

	topic.Command(
		"under_replicated_partitions",
		"Gets info for any under-replicated partitions").Action(cmd.runUnderReplicatedPartitions)
}
