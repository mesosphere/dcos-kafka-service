import json
import re
import tempfile

import pytest

import sdk_cmd
import sdk_install
import sdk_tasks
import sdk_utils
from tests import config, test_utils


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
def test_upgrade_111_210(configure_security):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

    from_version = '2.4.0-1.1.1'
    to_version = 'stub-universe'

    # In case that 2.1.0 has been released upgrade we want to test to a specific
    # 2.1.0 version rather than to latest stub universe version.
    ret_code, stdout, _ = sdk_cmd.run_cli(
        cmd='package describe kafka --package-versions',
    )
    assert ret_code == 0
    # Filter old versions in custom format that doesn't matches the x.x.x-x.x.x
    package_versions = [
        v for v in json.loads(stdout) if re.match(r'^([\d\.]+)\-([\d\.]+)$', v)
    ]
    version_210 = sorted(
        [v for v in package_versions if v.split('-')[1] == '2.1.0'],
        reverse=True,
    )
    if len(version_210) > 0:
        to_version = version_210[0]

    try:
        sdk_install.install(
            package_name=config.PACKAGE_NAME,
            service_name=foldered_name,
            expected_running_tasks=config.DEFAULT_BROKER_COUNT,
            additional_options={"service": {"name": foldered_name}, "brokers": {"cpus": 0.5}},
            package_version=from_version,
        )

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=foldered_name)

        # Assert that inter broker protocol is set to 1.x version
        _, options, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            foldered_name,
            'describe',
            parse_json=True,
        )
        assert options['kafka']['inter_broker_protocol_version'] == '1.0'

        task_ids = sdk_tasks.get_task_ids(foldered_name, "")

        # Run update to the new version
        with tempfile.NamedTemporaryFile() as opts_f:
            opts_f.write(json.dumps({
                'kafka': {
                    'inter_broker_protocol_version': '1.0'
                }
            }).encode('utf-8'))
            opts_f.flush()

            sdk_cmd.svc_cli(
                config.PACKAGE_NAME,
                foldered_name,
                'update start --package-version={} --options={}'.format(
                    to_version,
                    opts_f.name,
                ),
            )

        # we must manually upgrade the package CLI because it's not done automatically in this flow
        # (and why should it? that'd imply the package CLI replacing itself via a call to the main CLI...)
        sdk_cmd.run_cli(
            'package install --yes --cli --package-version={} {}'.format(
                to_version, config.PACKAGE_NAME,
            )
        )
        sdk_tasks.check_tasks_updated(foldered_name, "", task_ids)

        # Assert that inter broker protocol is set to 1.x version
        _, options, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            foldered_name,
            'describe',
            parse_json=True,
        )
        assert options['kafka']['inter_broker_protocol_version'] == '1.0'

        # Update protocol to 2.1 (serially) by changing kafka configuration
        with tempfile.NamedTemporaryFile() as opts_f:
            options['kafka']['inter_broker_protocol_version'] = '2.1'
            opts_f.write(json.dumps(options).encode('utf-8'))
            opts_f.flush()
            sdk_cmd.svc_cli(
                config.PACKAGE_NAME,
                foldered_name,
                "update start --options={}".format(opts_f.name),
            )

        # Assert that inter broker protocol is set to 2.1 version
        _, options, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            foldered_name,
            'describe',
            parse_json=True,
        )
        assert options['kafka']['inter_broker_protocol_version'] == '2.1'

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
