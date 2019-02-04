# Testing a Kafka package on a local cluster

## Build the package

### Install dependencies

* JDK 8
* Go (`brew install go`)

## Create a repository

Choose either a local repository or using the Mesosphere repository on AWS.

### Local Repository

???

### Mesosphere repository on AWS

* Set up maws
* Install minidcos
* Run `./build.sh aws`
* Note the last few commands
* Create a cluster

We need three agents for Kafka.
Let's test DC/OS Enterprise with permissive mode

* Download Enterprise installer to `./dcos_generate_config.sh`

```
minidcos docker create \
    --agents 3 \
    --public-agents 0 \
    --masters 1 \
    --variant enterprise \
    --security-mode permissive \
    --license-key ~/Documents/work/mesosphere/dcos_configs/enterprise/license/license.txt \
    dcos_generate_config.ee.sh
```
