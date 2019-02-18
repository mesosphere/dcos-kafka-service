"""
Kafka-Specific Utilities on networking

(This file contains the kafka-specific additions on the `sdk_networks` file)
"""
import sdk_agents


def check_endpoints_on_overlay(endpoints):
    def check_ip_addresses_on_overlay():
        # the overlay IP address should not contain any agent IPs
        return len(set(ip_addresses).intersection(set(all_agent_ips))) == 0

    assert "address" in endpoints, "endpoints: {} missing 'address' key".format(endpoints)
    assert "dns" in endpoints, "endpoints: {} missing 'dns' key".format(endpoints)

    # endpoints should have the format <ip_address>:port
    ip_addresses = [e.split(":")[0] for e in endpoints["address"]]
    all_agent_ips = [agent["hostname"] for agent in sdk_agents.get_agents()]
    assert (
        check_ip_addresses_on_overlay()
    ), "IP addresses for this service should not contain agent IPs, IPs were {}".format(
        ip_addresses
    )

    for dns in endpoints["dns"]:
        assert (
            "autoip.dcos.thisdcos.directory" in dns
        ), "DNS {} is incorrect should have autoip.dcos.thisdcos.directory".format(dns)
