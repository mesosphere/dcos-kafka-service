import shakedown
import time

PACKAGE_NAME = 'kafka'
WAIT_TIME_IN_SECONDS = 300

ACS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()

REQUEST_HEADERS = {
    'authorization': 'token=%s' % ACS_TOKEN
}

def uninstall():
    shakedown.uninstall_package_and_wait(PACKAGE_NAME)
    time.sleep(10)
    framework_cleaner_cmd = 'docker run mesosphere/janitor /janitor.py -r kafka-role -p kafka-principal -z dcos-service-kafka --auth_token=' + ACS_TOKEN
    print(framework_cleaner_cmd)
    shakedown.run_command_on_master(framework_cleaner_cmd)

def spin(fn, success_predicate, *args, **kwargs):
    end_time = time.time() + WAIT_TIME_IN_SECONDS
    while time.time() < end_time:
        result = fn(*args, **kwargs)
        is_successful, error_message = success_predicate(result)
        if is_successful:
            break
        time.sleep(1)

    assert is_successful, error_message

    return result
