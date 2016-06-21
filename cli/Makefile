all: env test packages binary

clean:
	bin/clean.sh

env:
	bin/env.sh

test:
	bin/test.sh

packages:
	bin/packages.sh

binary: env
	pyinstaller binary/binary.spec
	mkdir -p dist/linux
	mv dist/dcos-kafka dist/linux/dcos-kafka
