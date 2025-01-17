/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.core.rpc.utils.mate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MapScope implements Scope {
    private final ScopeName scopeName;
    private final Map<ContextKey, String> values;

    public MapScope(final ScopeName scopeName, final Map<ContextKey, String> values) {
        this.scopeName = Objects.requireNonNull(scopeName);
        this.values = Objects.requireNonNull(values);
    }

    @Override
    public Optional<ScopeValue> get(final ContextKey contextKey) {
        return Optional.ofNullable(this.values.get(contextKey))
                .map(value -> new ScopeValue(this.scopeName, value));
    }

    @Override
    public Set<ContextKey> keys() {
        return this.values.keySet();
    }

    public static MapScope singleContext(final ScopeName scopeName, final String context, final Map<String, String> values) {
        return new MapScope(
                scopeName, values.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> new ContextKey(context, e.getKey()),
                        e -> e.getValue())
                ));
    }
}
