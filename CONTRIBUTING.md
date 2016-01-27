# Contributing to the Kafka Framework

## Getting started

### Build

```bash
git clone --recursive https://github.com/mesosphere/kafka-private # include mesos-commons in pull
cd kafka-private
./build-container-hook.sh # creates package/container-hook-0.1.0.tgz
./gradlew shadowjar # creates kafka-scheduler/build/libs/kafka-scheduler-0.1.0-uber.jar
[... hack hack hack ...]
git pull --recurse-submodules # ensure mesos-commons is also updated
```

### Unit Test

```bash
./gradlew test
```

### Run in DCOS

Prerequisites:
- Have a DCOS cluster somewhere
- Install [dcos-cli](https://docs.mesosphere.com/administration/introcli/cli/)

#### Deploy from local custom Universe

```bash
git clone https://github.com/mesosphere/universe
dcos config prepend package.sources file:///path/to/universe
# Customize URLs in /path/to/universe/repo/packages/K/kafka/N/resource.json
dcos config show
# [...]
# package.sources=[
#   'file:///path/to/universe',
#   'https://github.com/mesosphere/universe/archive/version-1.x.zip']
dcos package update
dcos package install --yes kafka
```

#### Deploy from custom branch in Universe repo

```bash
dcos config prepend package.sources https://github.com/mesosphere/universe/archive/custom-branch.zip
dcos config show
# [...]
# package.sources=[
#   'https://github.com/mesosphere/universe/archive/custom-branch.zip',
#   'https://github.com/mesosphere/universe/archive/version-1.x.zip']
dcos package update
dcos package install --yes kafka
```

## Working with the [mesos-commons](https://github.com/mesosphere/mesos-commons) submodule

The `mesos-commons` directory holds a git submodule for common Java libraries for interacting with Mesos. Working with git submodules is a special kind of fun. This submodule is why the initial `git clone` **must** use the `--recursive` flag. Similarly, you should use `git pull --recurse-submodules` to pull the latest from both `kafka-private` AND `mesos-commons` whenever you update.

Similarly, any changes you make inside `mesos-commons` must be committed and pushed separately from the parent project. Then you will need to make a commit in the parent project to point to the latest version of the submodule. It is most likely that the parent code changed based on the submodule. You will want to commit the parent changes after the submodule changes, then commit the parent changes along with the update to the submodule reference. 
[Vogella](http://www.vogella.com/tutorials/Git/article.html#submodules) provides a decent tutorial for those new to submodules.

If you used a standard clone (without `--recursive`), the submodule can also be manually brought up by following these steps:

```bash
rm -rf mesos-commons # if mesos-commons exists in the parent project
git submodule init
git submodule update --remote
cd mesos-commons
git pull origin master
```

