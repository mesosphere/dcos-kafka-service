 /**
 * This contains special implementations for validating the end-to-end repair scheduler
 * functionality in Kafka. These implementations will force each task into a specific
 * repair code path, so that it's easier to exercise all the code paths.
 *
 * When using these instead of the usual constrainer and monitor, and when running with the default
 * 3 brokers, the following behavior should be observed when you kill each task.
 *
 * Kill task kafka-0
 * - this should be restarted as usual
 *
 * Kill task kafka-1
 * - This task will never actually get restarted, since it will immediately move to stopped. It should be replaced shortly

 * Kill task kafka-2
 * - This task will be never be replaced or restarted, so it should always appear in stopped list

 */
package com.mesosphere.dcos.kafka.repair;