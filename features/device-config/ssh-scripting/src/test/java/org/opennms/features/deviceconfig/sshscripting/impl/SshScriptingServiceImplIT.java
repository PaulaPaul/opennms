/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.deviceconfig.sshscripting.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.util.test.EchoShellFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.features.deviceconfig.sshscripting.SshScriptingService;

public class SshScriptingServiceImplIT {

    private static final String USER = "username";

    private static final String PASSWORD = "password";

    private SshServer sshd;

    private KeyPair hostKey;

    @Before
    public void prepare() throws Exception {
        setupSSHServer();
    }

    @After
    public void cleanup() throws InterruptedException {
        try {
            sshd.stop(true);
        } catch (Exception e) {
            // do nothing
        }
    }

    private SshScriptingService.Result execute(String password, Map<String, String> vars, String... statements) {
        return execute("localhost", password, vars, statements);
    }

    private SshScriptingService.Result execute(String host, String password, Map<String, String> vars, String... statements) {
        String script = List.of(statements).stream().collect(Collectors.joining("\n"));
        var ss = new SshScriptingServiceImpl();
        return ss.execute(script, USER, password, new InetSocketAddress(host, sshd.getPort()), null, vars, Duration.ofMillis(10000));
    }

    private SshScriptingService.Result execute(String... statements) {
        return execute(PASSWORD, Collections.emptyMap(), statements);
    }

    @Test
    public void login() {
        // 'user' and 'password' are made available as variables by default
        var result = execute(
                PASSWORD,
                Collections.emptyMap()
        );
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    public void login_failure() {
        // 'user' and 'password' are made available as variables by default
        var result = execute(
                PASSWORD + "x",
                Collections.emptyMap()
        );
        assertThat(result.isFailed(), is(true));
    }

    @Test
    public void echo() {
        var result = execute(
                "send: abc",
                "await: abc",
                "send: uvw",
                "await: uvw"
        );
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    public void variable_substitution() {
        var vars = new HashMap<String, String>() {{
           put("x", "var");
        }};
        // 'user' and 'password' are made available as variables by default
        var result = execute(
                PASSWORD,
                vars,
                "send: ${user} ${password} ${x}",
                "await: " + USER + " " + PASSWORD + " ${x}"
        );
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    public void await_nonexistent() {
        var ss = new SshScriptingServiceImpl();
        String script =
                List.of(
                        "send: abc",
                        "send: uvw",
                        "await: 123"
                ).stream().collect(Collectors.joining("\n"));

        var result = ss.execute(script, USER, PASSWORD, new InetSocketAddress("localhost", sshd.getPort()), null, Collections.emptyMap(), Duration.ofMillis(4000));
        assertThat(result.isFailed(), is(true));
        assertThat(result.stdout.isPresent(), is(true));
        assertThat(result.stdout.get(), is("abc\nuvw\n"));
    }

    @Test
    public void await_something_in_between() {
        var ss = new SshScriptingServiceImpl();
        String script =
                List.of(
                        "send: abc",
                        "send: uvw",
                        "send: 123",
                        "await: uvw"
                ).stream().collect(Collectors.joining("\n"));

        var result = ss.execute(script, USER, PASSWORD, new InetSocketAddress("localhost", sshd.getPort()), null, Collections.emptyMap(), Duration.ofMillis(4000));
        assertThat(result.isSuccess(), is(true));
    }

    private void setupSSHServer() throws Exception {
        this.hostKey = SecurityUtils.getKeyPairGenerator(KeyUtils.RSA_ALGORITHM).generateKeyPair();

        sshd = SshServer.setUpDefaultServer();
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setKeyPairProvider(KeyPairProvider.wrap(this.hostKey));
        sshd.setPasswordAuthenticator(
                (user, password, session) -> StringUtils.equals(user, USER) && StringUtils.equals(password, PASSWORD)
        );
        sshd.start();
    }

    @Test
    public void testIpAddresses() throws Exception {
        testIpAddress("::1", "0000:0000:0000:0000:0000:0000:0000:0001");
        testIpAddress("localhost", "127.0.0.1");

        final String anIPv4Address = "192.168.31.1";
        testIpAddress("localhost", anIPv4Address, anIPv4Address, null);

        final String anIPv6Address = "2001:0638:0301:11a0::1";
        testIpAddress("::1", "2001:0638:0301:11a0:0000:0000:0000:0001", null, anIPv6Address);
    }

    public void testIpAddress(final String hostname, final String expectedIp) throws Exception {
        testIpAddress(hostname, expectedIp, null, null);
    }

    public void testIpAddress(final String hostname, final String expectedIp, final String ipv4Address, final String ipv6Address) throws Exception {
        final SshScriptingServiceImpl ss = new SshScriptingServiceImpl();

        ss.setTftpServerIPv4Address(ipv4Address);
        ss.setTftpServerIPv6Address(ipv6Address);

        final String script = List.of(
                            "send: ${tftpServerIp}",
                            "await: "+expectedIp
                        ).stream().collect(Collectors.joining("\n"));

        final SshScriptingService.Result result = ss.execute(script, USER, PASSWORD, new InetSocketAddress(hostname, sshd.getPort()), KeyUtils.getFingerPrint(this.hostKey.getPublic()), Collections.emptyMap(), Duration.ofMillis(10000));

        assertThat(result.isSuccess(), is(true));
    }

    public void testDevice(final String filename, final String username, final String password, final String hostname, final String filenameSuffix, final String tftpServer) throws IOException {
        final SshScriptingServiceImpl ss = new SshScriptingServiceImpl();
        ss.setTftpServerIPv4Address(tftpServer);

        byte[] encoded = Files.readAllBytes(Paths.get("../../../opennms-base-assembly/src/main/filtered/etc/examples/device-config/" + filename));
        final String script = new String(encoded, StandardCharsets.UTF_8).replace("${filenameSuffix}", filenameSuffix);
        final SshScriptingService.Result result = ss.execute(script, username, password, new InetSocketAddress(hostname, 22), null, Collections.emptyMap(), Duration.ofMillis(20000));

        if (result.stdout.isPresent()) {
            System.out.println("StdOut: "+result.stdout.get());
        }
        if (result.stderr.isPresent()) {
            System.out.println("StdErr: "+result.stderr.get());
        }
        System.out.println("Message: "+result.message);

        assertThat(result.isSuccess(), is(true));
    }

    /**
     * Method for local testing dcb example scripts using real hardware. Of course, ignored by default.
     */
    @Test
    @Ignore
    public void testDevices() throws Exception {
        final String tftpServer = "10.174.24.55";

        // tested with Aruba 6100 switch
        testDevice("aruba-cx-cli.dcb", "dcb", "DCBpass!", "10.174.24.41", "001", tftpServer);
        testDevice("aruba-cx-json.dcb", "dcb", "DCBpass!", "10.174.24.41", "002", tftpServer);

        // tested with Aruba 2450 switch
        testDevice("aruba-os-config.dcb", "dcb", "DCBpass!", "10.174.24.42", "003", tftpServer);

        // tested with Cisco 2960 switch
        testDevice("cisco-ios-running.dcb", "dcb", "DCBpass!", "10.174.24.43", "004", tftpServer);
        testDevice("cisco-ios-startup.dcb", "dcb", "DCBpass!", "10.174.24.43", "005", tftpServer);

        // tested with Juniper SRX-1500 firewall
        testDevice("juniper-junos-config-gz.dcb", "dcb", "DCBpass!", "10.174.24.44", "006", tftpServer);
        testDevice("juniper-junos-config-txt.dcb", "dcb", "DCBpass!", "10.174.24.44", "007", tftpServer);
        testDevice("juniper-junos-config-set.dcb", "dcb", "DCBpass!", "10.174.24.44", "008", tftpServer);

        // tested with Palo Alto virtual firewall (PA-VM-ESX-10.0.4)
        testDevice("paloalto-panos-config.dcb", "dcb", "DCBpass!", "10.174.24.45", "009", tftpServer);
    }
}
