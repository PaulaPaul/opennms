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

package org.opennms.features.apilayer.scv;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.opennms.integration.api.v1.scv.Credentials;
import org.opennms.integration.api.v1.scv.SecureCredentialsVault;
import org.opennms.integration.api.v1.scv.immutables.ImmutableCredentials;

/** Exposes SecureCredentialsVault via Integration API */
public class SecureCredentialsVaultImpl implements SecureCredentialsVault {

    private final org.opennms.features.scv.api.SecureCredentialsVault delegate;

    public SecureCredentialsVaultImpl(org.opennms.features.scv.api.SecureCredentialsVault delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Set<String> getAliases() {
        return delegate.getAliases();
    }

    @Override
    public Credentials getCredentials(String alias) {
        Objects.requireNonNull(alias);
        return Optional.ofNullable(delegate.getCredentials(alias))
                .map(c -> new ImmutableCredentials(c.getUsername(), c.getPassword(), c.getAttributes()))
                .orElse(null);
    }

    @Override
    public void setCredentials(String alias, Credentials credentials) {
        Objects.requireNonNull(alias);
        Objects.requireNonNull(credentials);
        this.delegate.setCredentials(alias, new org.opennms.features.scv.api.Credentials(
                credentials.getUsername(),
                credentials.getPassword(),
                credentials.getAttributes())
        );
    }
}
